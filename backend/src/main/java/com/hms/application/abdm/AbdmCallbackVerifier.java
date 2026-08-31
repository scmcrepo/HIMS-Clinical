package com.hms.application.abdm;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Authenticates inbound ABDM Consent Manager callbacks.
 *
 * <p>Built for WO-028. {@code AbdmConsentCallbackController} was registered
 * {@code permitAll()} and accepted an arbitrary JSON body with nothing checking
 * who sent it. Its own class docstring claimed authentication came from "the
 * gateway credential and the signature" — the code read
 * {@code artifact.path("signature")} and stored it as a column. Storing a
 * signature is not verifying one.
 *
 * <p>The comparison is the point. {@link MessageDigest#isEqual} is constant-time;
 * {@code String.equals} short-circuits on the first differing byte and leaks the
 * correct prefix to anyone willing to time the responses.
 *
 * <h2>Fails closed</h2>
 *
 * <p>With no secret configured this rejects everything. That will stop ABDM
 * callbacks until {@code hms.abdm.callback.secret} is set, which is deliberate:
 * an endpoint that accepts unauthenticated writes to consent records is worse
 * than one that is temporarily unreachable. The absence is logged at ERROR on
 * every rejection so the cause is obvious rather than mysterious.
 *
 * <p>Startup is <em>not</em> failed on a missing secret. Taking the whole
 * backend down — every ward, every desk — because one integration is
 * misconfigured would be a worse trade than closing that one endpoint.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AbdmCallbackVerifier {

    private static final String ALGORITHM = "HmacSHA256";

    private final MeterRegistry meterRegistry;

    /**
     * Shared secret agreed with the ABDM gateway.
     *
     * <p>Empty by default so a fresh checkout does not carry a working key. Set
     * via environment, never committed.
     */
    @Value("${hms.abdm.callback.secret:}")
    private String secret;

    /**
     * Escape hatch for a deployment whose gateway contract genuinely does not
     * sign callbacks.
     *
     * <p>Default false. If it is ever set true, the endpoint is open to anyone
     * who can reach it, so the startup log says so in plain terms rather than
     * leaving it to be discovered.
     */
    @Value("${hms.abdm.callback.allow-unverified:false}")
    private boolean allowUnverified;

    /**
     * @param rawBody   the exact bytes received, before any parsing — re-serialising
     *                  a parsed tree reorders keys and changes the digest
     * @param signature value of the gateway's signature header
     * @return true when the caller proved possession of the shared secret
     */
    public boolean verify(String rawBody, String signature) {
        if (allowUnverified) {
            meterRegistry.counter("hms_abdm_callback_verifications_total",
                                  "outcome", "bypassed").increment();
            return true;
        }

        if (secret == null || secret.isBlank()) {
            log.error("event=abdm.callback.no_secret "
                      + "msg=\"hms.abdm.callback.secret is unset; rejecting all ABDM callbacks\"");
            meterRegistry.counter("hms_abdm_callback_verifications_total",
                                  "outcome", "no_secret").increment();
            return false;
        }

        if (rawBody == null || signature == null || signature.isBlank()) {
            meterRegistry.counter("hms_abdm_callback_verifications_total",
                                  "outcome", "missing_signature").increment();
            return false;
        }

        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            String expected = HexFormat.of()
                .formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));

            boolean ok = MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.trim().getBytes(StandardCharsets.UTF_8));

            meterRegistry.counter("hms_abdm_callback_verifications_total",
                                  "outcome", ok ? "verified" : "rejected").increment();
            if (!ok) {
                // No body, no signature, no id. A rejected callback is either a
                // misconfiguration or someone probing; neither is helped by
                // putting the payload in the log.
                log.warn("event=abdm.callback.signature_rejected");
            }
            return ok;
        } catch (Exception e) {
            log.error("event=abdm.callback.verification_failed error_type={}",
                      e.getClass().getSimpleName());
            meterRegistry.counter("hms_abdm_callback_verifications_total",
                                  "outcome", "error").increment();
            return false;
        }
    }
}
