package com.hms.application.portal;

import com.hms.api.portal.response.PortalResponses;
import com.hms.domain.shared.model.EntityStatus;
import com.hms.infrastructure.persistence.tenant.BranchEntity;
import com.hms.infrastructure.persistence.tenant.BranchJpaRepository;
import com.hms.infrastructure.persistence.tenant.TenantEntity;
import com.hms.infrastructure.persistence.tenant.TenantJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The hospital directory shown to a patient who is registering for the first
 * time.
 *
 * <p>Reads {@code tenants} and {@code branches}, which are global tables — they
 * deliberately do not extend {@code AuditableEntity} and carry no tenant filter,
 * because a tenant cannot be scoped to itself. That makes this the one portal
 * read that is legitimately cross-tenant without needing
 * {@link PortalTenantScope}, and it is safe because every field returned is
 * already public: the hospital's name, address and switchboard number.
 *
 * <p>Nothing patient-related may ever be added to these responses. If it is,
 * this stops being a directory and becomes a cross-tenant data path.
 */
@Service
@RequiredArgsConstructor
public class PortalDirectoryService {

    private final TenantJpaRepository tenantRepo;
    private final BranchJpaRepository branchRepo;

    @Transactional(readOnly = true)
    public List<PortalResponses.HospitalCandidate> listActiveHospitals() {
        return tenantRepo.findAll().stream()
            .filter(t -> t.getStatus() == EntityStatus.ACTIVE.getOrdinalValue())
            .map(t -> new PortalResponses.HospitalCandidate(
                t.getId(), t.getName(), t.getAddress(), t.getContactNumber(), null,
                // Empty rather than null: a registering patient has no records
                // anywhere by definition, and an empty list says that plainly
                // where a null would make the client guess.
                List.of(),
                listActiveBranches(t.getId())))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<PortalResponses.BranchSummary> listActiveBranches(UUID tenantId) {
        return branchRepo
            .findAllByTenantIdAndStatus(tenantId, (short) EntityStatus.ACTIVE.getOrdinalValue())
            .stream()
            .map(PortalDirectoryService::toSummary)
            .toList();
    }

    private static PortalResponses.BranchSummary toSummary(BranchEntity b) {
        return new PortalResponses.BranchSummary(
            b.getId(), b.getName(), b.getCode(), b.getAddress(), b.getContactNumber(),
            b.isDefault(), true);
    }
}
