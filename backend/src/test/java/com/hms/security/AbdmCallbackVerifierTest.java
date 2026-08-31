package com.hms.security;

import com.hms.application.abdm.AbdmCallbackVerifier;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WO-028 regression tests for the ABDM callback.
 *
 * <p>The defect these cover was not a missing check — it was a check that looked
 * present. {@code AbdmConsentCallbackController} carried a class docstring
 * asserting that "the protection is the gateway credential and the signature",
 * and the body read {@code artifact.path("signature")} and wrote it to a column.
 * A reviewer skimming for "is this authenticated?" would find both a claim and
 * something signature-shaped, and move on.
 *
 * <p>Combined with {@code TenantFilterAspect} disabling the tenant filter when no
 * tenant context is set, the endpoint accepted unauthenticated writes to consent
 * records in any tenant.
 */
class AbdmCallbackVerifierTest {

    private AbdmCallbackVerifier verifier;
    private MeterRegistry meters;

    private static final String SECRET = "test-shared-secret";
    private static final String BODY = "{\"notification\":{\"status\":\"REVOKED\"}}";

    @BeforeEach
    void setUp() {
        meters = new SimpleMeterRegistry();
        verifier = new AbdmCallbackVerifier(meters);
        ReflectionTestUtils.setField(verifier, "secret", SECRET);
        ReflectionTestUtils.setField(verifier, "allowUnverified", false);
    }

    private static String sign(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("A correctly signed callback is accepted")
    void acceptsValidSignature() throws Exception {
        assertThat(verifier.verify(BODY, sign(BODY, SECRET))).isTrue();
    }

    @Test
    @DisplayName("A callback signed with the wrong secret is rejected")
    void rejectsWrongSecret() throws Exception {
        assertThat(verifier.verify(BODY, sign(BODY, "not-the-secret"))).isFalse();
    }

    @Test
    @DisplayName("A tampered body is rejected even with a signature that was once valid")
    void rejectsTamperedBody() throws Exception {
        String signature = sign(BODY, SECRET);
        String tampered = BODY.replace("REVOKED", "GRANTED");

        assertThat(verifier.verify(tampered, signature)).isFalse();
    }

    @Test
    @DisplayName("A callback with no signature header is rejected")
    void rejectsMissingSignature() {
        assertThat(verifier.verify(BODY, null)).isFalse();
        assertThat(verifier.verify(BODY, "  ")).isFalse();
    }

    @Test
    @DisplayName("With no secret configured, everything is rejected — fails closed")
    void failsClosedWithoutSecret() throws Exception {
        ReflectionTestUtils.setField(verifier, "secret", "");

        // Even a signature that would be valid under some secret is refused,
        // because there is nothing to check it against. An endpoint accepting
        // unauthenticated writes to consent records is worse than one that is
        // temporarily unreachable.
        assertThat(verifier.verify(BODY, sign(BODY, SECRET))).isFalse();
        assertThat(meters.counter("hms_abdm_callback_verifications_total",
                                  "outcome", "no_secret").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("The bypass switch is off by default")
    void bypassIsOffByDefault() {
        AbdmCallbackVerifier fresh = new AbdmCallbackVerifier(new SimpleMeterRegistry());
        assertThat(ReflectionTestUtils.getField(fresh, "allowUnverified"))
            .as("hms.abdm.callback.allow-unverified must default false; "
                + "true reopens the endpoint to anyone who can reach it")
            .isIn(false, null);
    }

    @Test
    @DisplayName("Rejections are metered so a probe is visible rather than silent")
    void rejectionsAreMetered() throws Exception {
        verifier.verify(BODY, sign(BODY, "wrong"));

        assertThat(meters.counter("hms_abdm_callback_verifications_total",
                                  "outcome", "rejected").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("The controller no longer trusts an unverified body")
    void controllerCallsTheVerifier() throws IOException {
        // Source-level, because the failure mode is a reviewer seeing something
        // signature-shaped and assuming it is checked. This asserts the call
        // exists at all.
        Path controller = Paths.get(
            "src/main/java/com/hms/api/abdm/AbdmConsentCallbackController.java");
        String source = Files.readString(controller);

        assertThat(source)
            .as("the callback must verify before parsing or acting")
            .contains("verifier.verify(rawBody");
        assertThat(source)
            .as("an unverified caller gets 401, not the always-202 treatment")
            .contains("HttpStatus.UNAUTHORIZED");
    }

    @Test
    @DisplayName("Callback writes are scoped to the tenant that owns the record")
    void serviceScopesToOwningTenant() throws IOException {
        // Without this, artifacts created on the callback path are written with a
        // null tenant_id — invisible to every tenant-filtered query afterwards.
        String source = Files.readString(Paths.get(
            "src/main/java/com/hms/application/abdm/AbdmConsentService.java"));

        assertThat(source).contains("withTenantOf(");
        assertThat(source)
            .as("tenant context must be restored in a finally block; these run on "
                + "a request thread that will be reused")
            .contains("finally");
    }
}
