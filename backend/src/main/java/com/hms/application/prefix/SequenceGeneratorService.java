package com.hms.application.prefix;

import com.hms.api.prefix.request.CreateSequenceGeneratorRequest;
import com.hms.api.prefix.request.UpdateSequenceGeneratorRequest;
import com.hms.api.prefix.response.SequenceGeneratorResponse;
import com.hms.domain.billing.model.DocumentType;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.sequence.SequenceGeneratorEntity;
import com.hms.infrastructure.sequence.SequenceGeneratorJpaRepository;
import com.hms.infrastructure.tenant.TenantContext;
import com.hms.infrastructure.tenant.BranchContext;
import com.hms.infrastructure.persistence.tenant.BranchJpaRepository;
import com.hms.infrastructure.persistence.tenant.BranchEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SequenceGeneratorService {

    private final SequenceGeneratorJpaRepository repo;
    private final BranchJpaRepository branchRepo;

    /**
     * Creates a new sequence generator for a document type.
     * If an active generator already exists for the type: deactivates it first
     * (replaces it — same pattern as legacy PrefixService.createPrefix()).
     * The new generator starts inactive; call activate() to enable it.
     */
    @Transactional
    public SequenceGeneratorResponse create(CreateSequenceGeneratorRequest req) {
        UUID tenantId = TenantContext.require();
        UUID branchId = BranchContext.require();

        SequenceGeneratorEntity entity = repo.findActiveByDocumentTypeTenantAndBranchForUpdate(req.documentType(), tenantId, branchId)
            .orElseGet(() -> {
                SequenceGeneratorEntity newEntity = new SequenceGeneratorEntity();
                newEntity.setDocumentType(req.documentType());
                newEntity.setCurrentCounter(1L);
                newEntity.setCreatedAt(Instant.now());
                newEntity.setTenantId(tenantId);
                newEntity.setBranchId(branchId);
                newEntity.activate();
                return newEntity;
            });

        if (req.prefixString() != null && !req.prefixString().isBlank()) {
            repo.findConflictingPrefixes(req.prefixString(), tenantId, branchId)
                .stream()
                .filter(e -> e.isActivated() && (entity.getId() == null || !entity.getId().equals(e.getId())))
                .findFirst()
                .ifPresent(e -> {
                    throw new BusinessRuleViolationException("Prefix string '" + req.prefixString() + "' is already in use by document type " + e.getDocumentType());
                });
        }

        entity.setPrefixString(req.prefixString());
        entity.setResetPolicy(req.resetPolicy());

        return toResponse(repo.save(entity));
    }

    @Transactional
    public SequenceGeneratorResponse update(UUID id, UpdateSequenceGeneratorRequest req) {
        SequenceGeneratorEntity entity = findOrThrow(id);
        UUID tenantId = entity.getTenantId();
        UUID branchId = entity.getBranchId();

        if (req.prefixString() != null && !req.prefixString().isBlank()) {
            repo.findConflictingPrefixes(req.prefixString(), tenantId, branchId)
                .stream()
                .filter(e -> e.isActivated() && !e.getId().equals(id))
                .findFirst()
                .ifPresent(e -> {
                    throw new BusinessRuleViolationException("Prefix string '" + req.prefixString() + "' is already in use by document type " + e.getDocumentType());
                });
        }

        entity.setPrefixString(req.prefixString());
        entity.setDocumentType(req.documentType());
        entity.setResetPolicy(req.resetPolicy());
        
        return toResponse(repo.save(entity));
    }

    /**
     * Activates a sequence generator.
     * Only one generator per DocumentType can be active at a time.
     */
    @Transactional
    public SequenceGeneratorResponse activate(UUID generatorId) {
        SequenceGeneratorEntity entity = findOrThrow(generatorId);

        // Deactivate the currently active generator for this type in this scope (if any, excluding this one)
        repo.findActiveByDocumentTypeTenantAndBranchForUpdate(entity.getDocumentType(), entity.getTenantId(), entity.getBranchId())
            .filter(e -> !e.getId().equals(generatorId))
            .ifPresent(existing -> {
                existing.deactivate();
                repo.save(existing);
            });

        if (entity.getPrefixString() != null && !entity.getPrefixString().isBlank()) {
            repo.findConflictingPrefixes(entity.getPrefixString(), entity.getTenantId(), entity.getBranchId())
                .stream()
                .filter(e -> e.isActivated() && !e.getId().equals(generatorId))
                .findFirst()
                .ifPresent(e -> {
                    throw new BusinessRuleViolationException("Prefix string '" + entity.getPrefixString() + "' is already in use by document type " + e.getDocumentType());
                });
        }

        entity.activate();
        return toResponse(repo.save(entity));
    }

    @Transactional
    public SequenceGeneratorResponse deactivate(UUID generatorId) {
        SequenceGeneratorEntity entity = findOrThrow(generatorId);
        entity.deactivate();
        return toResponse(repo.save(entity));
    }

    @Transactional(readOnly = true)
    public List<SequenceGeneratorResponse> getAll() {
        UUID tenantId = TenantContext.require();
        UUID branchId = BranchContext.get();
        if (branchId == null) {
            branchId = branchRepo.findByTenantIdAndIsDefaultTrue(tenantId)
                .map(BranchEntity::getId)
                .orElse(null);
        }
        
        final UUID finalBranchId = branchId;
        return repo.findAllByTenantId(tenantId).stream()
            .filter(e -> finalBranchId != null && finalBranchId.equals(e.getBranchId()))
            .map(this::toResponse).toList();
    }

    /**
     * Returns one entry per DocumentType — if none configured, returns an
     * unconfigured placeholder (matching legacy getPrefixByEnum() pattern).
     * The UI uses absent/inactive entries to show "not configured" badges.
     */
    @Transactional(readOnly = true)
    public List<SequenceGeneratorResponse> getSummaryByDocumentType() {
        UUID tenantId = TenantContext.require();
        UUID branchId = BranchContext.get();
        if (branchId == null) {
            branchId = branchRepo.findByTenantIdAndIsDefaultTrue(tenantId)
                .map(BranchEntity::getId)
                .orElse(null);
        }

        List<SequenceGeneratorEntity> all = repo.findAllByTenantId(tenantId);

        final UUID finalBranchId = branchId;
        return Arrays.stream(DocumentType.values()).map(docType -> {
            return all.stream()
                .filter(e -> e.getDocumentType() == docType)
                .filter(e -> finalBranchId != null && finalBranchId.equals(e.getBranchId()))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .findFirst()
                .map(this::toResponse)
                .orElse(new SequenceGeneratorResponse(
                    null, null, docType, null, false, 0L, null, null, null
                ));
        }).toList();
    }

    @Transactional(readOnly = true)
    public SequenceGeneratorResponse getById(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<SequenceGeneratorResponse> getHistory(DocumentType documentType) {
        UUID tenantId = TenantContext.require();
        UUID branchId = BranchContext.get();
        if (branchId == null) {
            branchId = branchRepo.findByTenantIdAndIsDefaultTrue(tenantId)
                .map(BranchEntity::getId)
                .orElse(null);
        }

        return repo.findAllByDocumentTypeTenantAndBranch(documentType, tenantId, branchId).stream()
            .map(this::toResponse).toList();
    }

    private SequenceGeneratorEntity findOrThrow(UUID id) {
        return repo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("SequenceGenerator", id));
    }

    private SequenceGeneratorResponse toResponse(SequenceGeneratorEntity e) {
        return new SequenceGeneratorResponse(
            e.getId(), e.getPrefixString(), e.getDocumentType(), e.getResetPolicy(),
            e.isActivated(), e.getCurrentCounter(), e.getCurrentFiscalYear(),
            e.getActivatedAt(), e.getDeactivatedAt()
        );
    }
}
