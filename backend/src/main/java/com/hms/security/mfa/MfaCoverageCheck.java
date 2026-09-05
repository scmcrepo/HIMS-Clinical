package com.hms.security.mfa;

import com.hms.infrastructure.persistence.shared.UserJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * How many privileged users still have no second factor — WO-029 / U-002.
 *
 * <h2>Why a gauge and not just a report</h2>
 * The rollout for this feature is OFF, then OPTIONAL, then REQUIRED, and the
 * step that goes wrong is the last one. Switching to REQUIRED while any
 * privileged user is unenrolled locks that user out, and if it happens to be all
 * of them it locks out the people who would have to switch it back.
 *
 * <p>{@code application.yml} tells an administrator to watch this number reach
 * zero before that step. This class is what makes that instruction true rather
 * than decorative. It is the same pattern as
 * {@code ComplianceContactCoverageCheck}: make the gap countable, then loud.
 *
 * <p>Runs at startup and hourly, because users are created between restarts and
 * a number that was zero last Tuesday is not evidence about today.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MfaCoverageCheck {

    private final UserJpaRepository users;
    private final MfaService mfaService;
    private final MeterRegistry meterRegistry;

    private final AtomicLong uncovered = new AtomicLong(0);
    private final AtomicLong privileged = new AtomicLong(0);

    @Value("${hms.mfa.privileged-roles:SUPERADMIN,HOSPITAL_ADMIN}")
    private String privilegedRoles;

    /**
     * Registers the gauges only. No database access.
     *
     * <p>This used to be {@code @PostConstruct} and to call {@link #report},
     * which issues a JPA query. Querying a repository during bean construction
     * forces the JPA infrastructure to initialise before Spring has finished
     * wiring it, and is a known cause of {@code jpaAuditingHandler} /
     * {@code jpaMappingContext} startup failures — one of which was hit on the
     * first real boot of this branch.
     *
     * <p>Gauges are registered here rather than in the listener so the metric
     * exists from startup and reads 0 until the first report, instead of being
     * absent and breaking a dashboard query.
     */
    @jakarta.annotation.PostConstruct
    void registerGauges() {
        meterRegistry.gauge("hms_mfa_privileged_uncovered", uncovered, AtomicLong::get);
        meterRegistry.gauge("hms_mfa_privileged_total", privileged, AtomicLong::get);
    }

    /**
     * First report, once the context is fully up.
     *
     * <p>Matches {@code ComplianceContactCoverageCheck}, which is the
     * established convention here for exactly this reason.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void reportOnStartup() {
        report();
    }

    @Scheduled(fixedDelayString = "PT1H")
    @Transactional(readOnly = true)
    public void report() {
        try {
            List<String> roles = Arrays.stream(privilegedRoles.split(","))
                                       .map(r -> r.trim().toUpperCase(Locale.ROOT))
                                       .filter(r -> !r.isEmpty())
                                       .toList();
            if (roles.isEmpty()) {
                return;
            }

            List<UUID> privilegedIds = users.findActiveIdsByRoleNames(roles);
            long missing = mfaService.uncoveredCount(privilegedIds);

            privileged.set(privilegedIds.size());
            uncovered.set(missing);

            if (missing > 0 && mfaService.mode() == MfaService.Mode.REQUIRED) {
                // Already REQUIRED with gaps: these users cannot sign in right now.
                log.error("event=mfa.coverage.locked_out uncovered={} of={} mode=REQUIRED",
                          missing, privilegedIds.size());
            } else if (missing > 0) {
                log.info("event=mfa.coverage.incomplete uncovered={} of={} mode={}",
                         missing, privilegedIds.size(), mfaService.mode());
            } else {
                log.info("event=mfa.coverage.complete privileged={} mode={}",
                         privilegedIds.size(), mfaService.mode());
            }
        } catch (Exception e) {
            // A reporting job must never take the application down, and this one
            // runs at startup. Type only, never the message: it could carry data.
            log.error("event=mfa.coverage.failed error_type={}", e.getClass().getSimpleName());
        }
    }
}
