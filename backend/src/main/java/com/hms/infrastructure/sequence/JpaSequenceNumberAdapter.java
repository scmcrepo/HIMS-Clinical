package com.hms.infrastructure.sequence;
import com.hms.domain.billing.model.DocumentType;
import com.hms.domain.shared.port.out.SequenceNumberPort;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.infrastructure.tenant.TenantContext;
import com.hms.infrastructure.tenant.BranchContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Component @RequiredArgsConstructor
public class JpaSequenceNumberAdapter implements SequenceNumberPort {
    private final SequenceGeneratorJpaRepository repo;
    /**
     * Called within an existing transaction.
     * The @Lock(PESSIMISTIC_WRITE) on the repository query ensures
     * SELECT FOR UPDATE — no two threads can generate the same number.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public String generateNext(DocumentType documentType) {
        UUID tenantId = TenantContext.require();
        UUID branchId = BranchContext.get();
        if (branchId == null) {
            throw new IllegalStateException(
                "No branch in context. Cannot generate sequence number for branch-scoped document: " + documentType);
        }

        Optional<SequenceGeneratorEntity> active = repo.findActiveByDocumentTypeTenantAndBranchForUpdate(documentType, tenantId, branchId);
        if (active.isPresent()) {
            return active.get().formatAndIncrement();
        }

        // If not active, check if any exists at all to provide specific feedback
        List<SequenceGeneratorEntity> all = repo.findAllByDocumentTypeTenantAndBranch(documentType, tenantId, branchId);
        if (all.isEmpty()) {
            throw new BusinessRuleViolationException(
                "No prefix configured for " + documentType + ". Please create one in Admin > Prefixes.");
        } else {
            throw new BusinessRuleViolationException(
                "The prefix for " + documentType + " is currently deactivated. Please activate it in Admin > Prefixes.");
        }
    }
}
