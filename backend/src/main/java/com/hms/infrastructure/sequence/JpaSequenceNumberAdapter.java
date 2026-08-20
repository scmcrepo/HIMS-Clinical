package com.hms.infrastructure.sequence;
import com.hms.domain.billing.model.DocumentType;
import com.hms.domain.shared.port.out.SequenceNumberPort;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.infrastructure.persistence.tenant.BranchEntity;
import com.hms.infrastructure.persistence.tenant.BranchJpaRepository;
import com.hms.infrastructure.tenant.TenantContext;
import com.hms.infrastructure.tenant.BranchContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JpaSequenceNumberAdapter implements SequenceNumberPort {

    private final SequenceGeneratorJpaRepository repo;
    private final BranchJpaRepository branchRepo;

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
            branchId = branchRepo.findByTenantIdAndIsDefaultTrue(tenantId)
                .map(BranchEntity::getId)
                .orElseGet(() -> branchRepo.findAllByTenantIdAndStatus(tenantId, (short) 1)
                    .stream().findFirst().map(BranchEntity::getId).orElse(null));
        }

        if (branchId != null) {
            Optional<SequenceGeneratorEntity> active = repo.findActiveByDocumentTypeTenantAndBranchForUpdate(documentType, tenantId, branchId);
            if (active.isPresent()) {
                return active.get().formatAndIncrement();
            }
        }

        // Fallback: check if tenant's default branch or any active branch has a configured sequence generator
        Optional<BranchEntity> defaultBranch = branchRepo.findByTenantIdAndIsDefaultTrue(tenantId);
        if (defaultBranch.isPresent() && !defaultBranch.get().getId().equals(branchId)) {
            Optional<SequenceGeneratorEntity> defaultActive = repo.findActiveByDocumentTypeTenantAndBranchForUpdate(
                documentType, tenantId, defaultBranch.get().getId());
            if (defaultActive.isPresent()) {
                return defaultActive.get().formatAndIncrement();
            }
        }

        // Check if any branch in the tenant has this active prefix
        List<BranchEntity> allBranches = branchRepo.findAllByTenantIdAndStatus(tenantId, (short) 1);
        for (BranchEntity b : allBranches) {
            if (!b.getId().equals(branchId)) {
                Optional<SequenceGeneratorEntity> otherActive = repo.findActiveByDocumentTypeTenantAndBranchForUpdate(
                    documentType, tenantId, b.getId());
                if (otherActive.isPresent()) {
                    return otherActive.get().formatAndIncrement();
                }
            }
        }

        // If not active anywhere, check if any exists at all to provide specific feedback
        if (branchId != null) {
            List<SequenceGeneratorEntity> all = repo.findAllByDocumentTypeTenantAndBranch(documentType, tenantId, branchId);
            if (!all.isEmpty()) {
                throw new BusinessRuleViolationException(
                    "The prefix for " + documentType + " is currently deactivated. Please activate it in Admin > Prefixes.");
            }
        }

        throw new BusinessRuleViolationException(
            "No prefix configured for " + documentType + ". Please create one in Admin > Prefixes.");
    }
}
