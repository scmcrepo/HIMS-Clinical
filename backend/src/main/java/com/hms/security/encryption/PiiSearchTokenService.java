package com.hms.security.encryption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

/**
 * Produces deterministic HMAC-SHA256 search tokens for PII fields that need
 * exact-match database lookups despite being AES-encrypted.
 *
 * Problem: AES-GCM with a random IV produces different ciphertext every time
 * for the same plaintext, so SQL LIKE/= on encrypted columns never matches.
 *
 * Solution: Store a secondary HMAC-SHA256 token (a one-way fingerprint) in a
 * separate indexed column. HMAC is deterministic: same input + same key = same
 * token. This lets you query by token without revealing the plaintext.
 *
 * Threat model:
 *   - Token is NOT reversible (HMAC is one-way, unlike AES).
 *   - An attacker with DB access sees only opaque Base64 strings.
 *   - An attacker who knows the token key could brute-force short values
 *     (phone numbers) if they obtain the key — protect the HMAC key the same
 *     way as the encryption key.
 *   - Substring/fuzzy search is NOT supported via tokens; use patient number
 *     as the primary search key, and token only for exact phone lookup.
 *
 * Configuration:
 *   hms.security.search-token.key = ${HMS_SEARCH_TOKEN_KEY:}
 *   Generate: openssl rand -base64 32
 *   Must be DIFFERENT from the encryption key (defence in depth).
 */
@Service
public class PiiSearchTokenService {

    private static final String HMAC_ALGO = "HmacSHA256";

    private final byte[] tokenKeyBytes;
    private final boolean enabled;

    public PiiSearchTokenService(
            @Value("${hms.security.search-token.key:}") String base64TokenKey) {

        if (base64TokenKey == null || base64TokenKey.isBlank()) {
            this.tokenKeyBytes = null;
            this.enabled = false;
            // Log a warning but allow the application to start
            org.slf4j.LoggerFactory.getLogger(PiiSearchTokenService.class).warn(
                "PII search-token key is NOT configured. " +
                "HMAC-based duplicate-checking is DISABLED. " +
                "Set HMS_SEARCH_TOKEN_KEY (or hms.security.search-token.key) " +
                "to enable. Generate with: openssl rand -base64 32");
            return;
        }

        this.tokenKeyBytes = Base64.getDecoder().decode(base64TokenKey);
        this.enabled = true;
    }

    /**
     * Produces a Base64-encoded HMAC-SHA256 token for the given value.
     * The value is normalised before hashing (trimmed + lowercased) so that
     * "9876543210" and " 9876543210 " produce the same token.
     *
     * Returns null if value is null or blank.
     */
    public String token(String value) {
        if (!enabled || value == null || value.isBlank()) return null;
        String normalised = value.strip().toLowerCase(Locale.ROOT);
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(tokenKeyBytes, HMAC_ALGO));
            byte[] raw = mac.doFinal(normalised.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(raw);
        } catch (Exception e) {
            throw new PiiEncryptionException("Failed to generate search token", e);
        }
    }

    /**
     * Convenience: strips all non-digit characters from a phone number then
     * tokens it. "  +91-98765 43210 " → token("9876543210").
     */
    public String phoneToken(String rawPhone) {
        if (rawPhone == null) return null;
        String digits = rawPhone.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? null : token(digits);
    }
}
