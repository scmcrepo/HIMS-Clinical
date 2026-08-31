package com.hms.application.incident;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Turns blocked cross-tenant access attempts into incidents.
 *
 * <p>Before this, a {@code CrossTenantAccessException} produced a WARN line and a
 * {@code printStackTrace()} to stderr — outside the structured logging pipeline,
 * so it never reached Loki and nothing counted it. The isolation guard worked and
 * nobody would have known it had fired.
 *
 * <p>A blocked attempt is not itself a breach: the guard did its job. But a
 * <em>burst</em> of them means either a serious bug in tenant scoping or someone
 * probing, and both are worth waking a person for. A single stray attempt is
 * counted and not escalated, because an incident raised on every isolated event
 * trains people to close incidents without reading them.
 *
 * <h2>Deliberately does not notify anyone</h2>
 *
 * <p>It raises an internal incident and shouts. Filing a breach report with the
 * Data Protection Board is an irreversible external act with legal consequences,
 * and an automated false positive doing that on a hospital's behalf would be its
 * own serious incident. A human decides.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrossTenantAccessDetector {

    private final SecurityIncidentService incidentService;
    private final MeterRegistry meterRegistry;

    /**
     * Attempts within the window before an incident is raised.
     *
     * <p>Three, not one. Tuned to catch a pattern rather than an anomaly — but
     * every attempt is metered regardless, so a single event is still visible on
     * a dashboard even when it does not escalate.
     */
    @Value("${hms.security.cross-tenant.incident-threshold:3}")
    private int threshold;

    @Value("${hms.security.cross-tenant.window-minutes:15}")
    private int windowMinutes;

    private final AtomicInteger windowCount = new AtomicInteger();
    private volatile Instant windowStart = Instant.now();

    /**
     * Record a blocked cross-tenant access attempt.
     *
     * <p>Called from the exception handler. Never throws: a detector that can
     * break the request it is observing is worse than no detector, because it
     * turns a handled security event into an unhandled server error.
     *
     * @param context where it happened — a class or endpoint name, never the
     *                exception message, which may quote identifiers
     */
    public void recordBlockedAttempt(String context) {
        try {
            meterRegistry.counter("hms_cross_tenant_blocked_total",
                                  "context", context == null ? "unknown" : context).increment();

            Instant now = Instant.now();
            if (Duration.between(windowStart, now).toMinutes() >= windowMinutes) {
                windowStart = now;
                windowCount.set(0);
            }

            int count = windowCount.incrementAndGet();
            log.warn("event=security.cross_tenant.blocked context={} window_count={}",
                     context, count);

            if (count == threshold) {
                // Fires exactly on the threshold, not above it, so a sustained
                // attack raises one incident rather than one per request.
                raiseIncident(context, count);
            }
        } catch (RuntimeException e) {
            log.error("event=security.detector.failed error_type={}", e.getClass().getSimpleName());
        }
    }

    private void raiseIncident(String context, int count) {
        incidentService.raise(
            "CROSS_TENANT_ACCESS",
            "HIGH",
            "Repeated cross-tenant access attempts were blocked",
            count + " cross-tenant access attempts were blocked within "
                + windowMinutes + " minutes, at: " + context
                + ". The tenant isolation guard prevented access in each case. "
                + "This is either a defect in tenant scoping or deliberate probing; "
                + "both need investigation. No data is known to have been disclosed, "
                + "which is why the scope is recorded as uncertain rather than zero.",
            "AUTOMATED_DETECTION",
            "Potentially any tenant-scoped data",
            Instant.now(),
            true);

        log.error("event=security.cross_tenant.incident_raised context={} count={}",
                  context, count);
    }
}
