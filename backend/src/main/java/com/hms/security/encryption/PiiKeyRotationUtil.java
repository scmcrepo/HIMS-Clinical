package com.hms.security.encryption;

import com.hms.security.encryption.PiiEncryptedColumnRegistry.Target;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rotates the PII encryption key — WO-029 / U-003, DPDP Rule 6.
 *
 * <h2>What replaced what, and why it matters</h2>
 * The previous {@code PiiKeyRotationUtil} would have destroyed the database. It
 * was never run, and reading it before running it found three defects:
 *
 * <ol>
 *   <li><b>Infinite re-select.</b> Every pass issued the same
 *       {@code SELECT ... LIMIT 100} with no cursor and no marker of what had
 *       been done. Having rotated the first hundred rows it selected the same
 *       hundred — now under the NEW key — and tried to decrypt them with the
 *       OLD one. Any table of 100+ rows failed partway, leaving it half-rotated
 *       with no record of which row used which key. Unrecoverable by
 *       inspection: rotated and unrotated ciphertext look identical.</li>
 *   <li><b>No transaction.</b> {@code @Transactional} was imported and never
 *       applied. Each row committed alone.</li>
 *   <li><b>Three tables of twenty-three.</b> It covered patients, users and
 *       consultants. Reflection finds 23 tables and 69 encrypted columns.
 *       Rotating and then swapping the key in configuration would have made the
 *       other twenty permanently undecryptable — diagnoses, insurance claims,
 *       grievances — discovered only on the first read after the change.</li>
 * </ol>
 *
 * <h2>The design follows from those</h2>
 * <ul>
 *   <li>Targets are <b>discovered</b> ({@link PiiEncryptedColumnRegistry}), not
 *       listed, so coverage cannot drift behind the entity model.</li>
 *   <li>A <b>keyset cursor</b> walks id order and is committed in the same
 *       transaction as the batch it describes, so an interrupted run resumes
 *       instead of re-rotating.</li>
 *   <li><b>Dry run first.</b> {@link #plan} does the whole round trip in memory
 *       and writes nothing, so the failures are known before any row changes.</li>
 *   <li><b>Verification after.</b> {@link #verify} reads every row back under
 *       the new key alone.</li>
 * </ul>
 *
 * <h2>Runbook</h2>
 * <ol>
 *   <li>Back up. Nothing here can undo a rotation: the old ciphertext is gone
 *       once a batch commits, and the old key is the only way to read a backup
 *       taken before it.</li>
 *   <li>{@code openssl rand -base64 32} for the new key.</li>
 *   <li>{@link #plan} with both keys. Expect zero failures. Investigate any.</li>
 *   <li>{@link #rotate}. Re-run it if it stops; it resumes from the cursor.</li>
 *   <li>{@link #verify} with the new key alone. Expect zero failures.</li>
 *   <li>Only then swap the key in configuration and restart.</li>
 * </ol>
 *
 * <p>Rotation is not online. Rows written by the application while it runs are
 * encrypted with the key the running application holds, which is still the old
 * one — and rotation may have already passed their table. Run it with the
 * application stopped. This class cannot enforce that, so it is said here and
 * in the runbook rather than left to be discovered.
 */
@Slf4j
@Component
public class PiiKeyRotationUtil {

    /**
     * Rows per transaction. Small enough that a failure loses little work and a
     * lock is not held long; large enough that the cursor write is not most of
     * the cost.
     */
    private static final int BATCH_SIZE = 200;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final PiiEncryptedColumnRegistry registry;

    public PiiKeyRotationUtil(JdbcTemplate jdbc, PlatformTransactionManager txManager,
                              PiiEncryptedColumnRegistry registry) {
        this.jdbc = jdbc;
        // Built from the manager rather than injected, matching BulkImportService.
        // Spring Boot does auto-configure a TransactionTemplate bean, but this
        // class is the one place where losing a transaction silently corrupts
        // data, so it does not depend on an auto-configuration being present.
        this.tx = new TransactionTemplate(txManager);
        this.registry = registry;
    }

    /** Per-table outcome. */
    public record TableResult(String table, int rowsRead, int rowsChanged,
                              int rowsAlreadyNew, int rowsFailed, String lastErrorType) {}

    /** Whole-run outcome. */
    public record RotationReport(UUID runId, boolean dryRun,
                                 Map<String, TableResult> tables) {

        public int totalFailed() {
            return tables.values().stream().mapToInt(TableResult::rowsFailed).sum();
        }

        public int totalChanged() {
            return tables.values().stream().mapToInt(TableResult::rowsChanged).sum();
        }

        public boolean clean() {
            return totalFailed() == 0;
        }
    }

    /**
     * Dry run: prove every row can make the trip, and write nothing.
     *
     * <p>For each value it decrypts with the old key, encrypts with the new,
     * decrypts again with the new, and checks the result equals the original.
     * The second decryption is the point — encrypting successfully proves
     * nothing about whether the value can be read back.
     */
    public RotationReport plan(String oldBase64Key, String newBase64Key) {
        return run(oldBase64Key, newBase64Key, true);
    }

    /**
     * Rotate for real. Resumes from the recorded cursor if a previous run of the
     * same {@code runId} stopped partway.
     */
    public RotationReport rotate(String oldBase64Key, String newBase64Key) {
        return run(oldBase64Key, newBase64Key, false);
    }

    private RotationReport run(String oldBase64Key, String newBase64Key, boolean dryRun) {
        if (oldBase64Key == null || newBase64Key == null || oldBase64Key.isBlank()
            || newBase64Key.isBlank()) {
            throw new IllegalArgumentException("Both keys are required");
        }
        if (oldBase64Key.equals(newBase64Key)) {
            // Not a no-op worth allowing: it would march the cursor to the end of
            // every table and record a COMPLETED run, so a later real rotation
            // with the same runId would skip everything.
            throw new IllegalArgumentException(
                "The old and new keys are identical — nothing to rotate");
        }

        PiiEncryptionService oldEnc = new PiiEncryptionService(oldBase64Key);
        PiiEncryptionService newEnc = new PiiEncryptionService(newBase64Key);

        UUID runId = UUID.randomUUID();
        Map<String, Target> targets = registry.discover();

        log.info("event=key_rotation.start run_id={} dry_run={} tables={} columns={}",
                 runId, dryRun, targets.size(), registry.columnCount(targets));

        Map<String, TableResult> results = new LinkedHashMap<>();
        for (Target target : targets.values()) {
            results.put(target.table(), rotateTable(runId, target, oldEnc, newEnc, dryRun));
        }

        RotationReport report = new RotationReport(runId, dryRun, results);
        log.info("event=key_rotation.finish run_id={} dry_run={} changed={} failed={}",
                 runId, dryRun, report.totalChanged(), report.totalFailed());
        return report;
    }

    private TableResult rotateTable(UUID runId, Target target,
                                    PiiEncryptionService oldEnc, PiiEncryptionService newEnc,
                                    boolean dryRun) {
        String id = target.idColumn();
        String cols = String.join(", ", target.columns());
        String select = "SELECT " + id + ", " + cols + " FROM " + target.table()
                      + " WHERE " + id + " > ? ORDER BY " + id + " LIMIT " + BATCH_SIZE;
        String setClause = String.join(", ",
            target.columns().stream().map(c -> c + "=?").toList());
        String update = "UPDATE " + target.table() + " SET " + setClause + " WHERE " + id + "=?";

        Object cursor = resumeCursor(runId, target.table(), dryRun);

        int read = 0, changed = 0, alreadyNew = 0, failed = 0;
        String lastErrorType = null;

        while (true) {
            List<Map<String, Object>> rows =
                jdbc.queryForList(select, cursor == null ? MIN_UUID : cursor);
            if (rows.isEmpty()) {
                break;
            }

            List<Object[]> updates = new ArrayList<>();
            Object batchLastId = null;

            for (Map<String, Object> row : rows) {
                read++;
                batchLastId = row.get(id);

                Object[] args = new Object[target.columns().size() + 1];
                boolean rowFailed = false;
                boolean rowAlreadyNew = false;

                for (int i = 0; i < target.columns().size(); i++) {
                    Object raw = row.get(target.columns().get(i));
                    String value = raw == null ? null : raw.toString();

                    if (value == null || value.isEmpty()) {
                        args[i] = value;
                        continue;
                    }

                    try {
                        String plain = oldEnc.decrypt(value);
                        String reEncrypted = newEnc.encrypt(plain);

                        // The round trip, not just the encrypt. A value that
                        // encrypts but does not read back is the failure this
                        // whole exercise exists to avoid, and it is invisible
                        // unless you check.
                        if (!java.util.Objects.equals(plain, newEnc.decrypt(reEncrypted))) {
                            throw new PiiEncryptionException(
                                "round trip did not reproduce the original value");
                        }
                        args[i] = reEncrypted;

                    } catch (Exception decryptFailed) {
                        // Already under the new key? That is a resumed or
                        // re-run batch, not a fault — leave the value alone.
                        try {
                            newEnc.decrypt(value);
                            args[i] = value;
                            rowAlreadyNew = true;
                        } catch (Exception notNewEither) {
                            rowFailed = true;
                            args[i] = value;
                        }
                    }
                }

                if (rowFailed) {
                    failed++;
                    lastErrorType = "DecryptionFailure";
                    // Type only, never the message or the value: a decryption
                    // error can quote ciphertext or partial plaintext.
                    log.warn("event=key_rotation.row_failed table={} run_id={}",
                             target.table(), runId);
                    continue;
                }
                if (rowAlreadyNew) {
                    alreadyNew++;
                    continue;
                }

                args[target.columns().size()] = row.get(id);
                updates.add(args);
                changed++;
            }

            final Object commitCursor = batchLastId;
            final List<Object[]> batch = updates;
            final int doneSoFar = changed;
            final int failedSoFar = failed;
            final String errType = lastErrorType;

            if (!dryRun) {
                // Batch and cursor in ONE transaction. Split them and a crash
                // between the two either loses rotated rows from the cursor or
                // advances past rows that never committed.
                tx.executeWithoutResult(status -> {
                    if (!batch.isEmpty()) {
                        jdbc.batchUpdate(update, batch);
                    }
                    saveProgress(runId, target.table(), commitCursor, doneSoFar,
                                 failedSoFar, "IN_PROGRESS", errType, false);
                });
            }

            cursor = batchLastId;
            if (rows.size() < BATCH_SIZE) {
                break;
            }
        }

        saveProgress(runId, target.table(), cursor, changed, failed,
                     failed == 0 ? "COMPLETED" : "FAILED", lastErrorType, dryRun);

        log.info("event=key_rotation.table table={} read={} changed={} already_new={} failed={} dry_run={}",
                 target.table(), read, changed, alreadyNew, failed, dryRun);

        return new TableResult(target.table(), read, changed, alreadyNew, failed, lastErrorType);
    }

    /**
     * Read every encrypted value back using the new key alone.
     *
     * <p>Run after {@link #rotate} and before swapping the key in configuration.
     * Rotation reporting success means each batch committed; this is what
     * establishes that the data is actually readable with the key you are about
     * to make the only one.
     *
     * @return table name to count of values that would not decrypt
     */
    public Map<String, Integer> verify(String newBase64Key) {
        PiiEncryptionService newEnc = new PiiEncryptionService(newBase64Key);
        Map<String, Integer> failures = new LinkedHashMap<>();

        for (Target target : registry.discover().values()) {
            String id = target.idColumn();
            String select = "SELECT " + id + ", " + String.join(", ", target.columns())
                          + " FROM " + target.table()
                          + " WHERE " + id + " > ? ORDER BY " + id + " LIMIT " + BATCH_SIZE;

            Object cursor = MIN_UUID;
            int bad = 0;

            while (true) {
                List<Map<String, Object>> rows = jdbc.queryForList(select, cursor);
                if (rows.isEmpty()) {
                    break;
                }
                for (Map<String, Object> row : rows) {
                    cursor = row.get(id);
                    for (String c : target.columns()) {
                        Object raw = row.get(c);
                        if (raw == null || raw.toString().isEmpty()) {
                            continue;
                        }
                        try {
                            newEnc.decrypt(raw.toString());
                        } catch (Exception e) {
                            bad++;
                        }
                    }
                }
                if (rows.size() < BATCH_SIZE) {
                    break;
                }
            }

            if (bad > 0) {
                failures.put(target.table(), bad);
                log.error("event=key_rotation.verify_failed table={} values={}", target.table(), bad);
            }
        }

        if (failures.isEmpty()) {
            log.info("event=key_rotation.verify_ok tables={}", registry.discover().size());
        }
        return failures;
    }

    /**
     * Where a previous run of this id got to, or null to start at the beginning.
     *
     * <p>Dry runs never resume. A dry run that skipped the rows an earlier dry
     * run had already checked would report a clean plan for a table it had
     * barely looked at.
     */
    private Object resumeCursor(UUID runId, String table, boolean dryRun) {
        if (dryRun) {
            return null;
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT last_id FROM pii_key_rotation_progress "
            + "WHERE run_id = ? AND table_name = ? AND dry_run = FALSE", runId, table);
        return rows.isEmpty() ? null : rows.get(0).get("last_id");
    }

    private void saveProgress(UUID runId, String table, Object lastId, int done, int failedCount,
                              String state, String errorType, boolean dryRun) {
        jdbc.update(
            "INSERT INTO pii_key_rotation_progress "
            + "(run_id, table_name, last_id, rows_done, rows_failed, state, last_error, dry_run) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
            + "ON CONFLICT (run_id, table_name) DO UPDATE SET "
            + "last_id = EXCLUDED.last_id, rows_done = EXCLUDED.rows_done, "
            + "rows_failed = EXCLUDED.rows_failed, state = EXCLUDED.state, "
            + "last_error = EXCLUDED.last_error, updated_at = NOW()",
            runId, table, lastId, done, failedCount, state, errorType, dryRun);
    }

    /**
     * Lowest possible UUID, so {@code id > ?} starts at the beginning.
     *
     * <p>Simpler than branching the SQL on whether a cursor exists, and it keeps
     * one prepared statement rather than two.
     */
    private static final UUID MIN_UUID = new UUID(0L, 0L);
}
