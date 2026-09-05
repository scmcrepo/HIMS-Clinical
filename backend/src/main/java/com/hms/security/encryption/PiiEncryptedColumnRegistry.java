package com.hms.security.encryption;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Every table and column holding encrypted data — WO-029 / U-003.
 *
 * <h2>Why this is discovered and not listed</h2>
 * The old {@code PiiKeyRotationUtil} named three tables: patients, users,
 * consultants. Reflection finds <em>twenty-three</em>. Rotating with a list that
 * covers an eighth of the columns and then swapping the key in configuration
 * makes the rest permanently undecryptable — every diagnosis, every insurance
 * claim, every grievance. Silently, and only discovered on the first read.
 *
 * <p>A hardcoded list is wrong the moment somebody adds an
 * {@code @Convert(converter = EncryptedStringConverter.class)} and does not
 * think about rotation, which is every time. Discovery removes the failure
 * instead of documenting it.
 *
 * <p>It also removes a subtler one. An earlier attempt at this registry derived
 * the columns by regex over the source and produced
 * {@code pharmacy_sales.sale_status} — an {@code @Enumerated(ORDINAL)} column
 * that is not encrypted at all, picked up because a {@code @Convert} on an
 * earlier field happened to precede it. Feeding that to a rotation would have
 * decrypted an integer. Reflection reads the annotation on the field it is
 * actually attached to; text near a field is not the same as an annotation on it.
 *
 * <h2>The tables reflection cannot see</h2>
 * {@code patient_pediatric}, {@code sms_logs} and {@code template_data} hold
 * encrypted columns and are mapped by no entity, so nothing here can find them.
 * They are listed explicitly in {@link #UNMAPPED_TARGETS}, and that is the same
 * blind spot that let {@code patient_pediatric} keep children's health data in
 * the clear for eighteen migrations. If {@code U-006} decides those tables
 * should be dropped, delete the entries; until then rotation must cover them or
 * a rotation will orphan them.
 */
@Slf4j
@Component
public class PiiEncryptedColumnRegistry {

    /**
     * A table whose rows carry encrypted values.
     *
     * @param table      physical table name
     * @param idColumn   primary key column, used as the keyset cursor
     * @param columns    encrypted column names
     */
    public record Target(String table, String idColumn, List<String> columns) {}

    /**
     * Encrypted tables with no JPA entity, which reflection therefore misses.
     *
     * <p>Each was encrypted by a backfill in {@code PiiMigrationRunner} rather
     * than by a converter, because there is no entity to hang a converter on.
     * That is exactly why they are easy to forget.
     */
    private static final Map<String, Target> UNMAPPED_TARGETS = Map.of(
        "patient_pediatric",
            new Target("patient_pediatric", "patient_id", List.of("pediatric_data")),
        "sms_logs",
            new Target("sms_logs", "id", List.of("to_number", "message_body", "error_message")),
        "template_data",
            new Target("template_data", "id", List.of("content")));

    /**
     * Every rotation target, keyed by table name and sorted for stable ordering.
     *
     * <p>Sorted so that a dry-run plan and the rotation that follows it visit
     * tables in the same order — an operator comparing the two should not have
     * to reconcile a reshuffled list.
     */
    public Map<String, Target> discover() {
        Map<String, Target> targets = new TreeMap<>(UNMAPPED_TARGETS);

        ClassPathScanningCandidateComponentProvider scanner =
            new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));

        for (BeanDefinition bd : scanner.findCandidateComponents("com.hms")) {
            Class<?> type;
            try {
                type = Class.forName(bd.getBeanClassName());
            } catch (ClassNotFoundException e) {
                continue;
            }

            Table table = type.getAnnotation(Table.class);
            if (table == null || table.name().isBlank()) {
                continue;
            }

            List<String> encrypted = new ArrayList<>();
            String idColumn = null;

            // Walk the hierarchy: encrypted fields can be declared on a
            // @MappedSuperclass, and the @Id usually is.
            for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (idColumn == null && f.isAnnotationPresent(Id.class)) {
                        Column idCol = f.getAnnotation(Column.class);
                        idColumn = (idCol != null && !idCol.name().isBlank())
                            ? idCol.name() : f.getName();
                    }
                    Convert convert = f.getAnnotation(Convert.class);
                    if (convert == null
                        || !convert.converter().getSimpleName().startsWith("Encrypted")) {
                        continue;
                    }
                    Column col = f.getAnnotation(Column.class);
                    if (col != null && !col.name().isBlank()) {
                        encrypted.add(col.name().toLowerCase(Locale.ROOT));
                    }
                }
            }

            if (!encrypted.isEmpty()) {
                encrypted.sort(String::compareTo);
                targets.put(table.name().toLowerCase(Locale.ROOT),
                            new Target(table.name().toLowerCase(Locale.ROOT),
                                       idColumn == null ? "id" : idColumn,
                                       List.copyOf(encrypted)));
            }
        }

        return new LinkedHashMap<>(targets);
    }

    /** Total encrypted columns, for logging and for the dry-run report. */
    public int columnCount(Map<String, Target> targets) {
        return targets.values().stream().mapToInt(t -> t.columns().size()).sum();
    }
}
