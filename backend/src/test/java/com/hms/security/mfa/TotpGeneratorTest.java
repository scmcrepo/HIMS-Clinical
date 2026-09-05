package com.hms.security.mfa;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link TotpGenerator} against the test vectors published in RFC 6238 itself.
 *
 * <h2>Why these vectors and not hand-rolled ones</h2>
 * A TOTP implementation is only useful if it agrees with Google Authenticator,
 * Authy, 1Password and every other app a user might already have on their phone.
 * Tests written against this implementation's own output would pass just as
 * happily if the truncation offset were wrong — they would only prove the code
 * is consistent with itself, which is exactly the property that does not matter
 * here.
 *
 * <p>The RFC's Appendix B vectors use the ASCII seed "12345678901234567890" and
 * are quoted as 8 digits; the 6-digit code is the last six, because truncation
 * is a modulus and 10^6 divides 10^8. An implementation that reproduces these
 * will interoperate with the apps.
 */
@DisplayName("TOTP — RFC 6238 conformance")
class TotpGeneratorTest {

    /** RFC 6238 Appendix B seed, ASCII. */
    private static final byte[] RFC_SEED = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    private static String rfcSecret() {
        return TotpGenerator.base32Encode(RFC_SEED);
    }

    @Test
    @DisplayName("the RFC seed Base32-encodes to the documented value and round-trips")
    void base32MatchesTheKnownEncoding() {
        assertThat(rfcSecret()).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(TotpGenerator.base32Decode(rfcSecret())).isEqualTo(RFC_SEED);
    }

    @ParameterizedTest(name = "T={0} -> {1}")
    @CsvSource({
        "59,          287082",
        "1111111109,  081804",
        "1111111111,  050471",
        "1234567890,  005924",
        "2000000000,  279037",
        "20000000000, 353130",
    })
    @DisplayName("every RFC 6238 SHA-1 vector reproduces exactly")
    void rfcVectors(long epochSeconds, String expected) {
        long step = TotpGenerator.timeStep(epochSeconds);
        assertThat(TotpGenerator.generate(rfcSecret(), step)).isEqualTo(expected);
    }

    @Test
    @DisplayName("codes are always six digits, including when the value is small")
    void codesAreZeroPadded() {
        // T=1234567890 produces 005924 — two leading zeros. Stripping them would
        // produce a code no app would ever send, and the bug would hide for
        // roughly one login in a hundred.
        assertThat(TotpGenerator.generate(rfcSecret(), TotpGenerator.timeStep(1234567890L)))
            .hasSize(6)
            .isEqualTo("005924");
    }

    @Test
    @DisplayName("matchingStep accepts one step either side and returns which one")
    void skewWindowIsOneStepEitherSide() {
        String secret = rfcSecret();
        long now = 1_000_000L;

        for (long offset = -1; offset <= 1; offset++) {
            String code = TotpGenerator.generate(secret, now + offset);
            assertThat(TotpGenerator.matchingStep(secret, code, now))
                .as("offset %d should be inside the window", offset)
                .isEqualTo(now + offset);
        }

        // The caller needs the step, not a boolean: the replay guard cannot work
        // without knowing which step was accepted.
        assertThat(TotpGenerator.matchingStep(secret, TotpGenerator.generate(secret, now - 2), now))
            .as("two steps back is outside the window")
            .isNull();
        assertThat(TotpGenerator.matchingStep(secret, TotpGenerator.generate(secret, now + 2), now))
            .as("two steps forward is outside the window")
            .isNull();
    }

    @Test
    @DisplayName("malformed input is rejected rather than throwing")
    void malformedCandidatesAreRejected() {
        String secret = rfcSecret();
        assertThat(TotpGenerator.matchingStep(secret, null, 1)).isNull();
        assertThat(TotpGenerator.matchingStep(secret, "", 1)).isNull();
        assertThat(TotpGenerator.matchingStep(secret, "12345", 1)).isNull();
        assertThat(TotpGenerator.matchingStep(secret, "1234567", 1)).isNull();
        assertThat(TotpGenerator.matchingStep(secret, "abcdef", 1)).isNull();
    }

    @Test
    @DisplayName("whitespace in a typed code is tolerated")
    void whitespaceIsStripped() {
        String secret = rfcSecret();
        String code = TotpGenerator.generate(secret, 1000L);
        String spaced = code.substring(0, 3) + " " + code.substring(3);

        // Authenticator apps display codes as "123 456" and users copy what they
        // see. Refusing that is a support ticket, not a security control.
        assertThat(TotpGenerator.matchingStep(secret, spaced, 1000L)).isEqualTo(1000L);
    }

    @Test
    @DisplayName("generated secrets are 160-bit and decode cleanly")
    void generatedSecretsAreFullLength() {
        for (int i = 0; i < 20; i++) {
            String secret = TotpGenerator.generateSecret();
            assertThat(TotpGenerator.base32Decode(secret)).hasSize(20);
            // A fresh secret must be usable immediately, not just well-formed.
            String code = TotpGenerator.generate(secret, 42L);
            assertThat(TotpGenerator.matchingStep(secret, code, 42L)).isEqualTo(42L);
        }
    }

    @Test
    @DisplayName("secrets are not predictable between calls")
    void secretsDiffer() {
        assertThat(TotpGenerator.generateSecret()).isNotEqualTo(TotpGenerator.generateSecret());
    }

    @Test
    @DisplayName("Base32 decoding is forgiving of case, padding and separators")
    void base32DecodeIsForgiving() {
        assertThat(TotpGenerator.base32Decode("gezdgnbvgy3tqojqgezdgnbvgy3tqojq"))
            .as("users retype secrets in lower case when a camera will not focus")
            .isEqualTo(RFC_SEED);
        assertThat(TotpGenerator.base32Decode("GEZD GNBV GY3T QOJQ GEZD GNBV GY3T QOJQ"))
            .isEqualTo(RFC_SEED);

        assertThatThrownBy(() -> TotpGenerator.base32Decode("not-base32-!"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TotpGenerator.base32Decode(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the provisioning URI carries the issuer twice, on purpose")
    void provisioningUriIsAppCompatible() {
        String uri = TotpGenerator.provisioningUri("HIMS Clinical", "priya@apollo", "ABCDEF");

        // Older apps read the label prefix, newer ones the parameter. Dropping
        // either makes the entry appear unlabelled on some phones, and an entry
        // the user cannot identify is one they delete.
        assertThat(uri).startsWith("otpauth://totp/HIMS%20Clinical:");
        assertThat(uri).contains("issuer=HIMS%20Clinical");
        assertThat(uri).contains("secret=ABCDEF");
        assertThat(uri).contains("algorithm=SHA1").contains("digits=6").contains("period=30");
    }
}
