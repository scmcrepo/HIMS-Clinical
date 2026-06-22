package com.hms.security.encryption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption service for PII fields stored in the database.
 *
 * Algorithm : AES/GCM/NoPadding (authenticated encryption — prevents tampering)
 * Key size  : 256-bit (32 bytes), supplied via environment variable HMS_ENCRYPTION_KEY
 * IV size   : 96-bit (12 bytes), randomly generated per encryption call
 * Tag size  : 128-bit (16 bytes), appended automatically by GCM
 *
 * Stored format: Base64( IV[12] || CipherText || AuthTag[16] )
 *
 * Usage:
 *   - Annotate JPA entity fields with @Convert(converter = EncryptedStringConverter.class)
 *   - All reads/writes are transparent — no service layer changes needed.
 *
 * Key rotation:
 *   - To rotate keys: decrypt all rows with old key, re-encrypt with new key.
 *   - The migration utility {@link PiiKeyRotationUtil} handles this.
 */
@Service
public class PiiEncryptionService {

    private static final Logger log = LoggerFactory.getLogger(PiiEncryptionService.class);

    private static final String ALGORITHM        = "AES/GCM/NoPadding";
    private static final int    GCM_IV_LENGTH    = 12;   // bytes
    private static final int    GCM_TAG_BITS     = 128;  // bits
    private static final int    KEY_LENGTH_BYTES = 32;   // 256-bit AES

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();
    private final boolean enabled;

    /**
     * The encryption key is injected from environment variable HMS_ENCRYPTION_KEY.
     * Expected format: Base64-encoded 32-byte (256-bit) key.
     *
     * Generate a new key with:
     *   openssl rand -base64 32
     */
    public PiiEncryptionService(
            @Value("${hms.security.encryption.key:}") String base64Key) {

        if (base64Key == null || base64Key.isBlank()) {
            this.secretKey = null;
            this.enabled = false;
            log.warn("⚠ PII encryption key is NOT configured. " +
                "Data will be stored in PLAINTEXT. " +
                "Set HMS_ENCRYPTION_KEY (or hms.security.encryption.key) " +
                "to a Base64-encoded 256-bit key. " +
                "Generate one with: openssl rand -base64 32");
            return;
        }

        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                "HMS_ENCRYPTION_KEY must be exactly 32 bytes (256 bits) after Base64 decoding. " +
                "Got " + keyBytes.length + " bytes.");
        }

        this.secretKey = new SecretKeySpec(keyBytes, "AES");
        this.enabled = true;
        log.info("PII encryption service initialised with AES-256-GCM");
    }

    /**
     * Encrypts a plaintext string and returns a Base64-encoded ciphertext.
     * Returns null if the input is null (preserves DB nullability).
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        if (!enabled) return plaintext; // Pass-through when encryption is disabled
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Pack: IV || ciphertext (GCM auth tag is appended inside ciphertext by JCE)
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);

            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new PiiEncryptionException("Failed to encrypt PII field", e);
        }
    }

    /**
     * Decrypts a Base64-encoded ciphertext produced by {@link #encrypt(String)}.
     * Returns null if the input is null.
     *
     * Throws {@link PiiEncryptionException} if decryption or authentication fails,
     * which prevents silently returning corrupted/tampered data.
     */
    public String decrypt(String base64Ciphertext) {
        if (base64Ciphertext == null) return null;
        if (!enabled) return base64Ciphertext; // Pass-through when encryption is disabled
        if (!looksEncrypted(base64Ciphertext)) return base64Ciphertext; // Robust pass-through for plaintext values
        try {
            byte[] packed = Base64.getDecoder().decode(base64Ciphertext);

            ByteBuffer buffer = ByteBuffer.wrap(packed);
            byte[] iv         = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new PiiEncryptionException("Failed to decrypt PII field — data may be corrupted or key mismatch", e);
        }
    }

    /**
     * Checks whether a stored value looks like an encrypted blob.
     * Useful for migration utilities to skip already-encrypted rows.
     */
    public boolean looksEncrypted(String value) {
        if (value == null || value.length() < 20) return false;
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            return decoded.length > GCM_IV_LENGTH;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
