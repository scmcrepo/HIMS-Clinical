package com.hms.infrastructure.persistence;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WO-032 / F1 — every {@link AuditableEntity} subclass must have a table
 * carrying the columns that class maps.
 *
 * <h2>Why this test exists</h2>
 * {@code AuditableEntity} maps {@code tenant_id}, {@code branch_id},
 * {@code status}, {@code created_by}, {@code created_at}, {@code modified_by}
 * and {@code modified_at}. Hibernate emits all of them in every SELECT and
 * INSERT for every subclass. A table missing one does not fail at startup — it
 * fails the first time that repository is touched.
 *
 * <p>That delay is the whole problem. Six tables shipped without
 * {@code branch_id}: {@code erasure_requests} (V179),
 * {@code incident_affected_principals} (V209), {@code grievance_events} (V210)
 * and all three V213 retention tables. They are the storage for s. 8(7)
 * retention, s. 12 erasure, the s. 8(9) grievance audit trail and the Rule 7
 * affected-principals list. Every one of those subsystems was written, reviewed,
 * merged and reported as delivered across four work orders, and not one of them
 * had ever executed a single statement.
 *
 * <p>The reason it survived review is worth recording: the *parent* tables
 * ({@code consent_records}, {@code security_incidents}, {@code grievances})
 * declared {@code branch_id} correctly, so from the top the subsystems looked
 * wired up. V113-V118 bulk-added both columns to the tables existing at the
 * time, from hardcoded lists, which quietly established the expectation that
 * every table has them. Anything created after V118 has to declare them itself,
 * and nothing checked.
 *
 * <p>The only runtime symptom was one line in the error log —
 * {@code column rpe1_0.branch_id does not exist} — on every boot, next to
 * {@code event=retention.startup.validation_failed}. It had been there for
 * weeks.
 *
 * <h2>Why not {@code ddl-auto: validate}</h2>
 * Hibernate's own validation is stricter and would be the better check, but it
 * is all-or-nothing: it cannot pass while the two pre-existing defects in
 * {@link #KNOWN_BROKEN} remain, and those are a product decision rather than a
 * compliance one (see WO-032 F5). Turning it on today means a permanently red
 * build, which is how checks get disabled. This test asserts the same property
 * with an explicit, shrinking exemption list.
 *
 * <p>{@link #knownBrokenListDoesNotGrow} is the ratchet. Fixing an entry
 * without removing it from the list also fails, so the list can only shrink.
 * When it is empty, replace this test with
 * {@code spring.jpa.hibernate.ddl-auto: validate} and delete it.
 *
 * <p>Note that {@code application.yml} sets {@code ddl-auto: none} with the
 * comment "bypass strict Hibernate validation on startup". That comment is the
 * cause of this defect stated in advance.
 *
 * <p>Requires Docker; skipped automatically when unavailable, matching
 * {@code InsuranceReportTenantIsolationTest}.
 */
@Testcontainers
@DisabledIf("dockerNotAvailable")
@DisplayName("AuditableEntity — every mapped column exists on every table")
class AuditableEntitySchemaTest {

    static boolean dockerNotAvailable() {
        try {
            return !org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return true;
        }
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("hms_db")
            .withUsername("hms_user")
            .withPassword("hms_pass");

    /** Columns {@link AuditableEntity} maps. Add here if that class gains a {@code @Column}. */
    private static final List<String> REQUIRED = List.of(
        "tenant_id", "branch_id", "status",
        "created_by", "created_at", "modified_by", "modified_at");

    /**
     * Tables exempted from the column check. <strong>Empty, and it must stay
     * that way.</strong>
     *
     * <p>It held two entries for the length of one work order. Both were
     * pre-existing, both had live controllers, and both were found by this test
     * rather than by review:
     *
     * <ul>
     *   <li>{@code areas} — the table was dropped by V046 along with
     *       {@code patients.area_id}, but {@code AreaEntity},
     *       {@code AreaJpaRepository} and {@code AreaController} all survived it.
     *       The endpoint had been a guaranteed 500 since V046. The feature was
     *       retired, so the code was deleted to match (WO-032 / X-006).</li>
     *   <li>{@code customers} — missing {@code status}, {@code modified_by} and
     *       {@code modified_at}, so {@code POST /customer} threw. That table
     *       holds four encrypted PII columns for walk-in pharmacy customers and
     *       is live, so V216 completed the table instead.</li>
     * </ul>
     *
     * <p>Adding an entry here is not a fix. It is a decision to ship a
     * repository that throws, and it needs to be argued for in a work order —
     * not made silently to get a build green.
     */
    private static final Set<String> KNOWN_BROKEN = Set.of();

    private static Map<String, Set<String>> schema;
    private static Map<String, String> entityTables;

    @BeforeAll
    static void migrateAndRead() throws Exception {
        Flyway.configure()
              .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
              .locations("classpath:db/migration")
              .load()
              .migrate();

        schema = new LinkedHashMap<>();
        try (Connection c = DriverManager.getConnection(
                 POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             ResultSet rs = c.createStatement().executeQuery(
                 "SELECT table_name, column_name FROM information_schema.columns "
                 + "WHERE table_schema = 'public'")) {
            while (rs.next()) {
                schema.computeIfAbsent(rs.getString(1).toLowerCase(Locale.ROOT),
                                       k -> new LinkedHashSet<>())
                      .add(rs.getString(2).toLowerCase(Locale.ROOT));
            }
        }

        entityTables = new LinkedHashMap<>();
        ClassPathScanningCandidateComponentProvider scanner =
            new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        for (BeanDefinition bd : scanner.findCandidateComponents("com.hms")) {
            Class<?> type = Class.forName(bd.getBeanClassName());
            if (!AuditableEntity.class.isAssignableFrom(type)) {
                continue;
            }
            Table table = type.getAnnotation(Table.class);
            if (table != null && !table.name().isBlank()) {
                entityTables.put(type.getSimpleName(), table.name().toLowerCase(Locale.ROOT));
            }
        }
    }

    @Test
    @DisplayName("every AuditableEntity table carries all mapped columns")
    void everyMappedColumnExists() {
        List<String> failures = new ArrayList<>();

        entityTables.forEach((entity, table) -> {
            if (KNOWN_BROKEN.contains(table)) {
                return;
            }
            Set<String> columns = schema.get(table);
            if (columns == null) {
                failures.add(entity + " maps table '" + table + "' which does not exist");
                return;
            }
            List<String> missing = REQUIRED.stream().filter(c -> !columns.contains(c)).toList();
            if (!missing.isEmpty()) {
                failures.add(entity + " (" + table + ") missing: " + String.join(", ", missing));
            }
        });

        assertThat(entityTables)
            .as("entity scan found nothing — the scan itself is broken, not the schema")
            .isNotEmpty();

        assertThat(failures)
            .as("Each of these throws on first repository access, not at startup. "
                + "Add the column in a migration; do not add the table to KNOWN_BROKEN.")
            .isEmpty();
    }

    @Test
    @DisplayName("the known-broken exemption list only ever shrinks")
    void knownBrokenListDoesNotGrow() {
        Set<String> stillBroken = new TreeSet<>();

        for (String table : KNOWN_BROKEN) {
            Set<String> columns = schema.get(table);
            if (columns == null || !columns.containsAll(REQUIRED)) {
                stillBroken.add(table);
            }
        }

        assertThat(stillBroken)
            .as("An exempted table is now correct. Remove it from KNOWN_BROKEN so the "
                + "exemption cannot outlive the defect — that is what makes this list a "
                + "ratchet rather than a permanent excuse.")
            .containsExactlyInAnyOrderElementsOf(new TreeSet<>(KNOWN_BROKEN));

        assertThat(KNOWN_BROKEN)
            .as("The list is empty and should stay empty. With nothing exempted, "
                + "spring.jpa.hibernate.ddl-auto=validate is now unblocked and is the "
                + "stricter check — it catches type and nullability mismatches this "
                + "test cannot. Turn it on once a boot confirms it passes, then delete "
                + "this class.")
            .isEmpty();
    }
}
