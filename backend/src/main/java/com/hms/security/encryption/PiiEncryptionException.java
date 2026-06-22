package com.hms.security.encryption;

/**
 * Thrown when PII encryption or decryption fails.
 * This is a RuntimeException so it propagates through JPA converters naturally.
 * Callers should NOT catch this silently — a decrypt failure means data integrity
 * is at risk and should surface as a 500 to prevent exposing corrupted data.
 */
public class PiiEncryptionException extends RuntimeException {

    public PiiEncryptionException(String message, Throwable cause) {
        super(message, cause);
    }

    public PiiEncryptionException(String message) {
        super(message);
    }
}
