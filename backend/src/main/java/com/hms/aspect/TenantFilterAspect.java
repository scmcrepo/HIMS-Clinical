package com.hms.aspect;

import com.hms.infrastructure.tenant.BranchContext;
import com.hms.infrastructure.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Aspect
@Component
@Slf4j
public class TenantFilterAspect {

    @PersistenceContext
    private EntityManager entityManager;

    @Before("execution(* com.hms.infrastructure.persistence..*.*(..)) || execution(* com.hms.application..*.*(..)) || execution(* com.hms.api..*.*(..))")
    public void enableTenantFilters() {
        try {
            boolean txActive = org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive();
            Session session = entityManager.unwrap(Session.class);
            if (session != null && session.isOpen()) {
                UUID tenantId = TenantContext.get();
                UUID branchId = BranchContext.get();
                log.info("TenantFilterAspect: TX Active = {}, enabling filters for tenant {} and branch {} on session {}", txActive, tenantId, branchId, session);
                if (tenantId != null) {
                    session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
                } else {
                    session.disableFilter("tenantFilter");
                }
                if (branchId != null) {
                    session.enableFilter("branchFilter").setParameter("branchId", branchId);
                } else {
                    session.disableFilter("branchFilter");
                }
            } else {
                log.warn("TenantFilterAspect: session is null or closed");
            }
        } catch (Exception e) {
            log.error("TenantFilterAspect: error enabling/disabling filters", e);
        }
    }
}
