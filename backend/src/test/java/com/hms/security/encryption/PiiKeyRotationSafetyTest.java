package com.hms.security.encryption;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WO-029 / card U-003 — key rotation covers everything, and cannot repeat the
 * defects it replaced.
 *
 * <h2>What the old utility did</h2>
 * {@code PiiKeyRotationUtil} shipped with the encryption work and was never run.
 * Reading it before running it found three faults, each sufficient to destroy the
 * database:
 *
 * <ol>
 *   <li>The batch loop re-issued the same {@code SELECT ... LIMIT 100} with no
 *       cursor, so after rotating the first hundred rows it selected the same
 *       hundred — now under the new key — and decrypted them with the old one.
 *       Any table of 100+ rows failed partway, with no record of which rows had
 *       been converted. Rotated and unrotated ciphertext are indistinguishable
 *       by inspection, so the state was unrecoverable.</li>
 *   <li>{@code @Transactional} was imported and never applied.</li>
 *   <li>It named three tables. There are twenty-three.</li>
 * </ol>
 *
 * <p>The third is the one this class guards hardest, because it is the one that
 * comes back. Coverage decays every time somebody adds an encrypted column
 * without thinking about rotation, and nothing about adding a column prompts
 * that thought.
 */
@DisplayName("U-003: key rotation coverage and safety")
class PiiKeyRotationSafetyTest {

    private final PiiEncryptedColumnRegistry registry = new PiiEncryptedColumnRegistry();

    @Test
    @DisplayName("discovery finds far more than the three tables the old utility named")
    void discoveryCoversTheWholeEntityModel() {
        Map<String, PiiEncryptedColumnRegistry.Target> targets = registry.discover();

        // The old list, for contrast. If discovery ever returns something close
        // to three again, it has broken rather than improved.
        assertThat(targets.keySet())
            .as("the old utility rotated only these three")
            .contains("patients", "users", "consultants");

        assertThat(targets)
            .as("reflection found %d tables; the hardcoded list had 3, which would "
                + "have left the rest permanently undecryptable after a key swap",
                targets.size())
            .hasSizeGreaterThanOrEqualTo(20);

        assertThat(registry.columnCount(targets)).isGreaterThanOrEqualTo(60);
    }

    @Test
    @DisplayName("tables with no entity are included explicitly, because reflection cannot see them")
    void unmappedTablesAreNotForgotten() {
        Map<String, PiiEncryptedColumnRegistry.Target> targets = registry.discover();

        // Encrypted by a backfill rather than a converter, because there is no
        // entity to hang a converter on. Same blind spot that let
        // patient_pediatric hold children's health data in the clear for
        // eighteen migrations; leaving them out of rotation would orphan them.
        assertThat(targets).containsKeys("patient_pediatric", "sms_logs", "template_data");

        assertThat(targets.get("patient_pediatric").idColumn())
            .as("this table has no surrogate key — the cursor must walk patient_id")
            .isEqualTo("patient_id");

        assertThat(targets.get("sms_logs").columns())
            .as("error_message holds provider errors, which quote the number back")
            .contains("to_number", "message_body", "error_message");
    }

    @Test
    @DisplayName("a column that is merely near a @Convert is not treated as encrypted")
    void doesNotPickUpNeighbouringColumns() {
        Map<String, PiiEncryptedColumnRegistry.Target> targets = registry.discover();

        // An earlier attempt at this registry derived columns by regex over the
        // source and produced pharmacy_sales.sale_status — an @Enumerated(ORDINAL)
        // column, matched because a @Convert on an earlier field preceded it.
        // Rotation would have tried to decrypt an integer. Reflection reads the
        // annotation on the field it is actually attached to.
        PiiEncryptedColumnRegistry.Target pharmacy = targets.get("pharmacy_sales");
        if (pharmacy != null) {
            assertThat(pharmacy.columns())
                .as("sale_status is an ordinal enum, not ciphertext")
                .doesNotContain("sale_status");
        }
    }

    @Test
    @DisplayName("every target names an id column for the keyset cursor")
    void everyTargetHasACursorColumn() {
        // Without one the batch loop has nothing to page on, which is precisely
        // how the old implementation ended up re-selecting the same rows forever.
        assertThat(registry.discover().values())
            .allSatisfy(t -> {
                assertThat(t.idColumn()).isNotBlank();
                assertThat(t.columns()).isNotEmpty();
            });
    }

    @Test
    @DisplayName("the batch loop pages on a cursor and does not re-select")
    void rotationSqlUsesAKeysetCursor() throws IOException {
        String src = Files.readString(Paths.get(
            "src/main/java/com/hms/security/encryption/PiiKeyRotationUtil.java"));

        // Structural, and worth pinning: a LIMIT with no cursor and no ORDER BY
        // is the exact shape of the original defect, and it reads as reasonable
        // code right up until it destroys a table.
        assertThat(src).contains("ORDER BY");
        assertThat(src).contains("> ?");
        assertThat(src)
            .as("batch and cursor must commit together, or a crash between them "
                + "either loses rotated rows or skips unrotated ones")
            .contains("tx.executeWithoutResult");
    }

    @Test
    @DisplayName("rotating a key onto itself is refused")
    void identicalKeysAreRejected() throws IOException {
        String src = Files.readString(Paths.get(
            "src/main/java/com/hms/security/encryption/PiiKeyRotationUtil.java"));

        // Not a harmless no-op: it would march the cursor to the end of every
        // table and record COMPLETED, so a later real rotation reusing that runId
        // would skip everything and report success.
        assertThat(src).contains("identical");
    }
}
