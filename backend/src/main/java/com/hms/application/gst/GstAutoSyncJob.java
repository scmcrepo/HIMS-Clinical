package com.hms.application.gst;

import com.hms.domain.gst.model.GstConfiguration;
import com.hms.domain.gst.model.GstReturnType;
import com.hms.domain.gst.model.GstSyncFrequency;
import com.hms.infrastructure.persistence.gst.GstConfigurationJpaRepository;
import com.hms.infrastructure.tenant.BranchContext;
import com.hms.infrastructure.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Generates GSTR-1 drafts on the schedule each hospital configured (A1's auto-sync).
 *
 * <p>What "auto-sync" means here is auto-<em>generate</em>, not auto-file. Nothing is
 * transmitted — a hospital reviews and files. The value is that the return, and more
 * usefully its validation errors, appear early: an unclassified service or a malformed
 * HSN code surfaces days before the filing deadline instead of on the morning of it.
 *
 * <p><b>DAILY</b> rebuilds the current month, superseding yesterday's draft.
 * <b>MONTHLY</b> builds the previous month once, on the 1st. Either way the scheduler
 * keeps exactly one draft per period and never touches a return a person generated.
 *
 * <p><b>This job does not run yet.</b> The application has no {@code @EnableScheduling},
 * so none of its ten {@code @Scheduled} methods are registered. Enabling it is a separate
 * decision with a wide blast radius — it would also start the retention deletion and
 * consent expiry jobs, which have never run.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GstAutoSyncJob {

    private final GstConfigurationJpaRepository configRepo;
    private final GstFilingService filingService;

    /** 02:40, after the nightly compliance jobs and before anyone is at a desk. */
    @Scheduled(cron = "0 40 2 * * *")
    public void run() {
        LocalDate today = LocalDate.now();
        List<GstConfiguration> configs = configRepo.findAll();

        for (GstConfiguration config : configs) {
            YearMonth period = periodFor(config, today);
            if (period == null) continue;
            generateQuietly(config, period);
        }
    }

    /**
     * Which period this hospital is due, or null when it is not due today.
     *
     * <p>A hospital with no GSTIN is skipped entirely: every return it produced would fail
     * validation on the same missing field, and a nightly job manufacturing broken drafts
     * is noise that trains people to ignore the error list.
     */
    private YearMonth periodFor(GstConfiguration config, LocalDate today) {
        if (config.getGstin() == null || config.getGstin().isBlank()) return null;

        GstSyncFrequency frequency = config.getAutoSyncFrequency();
        if (frequency == null || frequency == GstSyncFrequency.MANUAL) return null;

        if (frequency == GstSyncFrequency.DAILY) {
            return YearMonth.from(today);
        }
        // MONTHLY: the month that just closed, built once on the 1st.
        return today.getDayOfMonth() == 1 ? YearMonth.from(today).minusMonths(1) : null;
    }

    /**
     * Runs one hospital's generation in its own transaction and context.
     *
     * <p>Each hospital is isolated deliberately: one tenant's bad data must not abort the
     * run for every tenant after it in the list. The tenant context is set explicitly
     * because a scheduled thread carries no authenticated user to derive it from, and
     * cleared in a finally block so a pooled thread cannot leak one hospital's scope into
     * the next.
     *
     * <p>No {@code @Transactional} here on purpose. This is called from {@link #run()} on
     * {@code this}, so the proxy would be bypassed and the annotation would silently do
     * nothing. The transaction boundary that matters is on
     * {@code GstFilingService.generate}, which <em>is</em> reached through a proxy.
     */
    private void generateQuietly(GstConfiguration config, YearMonth period) {
        try {
            TenantContext.set(config.getTenantId());
            BranchContext.clear();
            var record = filingService.generate(GstReturnType.GSTR1, period, true);
            log.info("event=gst.autosync.generated tenant={} period={} status={} lines={}",
                config.getTenantId(), period, record.getFilingStatus(), record.getLineItemCount());
        } catch (RuntimeException e) {
            log.error("event=gst.autosync.failed tenant={} period={} error_type={}",
                config.getTenantId(), period, e.getClass().getSimpleName());
        } finally {
            TenantContext.clear();
            BranchContext.clear();
        }
    }
}
