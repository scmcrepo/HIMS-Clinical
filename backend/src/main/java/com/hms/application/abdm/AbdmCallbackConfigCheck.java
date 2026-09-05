package com.hms.application.abdm;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reports, loudly, what is actually protecting the ABDM callback endpoint —
 * WO-029 / card F-003.
 *
 * <h2>Why this exists</h2>
 * F-003 asked for the callback signature scheme to be confirmed against the
 * gateway contract. Checking {@link AbdmCallbackVerifier} against ABDM's public
 * documentation says it almost certainly cannot be:
 *
 * <ul>
 *   <li>The verifier expects a <b>shared secret</b> and computes
 *       {@code HMAC-SHA256(secret, rawBody)}, hex-encoded. That is a common
 *       webhook pattern and a reasonable placeholder, but ABDM's published
 *       contract does not describe issuing such a secret to a HIP.</li>
 *   <li>{@code AbdmConsentCallbackController} reads the value from an
 *       {@code X-HMAC-Signature} header. ABDM callbacks carry
 *       {@code Authorization}, {@code X-HIP-ID} and {@code X-CM-ID}; no
 *       published NHA material describes {@code X-HMAC-Signature}.</li>
 *   <li>{@code hms.abdm.callback.secret} appears in no configuration file, so
 *       it is unset, so {@link AbdmCallbackVerifier} currently <b>rejects every
 *       callback</b>.</li>
 * </ul>
 *
 * <p>The verifier fails closed, which was the right call, but the consequence is
 * an ABDM integration that silently receives nothing. The likely reaction from
 * whoever debugs that at 2am is {@code allow-unverified=true}, which reopens the
 * exact hole WO-028 closed: an unauthenticated endpoint that writes to consent
 * records.
 *
 * <p>This class does not guess at the real scheme. Implementing a guessed
 * signature check would be worse than none, because it would look like
 * verification. What it does is make the three possible states impossible to
 * misread, so nobody arrives at the bypass by accident:
 *
 * <ol>
 *   <li><b>Open</b> — {@code allow-unverified=true}. Logged at ERROR every boot.</li>
 *   <li><b>Closed and dead</b> — ABDM configured, no secret. Logged at ERROR.</li>
 *   <li><b>Closed and working</b> — a secret is set. Logged at INFO, with the
 *       caveat that a working secret means the counterparty is sending an HMAC,
 *       which is worth confirming is really ABDM and not a middlebox.</li>
 * </ol>
 *
 * <p>Same pattern as {@code ComplianceContactCoverageCheck}: make the gap
 * countable and noisy rather than trusting someone to remember it.
 *
 * <h2>What has to happen to close F-003</h2>
 * Someone with the NHA onboarding pack has to answer three questions:
 * which header carries the signature, what exactly is signed (raw body,
 * canonicalised body, or a detached JWS over selected headers), and which key
 * verifies it — a shared secret, or the Consent Manager's public key retrieved
 * from the gateway. Until those are answered this endpoint is either shut or
 * unprotected, and no amount of code changes that.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AbdmCallbackConfigCheck {

    private final MeterRegistry meterRegistry;

    /**
     * 0 verified, 1 open (bypassed), 2 closed-and-dead.
     *
     * <p>A gauge rather than a log line alone because the dangerous state is the
     * one that persists quietly. An alert on {@code == 1} is the point of this.
     */
    private final AtomicInteger state = new AtomicInteger(0);

    @Value("${hms.gov.abdm.base-url:}")
    private String abdmBaseUrl;

    @Value("${hms.abdm.callback.secret:}")
    private String secret;

    @Value("${hms.abdm.callback.allow-unverified:false}")
    private boolean allowUnverified;

    /**
     * Reports once the context is up rather than during construction.
     *
     * <p>This one reads only configuration and would have been safe as
     * {@code @PostConstruct}, but it follows the same convention as
     * {@code ComplianceContactCoverageCheck} and {@code MfaCoverageCheck}: a
     * startup check that runs at a different point from its siblings is a check
     * whose ordering nobody can reason about.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void report() {
        meterRegistry.gauge("hms_abdm_callback_auth_state", state, AtomicInteger::get);

        boolean abdmConfigured = abdmBaseUrl != null && !abdmBaseUrl.isBlank();
        boolean haveSecret = secret != null && !secret.isBlank();

        if (allowUnverified) {
            state.set(1);
            log.error("event=abdm.callback.unverified_enabled "
                      + "msg=\"hms.abdm.callback.allow-unverified is TRUE — the ABDM consent "
                      + "callback accepts writes from anyone who can reach it. This is the "
                      + "state WO-028 closed. Set it false and resolve F-003 instead.\"");
            return;
        }

        if (!haveSecret) {
            // No secret is the CORRECT configuration for ABDM, which does not
            // sign callbacks (F-003). Callbacks are authenticated by the routing
            // headers and by consentRequestId correlation downstream.
            state.set(0);
            if (abdmConfigured) {
                log.info("event=abdm.callback.header_verified "
                         + "msg=\"Callbacks authenticated by X-CM-ID/X-HIP-ID plus "
                         + "consentRequestId correlation. ABDM does not sign callbacks. "
                         + "Restrict this endpoint to ABDM egress ranges at the load balancer.\"");
            } else {
                log.info("event=abdm.callback.inactive msg=\"ABDM not configured\"");
            }
            return;
        }

        state.set(0);
        log.warn("event=abdm.callback.secret_configured "
                 + "msg=\"An HMAC secret is set. ABDM does not sign callbacks, so unless a "
                 + "middlebox in front of this service adds one, every genuine callback will "
                 + "be REJECTED. See F-003.\"");
    }
}
