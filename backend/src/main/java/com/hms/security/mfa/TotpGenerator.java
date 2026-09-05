package com.hms.security.mfa;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * RFC 6238 time-based one-time passwords, and the RFC 4648 Base32 codec the
 * authenticator apps expect.
 *
 * <h2>Why this is hand-written</h2>
 * TOTP is a HMAC, a truncation and a modulus. Pulling in a library for it would
 * add a dependency to the authentication path — the highest blast-radius code in
 * the system — to avoid about sixty lines. This class has no dependencies beyond
 * the JDK, which also means {@code TotpGeneratorTest} can check it against the
 * published RFC 6238 test vectors without a Spring context or a database. An
 * implementation that agrees with the RFC's own vectors is one that will agree
 * with Google Authenticator, Authy and 1Password.
 *
 * <h2>SHA-1</h2>
 * RFC 6238 permits SHA-1, SHA-256 and SHA-512; SHA-1 is what every mainstream
 * authenticator app actually implements, and it is what an {@code otpauth://}
 * URI means when it omits the algorithm parameter. SHA-1's collision weaknesses
 * do not apply to HMAC-SHA1, which remains unbroken as a MAC. Choosing SHA-256
 * here would be defensible on paper and would fail against most users' phones,
 * which is a worse security outcome than SHA-1.
 *
 * <p>Stateless and thread-safe. All methods are static; nothing here decides
 * whether a code should be accepted — that is {@code MfaService}, which also
 * owns the replay guard.
 */
public final class TotpGenerator {

    /** Seconds per time step. 30 is the near-universal default and what apps assume. */
    public static final int TIME_STEP_SECONDS = 30;

    /** Digits in a code. */
    public static final int DIGITS = 6;

    /**
     * Steps of clock skew tolerated either side of the current one.
     *
     * <p>One step, so a code is valid for at most 90 seconds. Two would be
     * kinder to a badly set phone clock and would triple the window an observed
     * code stays usable in. The replay guard makes a used code worthless
     * immediately, so the residual risk is an unused code being observed, and
     * that window should stay small.
     */
    public static final int SKEW_STEPS = 1;

    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int[] POWERS = {1, 10, 100, 1_000, 10_000, 100_000, 1_000_000,
                                         10_000_000, 100_000_000};
    private static final SecureRandom RANDOM = new SecureRandom();

    private TotpGenerator() {
    }

    /**
     * A fresh 160-bit secret, Base32-encoded.
     *
     * <p>160 bits because that is HMAC-SHA1's block-derived key size; shorter
     * secrets are accepted by apps but reduce the work of a brute force against
     * a stolen database to below the cost of the encryption around it.
     */
    public static String generateSecret() {
        byte[] buffer = new byte[20];
        RANDOM.nextBytes(buffer);
        return base32Encode(buffer);
    }

    /** The RFC 6238 time step containing the given epoch second. */
    public static long timeStep(long epochSeconds) {
        return epochSeconds / TIME_STEP_SECONDS;
    }

    /**
     * The code for a Base32 secret at a given time step.
     *
     * @return a zero-padded 6-digit string
     */
    public static String generate(String base32Secret, long timeStep) {
        byte[] key = base32Decode(base32Secret);
        byte[] counter = ByteBuffer.allocate(8).putLong(timeStep).array();

        byte[] hash;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            hash = mac.doFinal(counter);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // HmacSHA1 is mandatory in every JDK. If this throws, the platform is
            // broken in a way that should stop the login rather than be swallowed.
            throw new IllegalStateException("HMAC-SHA1 unavailable", e);
        }

        // RFC 4226 dynamic truncation: the low nibble of the last byte picks the
        // offset, and the high bit of the selected word is masked off so the
        // result is positive on every platform.
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset]     & 0x7F) << 24)
                   | ((hash[offset + 1] & 0xFF) << 16)
                   | ((hash[offset + 2] & 0xFF) << 8)
                   |  (hash[offset + 3] & 0xFF);

        int code = binary % POWERS[DIGITS];
        return String.format(Locale.ROOT, "%0" + DIGITS + "d", code);
    }

    /**
     * The time step whose code matches, searching the tolerated window.
     *
     * <p>Returns the matching step rather than a boolean because the caller
     * needs it for the replay guard: knowing that a code was valid is not enough
     * to know whether it has already been used.
     *
     * @return the matching step, or {@code null} if none matched
     */
    public static Long matchingStep(String base32Secret, String candidate, long currentStep) {
        if (candidate == null) {
            return null;
        }
        String cleaned = candidate.replaceAll("\\s", "");
        if (cleaned.length() != DIGITS) {
            return null;
        }

        for (long step = currentStep - SKEW_STEPS; step <= currentStep + SKEW_STEPS; step++) {
            if (constantTimeEquals(generate(base32Secret, step), cleaned)) {
                return step;
            }
        }
        return null;
    }

    /**
     * Length-independent, content-constant-time comparison.
     *
     * <p>Both arguments are six ASCII digits, so the timing signal from
     * {@code String.equals} would be tiny. It is also free to remove, and a
     * comparison of a secret-derived value is the wrong place to reason about
     * whether an attacker can measure something.
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    /**
     * The {@code otpauth://} URI an authenticator app scans.
     *
     * <p>The issuer appears twice — once as a label prefix and once as a
     * parameter — which looks redundant and is not: older apps read only the
     * prefix, newer ones only the parameter, and omitting either makes the entry
     * show up unlabelled on some phones. An account the user cannot identify in
     * their app is an account they will delete.
     */
    public static String provisioningUri(String issuer, String accountName, String base32Secret) {
        String label = urlEncode(issuer) + ":" + urlEncode(accountName);
        return "otpauth://totp/" + label
             + "?secret=" + base32Secret
             + "&issuer=" + urlEncode(issuer)
             + "&algorithm=SHA1"
             + "&digits=" + DIGITS
             + "&period=" + TIME_STEP_SECONDS;
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s == null ? "" : s,
                                          java.nio.charset.StandardCharsets.UTF_8)
                                  .replace("+", "%20");
    }

    // ── Base32, RFC 4648 without padding on encode ──────────────────────────

    static String base32Encode(byte[] data) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;

        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                out.append(BASE32_ALPHABET.charAt((buffer >> (bitsLeft - 5)) & 0x1F));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            out.append(BASE32_ALPHABET.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return out.toString();
    }

    static byte[] base32Decode(String encoded) {
        // Uppercase and strip padding and spaces: users retype secrets by hand
        // when a camera will not focus, and rejecting "jbsw y3dp" as invalid
        // would be a support ticket rather than a security control.
        String clean = encoded.replaceAll("[=\\s-]", "").toUpperCase(Locale.ROOT);
        if (clean.isEmpty()) {
            throw new IllegalArgumentException("Empty TOTP secret");
        }

        byte[] out = new byte[clean.length() * 5 / 8];
        int buffer = 0;
        int bitsLeft = 0;
        int index = 0;

        for (char c : clean.toCharArray()) {
            int value = BASE32_ALPHABET.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("Not valid Base32: " + c);
            }
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out[index++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return out;
    }
}
