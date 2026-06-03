package com.hms.application.casesheet;

import com.hms.api.casesheet.request.*;
import com.hms.api.casesheet.response.*;
import com.hms.domain.casesheet.model.*;
import com.hms.domain.encounter.model.ClinicalEncounter;
import com.hms.domain.shared.model.EntityStatus;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.casesheet.CaseSheetRecordJpaRepository;
import com.hms.infrastructure.persistence.casesheet.CaseSheetTemplateJpaRepository;
import com.hms.infrastructure.persistence.consultant.ConsultantJpaRepository;
import com.hms.infrastructure.persistence.encounter.ClinicalEncounterJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core application service for the OP/IP case sheet module.
 *
 * Responsibilities:
 *   1. Template CRUD — admins configure form layouts per specialization + visit type
 *   2. Record save   — doctors fill and save encounter case sheets (supports partial/auto-save)
 *   3. Record fetch  — retrieve filled records for viewing or re-editing
 *
 * Integration point:
 *   On first save of a CaseSheetRecord the encounter's casesheetRecordedAt is stamped
 *   and status advances to CASESHEET_RECORDED — exactly what gates the "Mark Consulted"
 *   button in the OP workflow.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class CaseSheetService {

    private final CaseSheetTemplateJpaRepository templateRepo;
    private final CaseSheetRecordJpaRepository   recordRepo;
    private final ClinicalEncounterJpaRepository encounterRepo;
    private final ConsultantJpaRepository        consultantRepo;
    private final com.hms.infrastructure.persistence.department.DepartmentJpaRepository departmentRepo;
    private final com.hms.infrastructure.persistence.department.DepartmentTemplateJpaRepository departmentTemplateRepo;

    // ─── Template Operations ──────────────────────────────────────────────────

    public List<CaseSheetTemplateSummary> listTemplates(String specialization, CaseSheetVisitType visitType) {
        List<CaseSheetTemplate> templates;
        if (specialization != null && visitType != null) {
            templates = templateRepo.findByStatusAndSpecializationIgnoreCaseAndVisitTypeOrderByNameAsc(
                    EntityStatus.ACTIVE, specialization, visitType);
        } else if (specialization != null) {
            templates = templateRepo.findByStatusAndSpecializationIgnoreCaseOrderByNameAsc(
                    EntityStatus.ACTIVE, specialization);
        } else if (visitType != null) {
            templates = templateRepo.findByStatusAndVisitTypeOrderByNameAsc(EntityStatus.ACTIVE, visitType);
        } else {
            templates = templateRepo.findByStatusOrderBySpecializationAscNameAsc(EntityStatus.ACTIVE);
        }
        return templates.stream().map(this::toSummary).collect(Collectors.toList());
    }

    public CaseSheetTemplateDetail getTemplate(UUID id) {
        return toDetail(fetchTemplate(id));
    }

    public CaseSheetTemplateDetail getDefaultTemplate(String specialization, CaseSheetVisitType visitType) {
        return templateRepo.findDefault(EntityStatus.ACTIVE, specialization, visitType)
                .map(this::toDetail)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No default case sheet template for specialization=" + specialization + " visitType=" + visitType));
    }

    public CaseSheetTemplate resolveTemplateForEncounter(ClinicalEncounter encounter) {
        UUID providerId = encounter.getPrimaryProviderId();
        CaseSheetVisitType vt = encounter.isInpatient() ? CaseSheetVisitType.IP : CaseSheetVisitType.OP;
        
        if (providerId != null) {
            var consultantOpt = consultantRepo.findById(providerId);
            if (consultantOpt.isPresent()) {
                UUID deptId = consultantOpt.get().getDepartmentId();
                if (deptId != null) {
                    List<com.hms.domain.shared.model.DepartmentTemplate> dtList = departmentTemplateRepo.findByDepartmentId(deptId);
                    if (dtList != null && !dtList.isEmpty()) {
                         List<CaseSheetTemplate> templates = dtList.stream()
                                 .map(com.hms.domain.shared.model.DepartmentTemplate::getTemplate)
                                 .filter(t -> t != null && t.getStatus() == EntityStatus.ACTIVE && t.getVisitType() == vt)
                                 .collect(Collectors.toList());
                         if (!templates.isEmpty()) {
                             return templates.stream()
                                     .filter(CaseSheetTemplate::isDefaultTemplate)
                                     .findFirst()
                                     .orElse(templates.get(0));
                         }
                    }
                }
            }
        }
        
        // Fallback to legacy specialization-based resolution
        String spec = resolveSpecialization(providerId);
        return templateRepo.findDefault(EntityStatus.ACTIVE, spec, vt)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No default case sheet template for specialization=" + spec + " visitType=" + vt));
    }

    public CaseSheetTemplateDetail resolveTemplateDetailForEncounter(UUID encounterId) {
        ClinicalEncounter encounter = encounterRepo.findById(encounterId)
                .orElseThrow(() -> new ResourceNotFoundException("Encounter not found: " + encounterId));
        return toDetail(resolveTemplateForEncounter(encounter));
    }

    @Transactional
    public CaseSheetTemplateDetail createTemplate(CreateTemplateRequest req) {
        if (templateRepo.existsByNameIgnoreCaseAndSpecializationIgnoreCaseAndVisitType(
                req.name(), req.specialization(), req.visitType())) {
            throw new BusinessRuleViolationException(
                    "Template '" + req.name() + "' already exists for " + req.specialization() + "/" + req.visitType());
        }
        CaseSheetTemplate t = new CaseSheetTemplate();
        t.setName(req.name());
        t.setSpecialization(req.specialization().toUpperCase());
        t.setVisitType(req.visitType());
        t.setDescription(req.description());
        t.setDefaultTemplate(req.defaultTemplate());
        if (req.fields() != null) req.fields().forEach(f -> t.getFields().add(buildField(f, t)));
        if (req.defaultTemplate()) demoteOtherDefaults(req.specialization(), req.visitType(), null);
        return toDetail(templateRepo.save(t));
    }

    @Transactional
    public CaseSheetTemplateDetail updateTemplate(UUID id, UpdateTemplateRequest req) {
        CaseSheetTemplate t = fetchTemplate(id);
        if (req.name()           != null) t.setName(req.name());
        if (req.specialization() != null) t.setSpecialization(req.specialization().toUpperCase());
        if (req.visitType()      != null) t.setVisitType(req.visitType());
        if (req.description()    != null) t.setDescription(req.description());
        if (req.defaultTemplate() != null) {
            t.setDefaultTemplate(req.defaultTemplate());
            if (req.defaultTemplate()) demoteOtherDefaults(t.getSpecialization(), t.getVisitType(), id);
        }
        if (req.fields() != null) {
            t.getFields().clear();
            templateRepo.saveAndFlush(t);
            req.fields().forEach(f -> t.getFields().add(buildField(f, t)));
        }
        return toDetail(templateRepo.saveAndFlush(t));
    }

    @Transactional
    public void deleteTemplate(UUID id) {
        CaseSheetTemplate t = fetchTemplate(id);
        t.softDelete();
        templateRepo.save(t);
    }

    // ─── Record Operations ────────────────────────────────────────────────────

    public List<CaseSheetRecordResponse> getRecordsByEncounter(UUID encounterId) {
        return recordRepo.findByEncounterIdAndStatus(encounterId, EntityStatus.ACTIVE)
                .stream().map(this::toRecordResponse).collect(Collectors.toList());
    }

    public CaseSheetRecordResponse getRecord(UUID recordId) {
        CaseSheetRecord r = recordRepo.findById(recordId)
                .orElseThrow(() -> new ResourceNotFoundException("CaseSheetRecord not found: " + recordId));
        return toRecordResponse(r);
    }

    /**
     * Upsert: create or partially update the case sheet record for an encounter.
     *
     * Rules:
     * - If templateId is supplied and a record exists for that template → merge data.
     * - If templateId is supplied and no record exists → create new.
     * - If templateId is absent and a record exists for this encounter → merge into first record.
     * - If templateId is absent and no records exist → resolve default template by specialization.
     * - On first save: stamps encounter.casesheetRecordedAt → CASESHEET_RECORDED.
     */
    @Transactional
    public CaseSheetRecordResponse saveRecord(UUID encounterId, SaveRecordRequest req) {
        ClinicalEncounter encounter = encounterRepo.findById(encounterId)
                .orElseThrow(() -> new ResourceNotFoundException("Encounter not found: " + encounterId));

        UUID templateId = req.templateId();
        Optional<CaseSheetRecord> existing;

        if (templateId != null) {
            existing = recordRepo.findByEncounterIdAndTemplateIdAndStatus(
                    encounterId, templateId, EntityStatus.ACTIVE);
        } else {
            List<CaseSheetRecord> all = recordRepo.findByEncounterIdAndStatus(encounterId, EntityStatus.ACTIVE);
            existing   = all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
            templateId = existing.map(r -> r.getTemplate().getId()).orElse(null);
        }

        CaseSheetRecord record;
        if (existing.isPresent()) {
            record = existing.get();
            record.mergeData(req.data());
        } else {
            CaseSheetTemplate template;
            if (templateId != null) {
                template = fetchTemplate(templateId);
            } else {
                template = resolveTemplateForEncounter(encounter);
            }
            record = new CaseSheetRecord();
            record.setEncounterId(encounterId);
            record.setTemplate(template);
            record.setData(req.data() != null ? req.data() : new HashMap<>());
        }
        record.setRecordedAt(Instant.now());
        CaseSheetRecord saved = recordRepo.save(record);

        // Advance encounter status on first save
        if (encounter.getCasesheetRecordedAt() == null) {
            encounter.recordCasesheetTimestamp();
            encounterRepo.save(encounter);
            log.info("Encounter {} status advanced to CASESHEET_RECORDED", encounterId);
        }
        return toRecordResponse(saved);
    }

    @Transactional
    public void deleteRecord(UUID recordId) {
        CaseSheetRecord r = recordRepo.findById(recordId)
                .orElseThrow(() -> new ResourceNotFoundException("CaseSheetRecord not found: " + recordId));
        r.softDelete();
        recordRepo.save(r);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private CaseSheetTemplate fetchTemplate(UUID id) {
        return templateRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CaseSheetTemplate not found: " + id));
    }

    public String getSpecializationForEncounter(UUID encounterId) {
        ClinicalEncounter encounter = encounterRepo.findById(encounterId).orElse(null);
        if (encounter == null) return "GENERAL";
        return resolveSpecialization(encounter.getPrimaryProviderId());
    }

    /** Looks up the consultant's specialisation field or department name to auto-select template */
    private String resolveSpecialization(UUID providerId) {
        if (providerId == null) return "GENERAL";
        return consultantRepo.findById(providerId)
                .map(c -> {
                    if (c.getSpecialisation() != null && !c.getSpecialisation().isBlank()) {
                        return c.getSpecialisation().toUpperCase();
                    }
                    if (c.getDepartmentId() != null) {
                        return departmentRepo.findById(c.getDepartmentId())
                                .map(d -> d.getName().toUpperCase())
                                .orElse("GENERAL");
                    }
                    return "GENERAL";
                })
                .orElse("GENERAL");
    }

    private void demoteOtherDefaults(String specialization, CaseSheetVisitType visitType, UUID excludeId) {
        templateRepo.findByStatusAndSpecializationIgnoreCaseAndVisitTypeOrderByNameAsc(
                EntityStatus.ACTIVE, specialization, visitType).stream()
                .filter(t -> t.isDefaultTemplate() && (excludeId == null || !t.getId().equals(excludeId)))
                .forEach(t -> { t.setDefaultTemplate(false); templateRepo.save(t); });
    }

    private CaseSheetTemplateField buildField(FieldRequest req, CaseSheetTemplate template) {
        CaseSheetTemplateField f = new CaseSheetTemplateField();
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

    private CaseSheetTemplateSummary toSummary(CaseSheetTemplate t) {
        return new CaseSheetTemplateSummary(
                t.getId(), t.getName(), t.getSpecialization(), t.getVisitType(),
                t.getDescription(), t.isDefaultTemplate(), t.getFields().size());
    }

    private CaseSheetTemplateDetail toDetail(CaseSheetTemplate t) {
        List<FieldResponse> fields = t.getFields().stream()
                .filter(f -> f.getStatus() == EntityStatus.ACTIVE && f.isVisible())
                .map(f -> new FieldResponse(f.getId(), f.getFieldKey(), f.getLabel(), f.getFieldType(),
                        f.getSection(), f.getDisplayOrder(), f.isRequired(), f.getPlaceholder(),
                        f.getHelpText(), f.getOptions(), f.getValidation(), f.getDefaultValue(), f.isVisible()))
                .collect(Collectors.toList());
        return new CaseSheetTemplateDetail(t.getId(), t.getName(), t.getSpecialization(),
                t.getVisitType(), t.getDescription(), t.isDefaultTemplate(), fields,
                t.getCreatedAt(), t.getModifiedAt());
    }

    private CaseSheetRecordResponse toRecordResponse(CaseSheetRecord r) {
        return new CaseSheetRecordResponse(r.getId(), r.getEncounterId(),
                toSummary(r.getTemplate()), r.getData(), r.getRecordedBy(),
                r.getRecordedAt(), r.getModifiedAt());
    }
}
