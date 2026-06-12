package com.hms.application.casesheet;

import com.hms.api.casesheet.request.*;
import com.hms.api.casesheet.response.*;
import com.hms.domain.casesheet.model.*;
import com.hms.domain.encounter.model.ClinicalEncounter;
import com.hms.domain.shared.model.EntityStatus;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.casesheet.DischargeSummaryRecordJpaRepository;
import com.hms.infrastructure.persistence.casesheet.DischargeSummaryTemplateJpaRepository;
import com.hms.infrastructure.persistence.encounter.ClinicalEncounterJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class DischargeSummaryService {

    private final DischargeSummaryTemplateJpaRepository templateRepo;
    private final DischargeSummaryRecordJpaRepository   recordRepo;
    private final ClinicalEncounterJpaRepository        encounterRepo;

    // ─── Template Operations ──────────────────────────────────────────────────

    public List<DischargeTemplateSummary> listTemplates(String specialization, EntityStatus status) {
        List<EntityStatus> statuses = (status != null) 
                ? List.of(status) 
                : List.of(EntityStatus.ACTIVE, EntityStatus.INACTIVE);

        List<DischargeSummaryTemplate> templates;
        if (specialization != null && !specialization.isBlank()) {
            templates = templateRepo.findByStatusInAndSpecializationIgnoreCaseOrderByNameAsc(statuses, specialization);
        } else {
            templates = templateRepo.findByStatusInOrderBySpecializationAscNameAsc(statuses);
        }
        return templates.stream().map(this::toSummary).collect(Collectors.toList());
    }

    public DischargeTemplateDetail getTemplate(UUID id) {
        return toDetail(fetchTemplate(id));
    }

    @Transactional
    public DischargeTemplateDetail createTemplate(CreateDischargeTemplateRequest req) {
        if (templateRepo.existsByNameIgnoreCaseAndSpecializationIgnoreCase(req.name(), req.specialization())) {
            throw new BusinessRuleViolationException(
                    "Discharge template '" + req.name() + "' already exists for " + req.specialization());
        }
        DischargeSummaryTemplate t = new DischargeSummaryTemplate();
        t.setName(req.name());
        t.setSpecialization(req.specialization().toUpperCase());
        t.setDescription(req.description());
        t.setDefaultTemplate(req.defaultTemplate());
        if (req.fields() != null) req.fields().forEach(f -> t.getFields().add(buildField(f, t)));
        if (req.defaultTemplate()) demoteOtherDefaults(req.specialization(), null);
        return toDetail(templateRepo.save(t));
    }

    @Transactional
    public DischargeTemplateDetail updateTemplate(UUID id, UpdateDischargeTemplateRequest req) {
        DischargeSummaryTemplate t = fetchTemplate(id);
        if (req.name()           != null) t.setName(req.name());
        if (req.specialization() != null) t.setSpecialization(req.specialization().toUpperCase());
        if (req.description()    != null) t.setDescription(req.description());
        if (req.defaultTemplate() != null) {
            t.setDefaultTemplate(req.defaultTemplate());
            if (req.defaultTemplate()) demoteOtherDefaults(t.getSpecialization(), id);
        }
        if (req.status()          != null) t.setStatus(req.status());
        if (req.fields() != null) {
            t.getFields().clear();
            templateRepo.saveAndFlush(t);
            req.fields().forEach(f -> t.getFields().add(buildField(f, t)));
        }
        return toDetail(templateRepo.saveAndFlush(t));
    }

    @Transactional
    public void deleteTemplate(UUID id) {
        DischargeSummaryTemplate t = fetchTemplate(id);
        t.softDelete();
        templateRepo.save(t);
    }

    // ─── Record Operations ────────────────────────────────────────────────────

    public List<DischargeRecordResponse> getRecordsByEncounter(UUID encounterId) {
        return recordRepo.findByEncounterIdAndStatus(encounterId, EntityStatus.ACTIVE)
                .stream().map(this::toRecordResponse).collect(Collectors.toList());
    }

    @Transactional
    public DischargeRecordResponse saveRecord(UUID encounterId, SaveRecordRequest req) {
        ClinicalEncounter encounter = encounterRepo.findById(encounterId)
                .orElseThrow(() -> new ResourceNotFoundException("Encounter not found: " + encounterId));

        UUID templateId = req.templateId();
        if (templateId == null) {
            throw new BusinessRuleViolationException("Template ID is required to save a discharge summary record.");
        }

        Optional<DischargeSummaryRecord> existing = recordRepo.findByEncounterIdAndTemplateIdAndStatus(
                encounterId, templateId, EntityStatus.ACTIVE);

        DischargeSummaryRecord record;
        if (existing.isPresent()) {
            record = existing.get();
            record.mergeData(req.data());
        } else {
            // Check if there is already a record for this encounter with a DIFFERENT template, 
            // since only one discharge summary record should exist for a patient's encounter.
            List<DischargeSummaryRecord> allRecords = recordRepo.findByEncounterIdAndStatus(encounterId, EntityStatus.ACTIVE);
            if (!allRecords.isEmpty()) {
                throw new BusinessRuleViolationException("A discharge summary record already exists for this encounter. Selected template cannot be changed.");
            }

            DischargeSummaryTemplate template = fetchTemplate(templateId);
            record = new DischargeSummaryRecord();
            record.setEncounterId(encounterId);
            record.setTemplate(template);
            record.setData(req.data() != null ? req.data() : new HashMap<>());
        }
        record.setRecordedAt(Instant.now());
        DischargeSummaryRecord saved = recordRepo.save(record);
        return toRecordResponse(saved);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private DischargeSummaryTemplate fetchTemplate(UUID id) {
        return templateRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DischargeSummaryTemplate not found: " + id));
    }

    private void demoteOtherDefaults(String specialization, UUID excludeId) {
        templateRepo.findByStatusAndSpecializationIgnoreCaseOrderByNameAsc(
                EntityStatus.ACTIVE, specialization).stream()
                .filter(t -> t.isDefaultTemplate() && (excludeId == null || !t.getId().equals(excludeId)))
                .forEach(t -> { t.setDefaultTemplate(false); templateRepo.save(t); });
    }

    private DischargeSummaryTemplateField buildField(FieldRequest req, DischargeSummaryTemplate template) {
        DischargeSummaryTemplateField f = new DischargeSummaryTemplateField();
        f.setTemplate(template);
        f.setFieldKey(req.fieldKey());
        f.setLabel(req.label());
        f.setFieldType(req.fieldType());
        f.setSection(req.section());
        f.setDisplayOrder(req.displayOrder());
        f.setRequired(req.required());
        f.setPlaceholder(req.placeholder());
        f.setHelpText(req.helpText());
        f.setOptions(req.options());
        f.setValidation(req.validation());
        f.setDefaultValue(req.defaultValue());
        f.setVisible(req.visible());
        return f;
    }

    // ─── Mappers ──────────────────────────────────────────────────────────────

    private DischargeTemplateSummary toSummary(DischargeSummaryTemplate t) {
        return new DischargeTemplateSummary(
                t.getId(), t.getName(), t.getSpecialization(),
                t.getDescription(), t.isDefaultTemplate(), t.getFields().size(), t.getStatus());
    }

    private DischargeTemplateDetail toDetail(DischargeSummaryTemplate t) {
        List<FieldResponse> fields = t.getFields().stream()
                .filter(f -> f.getStatus() == EntityStatus.ACTIVE && f.isVisible())
                .map(f -> new FieldResponse(f.getId(), f.getFieldKey(), f.getLabel(), f.getFieldType(),
                        f.getSection(), f.getDisplayOrder(), f.isRequired(), f.getPlaceholder(),
                        f.getHelpText(), f.getOptions(), f.getValidation(), f.getDefaultValue(), f.isVisible()))
                .collect(Collectors.toList());
        return new DischargeTemplateDetail(t.getId(), t.getName(), t.getSpecialization(),
                t.getDescription(), t.isDefaultTemplate(), fields,
                t.getCreatedAt(), t.getModifiedAt(), t.getStatus());
    }

    private DischargeRecordResponse toRecordResponse(DischargeSummaryRecord r) {
        return new DischargeRecordResponse(r.getId(), r.getEncounterId(),
                toSummary(r.getTemplate()), r.getData(), r.getRecordedBy(),
                r.getRecordedAt(), r.getModifiedAt());
    }
}
