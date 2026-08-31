package com.hms.application.grievance;

import com.hms.infrastructure.persistence.grievance.ComplianceContactJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Reports tenants with no published data protection contact — card J-006.
 *
 * <p>V210 created {@code compliance_contacts} and seeded no rows, because the
 * details are per-hospital and cannot be invented. The consequence is that
 * {@code GET /compliance/grievances/contact/public} returns 404 for every tenant
 * and <b>s. 8(9) is unmet for each one</b> — a hospital is required to publish a
 * means of raising a grievance, and there is currently nothing to publish.
 *
 * <p>This class does not fix that. It cannot: nobody but the hospital knows its
 * own contact details. What it does is stop the gap being <em>invisible</em>,
 * which is the property that lets an unmet obligation survive a year of
 * deployments unnoticed.
 *
 * <p>The same reasoning as the retention engine's {@code RetentionNeverArmed}
 * alert: building the machinery does not discharge the duty, and the difference
 * between "built" and "in effect" needs to be measurable or it will be assumed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplianceContactCoverageCheck {

    private final ComplianceContactJpaRepository contacts;
    private final MeterRegistry meterRegistry;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Check once the application is up.
     *
     * <p>Runs after startup rather than during it, and never throws: a missing
     * contact is a compliance gap, not a reason to stop a hospital system from
     * booting.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void checkOnStartup() {
        report();
    }

    /**
     * Re-check daily.
     *
     * <p>Tenants are onboarded between restarts, and a check that only ran at
     * startup would miss every hospital added since — which is precisely the
     * population most likely to lack a contact.
     */
    @Scheduled(cron = "0 45 7 * * *")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void checkDaily() {
        report();
    }

    @SuppressWarnings("unchecked")
    void report() {
        try {
            // Deliberately tenant-agnostic and raw: this asks a question about
            // every tenant at once, which no tenant-scoped repository can
            // answer. Reads ids only — no personal data crosses a boundary here.
            List<UUID> allTenants = entityManager
                .createNativeQuery("SELECT id FROM tenants WHERE status = 1")
                .getResultList();

            List<UUID> covered = contacts.tenantsWithPublishedContact();

            List<UUID> missing = allTenants.stream()
                .filter(t -> !covered.contains(t))
                .toList();

            meterRegistry.gauge("hms_tenants_without_contact", missing.size());
            meterRegistry.gauge("hms_tenants_total", allTenants.size());

            if (missing.isEmpty()) {
                log.info("event=compliance.contact.coverage tenants={} missing=0",
                         allTenants.size());
                return;
            }

            // ERROR, not WARN. This is a statutory obligation that is currently
            // unmet, and the log level is what decides whether anyone sees it.
            log.error("event=compliance.contact.missing tenant_count={} of={} "
                      + "msg=\"s.8(9) requires a published grievance contact; "
                      + "these tenants have none and their public contact endpoint "
                      + "returns 404\"",
                      missing.size(), allTenants.size());

            for (UUID tenantId : missing) {
                log.error("event=compliance.contact.missing.tenant tenant_id={}", tenantId);
            }
        } catch (RuntimeException e) {
            log.error("event=compliance.contact.coverage.failed error_type={}",
                      e.getClass().getSimpleName());
        }
    }
}
