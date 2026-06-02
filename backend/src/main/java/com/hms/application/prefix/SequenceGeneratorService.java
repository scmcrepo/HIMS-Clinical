package com.hms.application.prefix;

import com.hms.api.prefix.request.CreateSequenceGeneratorRequest;
import com.hms.api.prefix.request.UpdateSequenceGeneratorRequest;
import com.hms.api.prefix.response.SequenceGeneratorResponse;
import com.hms.domain.billing.model.DocumentType;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.sequence.SequenceGeneratorEntity;
import com.hms.infrastructure.sequence.SequenceGeneratorJpaRepository;
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

    /**
     * Creates a new sequence generator for a document type.
     * If an active generator already exists for the type: deactivates it first
     * (replaces it — same pattern as legacy PrefixService.createPrefix()).
     * The new generator starts inactive; call activate() to enable it.
     */
    @Transactional
    public SequenceGeneratorResponse create(CreateSequenceGeneratorRequest req) {
        // Deactivate any existing active generator for this document type
        repo.findActiveByDocumentTypeForUpdate(req.documentType())
            .ifPresent(existing -> {
                existing.deactivate();
                repo.save(existing);
            });

        if (req.prefixString() != null && !req.prefixString().isBlank()) {
            repo.findByPrefixStringIgnoreCase(req.prefixString())
                .filter(SequenceGeneratorEntity::isActivated)
                .ifPresent(e -> {
                    throw new BusinessRuleViolationException("Prefix string '" + req.prefixString() + "' is already in use by document type " + e.getDocumentType());
                });
        }

        SequenceGeneratorEntity entity = new SequenceGeneratorEntity();
        entity.setPrefixString(req.prefixString());
        entity.setDocumentType(req.documentType());
        entity.setResetPolicy(req.resetPolicy());
        entity.setActivated(false);
        entity.setCurrentCounter(1L);
        entity.setCreatedAt(Instant.now());

        return toResponse(repo.save(entity));
    }

    @Transactional
    public SequenceGeneratorResponse update(UUID id, UpdateSequenceGeneratorRequest req) {
        SequenceGeneratorEntity entity = findOrThrow(id);

        if (req.prefixString() != null && !req.prefixString().isBlank()) {
            repo.findByPrefixStringIgnoreCase(req.prefixString())
                .filter(e -> e.isActivated() && !e.getId().equals(id))
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

        // Deactivate the currently active generator for this type (if any, excluding this one)
        repo.findActiveByDocumentTypeForUpdate(entity.getDocumentType())
            .filter(e -> !e.getId().equals(generatorId))
            .ifPresent(existing -> {
                existing.deactivate();
                repo.save(existing);
            });

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
        return repo.findAll().stream().map(this::toResponse).toList();
    }

    /**
     * Returns one entry per DocumentType — if none configured, returns an
     * unconfigured placeholder (matching legacy getPrefixByEnum() pattern).
     * The UI uses absent/inactive entries to show "not configured" badges.
     */
    @Transactional(readOnly = true)
    public List<SequenceGeneratorResponse> getSummaryByDocumentType() {
        List<SequenceGeneratorEntity> all = repo.findAll();

        return Arrays.stream(DocumentType.values()).map(docType -> {
            return all.stream()
                .filter(e -> e.getDocumentType() == docType)
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
        return repo.findAllByDocumentType(documentType).stream()
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
