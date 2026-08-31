package com.hms.security.encryption;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/**
 * Encrypts a {@code Map<String, Object>} field that was previously stored as
 * JSONB.
 *
 * <p>Built for WO-029. {@code Patient.pediatric_data} and
 * {@code Patient.template_data} are unstructured maps whose contents depend on
 * how each tenant configures its forms. Data minimisation cannot be assessed for
 * a field that can hold anything, and paediatric data is the category Rule 12
 * treats most carefully — so the honest position is that both may contain
 * anything and must be encrypted.
 *
 * <h2>The column stops being JSONB</h2>
 *
 * <p>Ciphertext is opaque, so the database can no longer index into it, query
 * inside it, or validate that it is JSON. The column becomes TEXT and the value
 * becomes a blob only the application can read.
 *
 * <p>That is a real loss and worth stating plainly: any future feature wanting
 * to filter patients by a key inside {@code template_data} will not be able to.
 * Blind indexes are the escape hatch if a specific key ever needs to be
 * searchable — the same pattern {@code contactNumberToken} uses — but that has
 * to be designed per key rather than granted wholesale.
 *
 * <h2>Reading rows written before encryption</h2>
 *
 * <p>{@link #convertToEntityAttribute} tolerates plaintext JSON. A column
 * mid-migration holds both forms, and a converter that threw on the old one
 * would make every historical paediatric record unreadable the moment the
 * annotation shipped — the exact failure V208 caused and V212 had to repair.
 *
 * <p>It also returns an empty map rather than throwing on malformed JSON, for
 * the same reason: this field holds supplementary form data, and one bad row
 * must not block a clinician from loading a patient.
 */
@Converter(autoApply = false)
@Component
public class EncryptedJsonMapConverter implements AttributeConverter<Map<String, Object>, String> {

    /**
     * Injected through a static holder because JPA instantiates converters by
     * reflection, outside the Spring context. Same approach as
     * {@link EncryptedStringConverter}.
     */
    private static PiiEncryptionService encryptionService;

    private static final org.slf4j.Logger log =
        org.slf4j.LoggerFactory.getLogger(EncryptedJsonMapConverter.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE =
        new TypeReference<>() {};

    @Autowired
    public void setEncryptionService(PiiEncryptionService service) {
        EncryptedJsonMapConverter.encryptionService = service;
    }

    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        if (attribute == null) {
            return null;
        }
        requireService();
        try {
            // An empty map round-trips as "{}" rather than null, so "the tenant
            // configured no fields" stays distinguishable from "never set".
            return encryptionService.encrypt(MAPPER.writeValueAsString(attribute));
        } catch (PiiEncryptionException e) {
            throw e;
        } catch (Exception e) {
            // Never include the value: this map is the thing being protected.
            throw new PiiEncryptionException(
                "Failed to serialise map for encryption: " + e.getClass().getSimpleName(), e);
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        requireService();

        String json;
        if (encryptionService.looksEncrypted(dbData)) {
            json = encryptionService.decrypt(dbData);
        } else {
            // A row written before the annotation shipped, or before
            // PiiMigrationRunner reached it. Reading it is correct; the runner
            // encrypts it on its next pass.
            json = dbData;
        }

        try {
            Map<String, Object> parsed = MAPPER.readValue(json, MAP_TYPE);
            return parsed == null ? null : parsed;
        } catch (Exception e) {
            // Deliberately does NOT throw. This map holds supplementary form
            // data; a single malformed row must not make the whole patient
            // record unloadable, because that would block care over a
            // data-quality problem. The caller sees an empty map, and the
            // operator sees this line.
            //
            // Never logs the value or the decrypted JSON — that is the data
            // being protected.
            log.error("event=pii.json.parse_failed error_type={}", e.getClass().getSimpleName());
            return Collections.emptyMap();
        }
    }

    private static void requireService() {
        if (encryptionService == null) {
            throw new PiiEncryptionException(
                "EncryptedJsonMapConverter used before Spring injected PiiEncryptionService");
        }
    }

    /** Exposed for the migration runner, which works with raw strings. */
    public static Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return MAPPER.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
