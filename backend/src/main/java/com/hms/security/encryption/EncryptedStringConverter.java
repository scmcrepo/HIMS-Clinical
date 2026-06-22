package com.hms.security.encryption;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * JPA {@link AttributeConverter} that transparently encrypts/decrypts String fields
 * using AES-256-GCM via {@link PiiEncryptionService}.
 *
 * Usage — annotate any String field in a JPA entity:
 *
 * <pre>
 * {@literal @}Column(name = "contact_number", length = 512)
 * {@literal @}Convert(converter = EncryptedStringConverter.class)
 * private String contactNumber;
 * </pre>
 *
 * Column length note:
 *   Encrypted values are larger than plaintext (IV + ciphertext + GCM tag, Base64-encoded).
 *   A plaintext of N chars becomes roughly ⌈(N + 28) × 4/3⌉ Base64 chars.
 *   Rule of thumb: set column length to max(512, original_length × 2).
 *
 * autoApply = false: we intentionally encrypt only annotated fields, not all Strings.
 */
@Converter(autoApply = false)
@Component
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    /**
     * Spring injects this via the static holder below because JPA instantiates
     * converters via reflection — outside the Spring context.
     */
    private static PiiEncryptionService encryptionService;

    @Autowired
    public void setEncryptionService(PiiEncryptionService service) {
        EncryptedStringConverter.encryptionService = service;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (encryptionService == null) {
            throw new PiiEncryptionException(
                "EncryptedStringConverter: PiiEncryptionService not yet initialised. " +
                "Check Spring context startup order.");
        }
        return encryptionService.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (encryptionService == null) {
            throw new PiiEncryptionException(
                "EncryptedStringConverter: PiiEncryptionService not yet initialised.");
        }
        return encryptionService.decrypt(dbData);
    }
}
