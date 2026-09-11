package com.hms.application.gst;

import com.hms.domain.gst.model.*;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.infrastructure.persistence.gst.GstConfigurationJpaRepository;
import com.hms.infrastructure.tenant.TenantContext;
import com.hms.security.HmsUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Reads and writes one hospital's GST configuration (A1, A7).
 *
 * <p>Two rules are enforced here rather than at the controller, because both are about
 * what the value <em>means</em> rather than who is calling:
 *
 * <ul>
 *   <li><b>The integration switch is a platform decision (D3).</b> A hospital admin
 *       maintains their own GSTIN and credentials, but only a platform super-admin may
 *       turn the integration on or off for a hospital. A feature key cannot express this
 *       — SUPERADMIN bypasses feature checks entirely, so granting the key to fewer
 *       roles would restrict the wrong people.</li>
 *   <li><b>Switching to production is not a silent toggle (A7).</b> It requires an
 *       explicit confirmation flag, so a mis-click on a dropdown cannot start pointing
 *       real filings at the live GST network.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GstConfigurationService {

    private final GstConfigurationJpaRepository configRepo;

    @Transactional(readOnly = true)
    public GstConfiguration getForCurrentTenant() {
        UUID tenantId = TenantContext.require();
        return configRepo.findByTenantId(tenantId).orElseGet(() -> {
            GstConfiguration blank = new GstConfiguration();
            blank.setTenantId(tenantId);
            return blank;
        });
    }

    /**
     * Saves the hospital's own settings. Never changes {@code integrationEnabled} —
     * see {@link #setIntegrationEnabled}.
     */
    @Transactional
    public GstConfiguration save(GstConfiguration req, boolean confirmProduction) {
        UUID tenantId = TenantContext.require();
        GstConfiguration existing = configRepo.findByTenantId(tenantId)
            .orElseGet(() -> {
                GstConfiguration created = new GstConfiguration();
                created.setTenantId(tenantId);
                return created;
            });

        String gstin = Gstin.normalise(req.getGstin());
        Gstin.requireValid(gstin);
        existing.setGstin(gstin);
        // Recomputed on every save so it can never disagree with the GSTIN above it.
        existing.setStateCode(Gstin.stateCode(gstin));
        existing.setLegalName(req.getLegalName());

        // A blank credential means "leave it alone", not "clear it". The config screen
        // never sends these back — it cannot read them — so treating blank as a delete
        // would wipe the hospital's credentials every time they edited their legal name.
        if (req.getApiKey() != null && !req.getApiKey().isBlank()) {
            existing.setApiKey(req.getApiKey().trim());
        }
        if (req.getApiSecret() != null && !req.getApiSecret().isBlank()) {
            existing.setApiSecret(req.getApiSecret().trim());
        }

        existing.setSandboxBaseUrl(req.getSandboxBaseUrl());
        existing.setProductionBaseUrl(req.getProductionBaseUrl());
        existing.setAutoSyncFrequency(req.getAutoSyncFrequency() != null
            ? req.getAutoSyncFrequency() : GstSyncFrequency.MANUAL);

        GstEnvironment target = req.getActiveEnvironment() != null
            ? req.getActiveEnvironment() : GstEnvironment.SANDBOX;
        if (target == GstEnvironment.PRODUCTION
            && existing.getActiveEnvironment() != GstEnvironment.PRODUCTION
            && !confirmProduction) {
            throw new BusinessRuleViolationException(
                "Switching to the production GST environment needs explicit confirmation");
        }
        existing.setActiveEnvironment(target);

        GstConfiguration saved = configRepo.save(existing);
        log.info("GST configuration saved for tenant {} — environment {}, GSTIN state {}",
            tenantId, saved.getActiveEnvironment(), saved.getStateCode());
        return saved;
    }

    /** D3: platform super-admin only, per hospital. */
    @Transactional
    public GstConfiguration setIntegrationEnabled(boolean enabled) {
        requireSuperAdmin();
        UUID tenantId = TenantContext.require();
        GstConfiguration existing = configRepo.findByTenantId(tenantId)
            .orElseGet(() -> {
                GstConfiguration created = new GstConfiguration();
                created.setTenantId(tenantId);
                return created;
            });
        existing.setIntegrationEnabled(enabled);
        GstConfiguration saved = configRepo.save(existing);
        log.info("GST integration {} for tenant {} by super-admin",
            enabled ? "ENABLED" : "DISABLED", tenantId);
        return saved;
    }

    /** The state code every supply falls back to when a bill carries no place of supply. */
    @Transactional(readOnly = true)
    public String homeStateCode() {
        return configRepo.findByTenantId(TenantContext.require())
            .map(GstConfiguration::getStateCode)
            .orElse(null);
    }

    private void requireSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth != null ? auth.getPrincipal() : null;
        boolean superAdmin = principal instanceof HmsUserDetails user && user.isSuperAdmin();
        if (!superAdmin) {
            throw new BusinessRuleViolationException(
                "Only a platform super-admin can enable or disable GST integration for a hospital");
        }
    }
}
