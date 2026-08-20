package com.hms.application.portal;

import com.hms.api.appointment.request.BookAppointmentRequest;
import com.hms.api.appointment.response.AppointmentResponse;
import com.hms.api.appointment.response.SlotAvailabilityResponse;
import com.hms.api.portal.request.PortalRequests;
import com.hms.api.portal.response.PortalResponses;
import com.hms.application.appointment.AppointmentSchedulingService;
import com.hms.application.diagnostic.DiagnosticReportService;
import com.hms.domain.appointment.model.Appointment;
import com.hms.domain.attachment.model.Attachment;
import com.hms.application.patient.PatientManagementService;
import com.hms.domain.casesheet.model.CaseSheetRecord;
import com.hms.domain.consultant.model.Consultant;
import com.hms.domain.diagnostic.model.DiagnosticReport;
import com.hms.domain.encounter.model.ClinicalEncounter;
import com.hms.domain.encounter.model.VisitMode;
import com.hms.domain.patient.model.Patient;
import com.hms.domain.appointment.model.AppointmentSlot;
import com.hms.application.attachment.AttachmentService;
import com.hms.domain.shared.model.EntityStatus;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.appointment.AppointmentJpaRepository;
import com.hms.infrastructure.persistence.attachment.AttachmentJpaRepository;
import com.hms.infrastructure.persistence.casesheet.CaseSheetRecordJpaRepository;
import com.hms.infrastructure.persistence.consultant.ConsultantJpaRepository;
import com.hms.infrastructure.persistence.department.DepartmentJpaRepository;
import com.hms.infrastructure.persistence.encounter.ClinicalEncounterJpaRepository;
import com.hms.infrastructure.persistence.patient.PatientJpaRepository;
import com.hms.infrastructure.persistence.tenant.BranchJpaRepository;
import com.hms.infrastructure.persistence.tenant.TenantJpaRepository;
import com.hms.infrastructure.sequence.NumberSequenceEntity;
import com.hms.infrastructure.sequence.NumberSequenceJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import lombok.RequiredArgsConstructor;
import com.hms.infrastructure.persistence.appointment.AppointmentSlotJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import com.hms.infrastructure.tenant.BranchContext;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortalPatientService {

    private final PatientJpaRepository patientRepo;
    private final TenantJpaRepository tenantRepo;
    private final BranchJpaRepository branchRepo;
    private final NumberSequenceJpaRepository numberSequenceRepo;
    private final ConsultantJpaRepository consultantRepo;
    private final DepartmentJpaRepository departmentRepo;
    private final AppointmentJpaRepository appointmentRepo;
    private final AppointmentSlotJpaRepository slotRepo;
    private final AppointmentSchedulingService appointmentSchedulingService;
    private final ClinicalEncounterJpaRepository encounterRepo;
    private final CaseSheetRecordJpaRepository caseSheetRecordRepo;
    private final DiagnosticReportService diagnosticReportService;
    private final AttachmentJpaRepository attachmentRepo;
    private final AttachmentService attachmentService;
    private final PatientManagementService patientManagementService;
    private final com.hms.infrastructure.persistence.billing.BillJpaRepository billRepo;
    private final com.hms.infrastructure.persistence.billing.PaymentJpaRepository paymentRepo;
    private final com.hms.application.print.PrintService printService;
    private final com.hms.infrastructure.persistence.diagnostic.DiagnosticOrderJpaRepository diagOrderRepo;
    private final com.hms.infrastructure.persistence.diagtemplate.DiagnosticTemplateJpaRepository diagTemplateRepo;
    private final com.hms.infrastructure.persistence.diagnostic.DiagnosticReportJpaRepository diagReportRepo;
    private final com.hms.infrastructure.persistence.casesheet.DischargeSummaryRecordJpaRepository dischargeSummaryRecordRepo;
    private final com.hms.infrastructure.persistence.sales.PharmacySaleJpaRepository saleRepo;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public PortalResponses.PatientProfile getProfile(UUID patientId, UUID tenantId, UUID branchId) {
        // The patient's registered branch may differ from the branch selected at
        // login, so the Hibernate branchFilter (strict branch_id = :branchId on
        // Patient) would hide the patient from themselves. Temporarily clear
        // BranchContext so that TenantFilterAspect disables the branchFilter —
        // the tenantFilter still scopes it to the correct hospital.
        Patient patient;
        try {
            BranchContext.clear();
            patient = patientRepo.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));
        } finally {
            // Re-set the branch context for subsequent queries in this request.
            if (branchId != null) {
                BranchContext.set(branchId);
            }
        }

        String tenantName = tenantRepo.findById(tenantId)
            .map(t -> t.getName())
            .orElse("Hospital");

        String branchName = branchRepo.findById(branchId)
            .map(b -> b.getName())
            .orElse("Main Branch");

        String numberSeq = numberSequenceRepo.findById(patientId)
            .map(NumberSequenceEntity::getValue)
            .orElse(null);

        Integer age = null;
        LocalDate effectiveDob = patient.getDateOfBirth() != null ? patient.getDateOfBirth() : patient.getEstimatedDateOfBirth();
        if (effectiveDob != null) {
            age = java.time.Period.between(effectiveDob, LocalDate.now()).getYears();
        }

        String photoUrl = null;
        var attachments = attachmentRepo.findByPatientIdOrderByCreatedAtDesc(patientId);
        if (attachments != null) {
            var profilePic = attachments.stream()
                .filter(a -> "PATIENT_PICTURE".equalsIgnoreCase(a.getCategory())
                          || "PROFILE_PICTURE".equalsIgnoreCase(a.getCategory())
                          || "PATIENT_PHOTO".equalsIgnoreCase(a.getCategory())
                          || (a.getAttachmentType() != null && "PATIENT_PICTURE".equalsIgnoreCase(a.getAttachmentType().name())))
                .max(Comparator.comparing(Attachment::getCreatedAt));
            if (profilePic.isPresent()) {
                Attachment pic = profilePic.get();
                if (pic.getFilePath() != null) {
                    try {
                        java.nio.file.Path p = java.nio.file.Paths.get(pic.getFilePath());
                        if (java.nio.file.Files.exists(p)) {
                            byte[] bytes = java.nio.file.Files.readAllBytes(p);
                            String mime = pic.getContentType() != null ? pic.getContentType() : "image/jpeg";
                            photoUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to read patient photo file", e);
                    }
                }
                if (photoUrl == null) {
                    photoUrl = "/portal/attachments/" + pic.getId() + "/content";
                }
            }
        }

        return new PortalResponses.PatientProfile(
            patient.getId(),
            patient.computeFullName(),
            age,
            patient.getGender() != null ? patient.getGender().name() : "OTHER",
            patient.getBloodGroup(),
            numberSeq,
            photoUrl,
            patient.isSelfRegistered(),
            tenantName,
            branchName,
            patient.getDateOfBirth() != null ? patient.getDateOfBirth() : patient.getEstimatedDateOfBirth(),
            patient.getContactNumber(),
            patient.getEmail(),
            patient.getAddress(),
            patient.getFirstName(),
            patient.getLastName(),
            patient.getSalutation()
        );
    }

    @Transactional
    public PortalResponses.PatientProfile updateProfile(UUID patientId, UUID tenantId, UUID branchId, PortalRequests.UpdateProfile req) {
        LocalDate calculatedDob = null;
        if (req.age() != null && req.age() >= 0) {
            calculatedDob = LocalDate.now().minusYears(req.age());
        }
        LocalDate finalDob = req.dateOfBirth() != null ? req.dateOfBirth() : calculatedDob;

        var updateReq = new com.hms.api.patient.request.UpdatePatientRequest(
            req.salutation(),
            req.firstName(),
            req.lastName(),
            req.gender(),
            finalDob,
            finalDob,
            req.mobile(),
            req.email(),
            req.bloodGroup(),
            req.address(),
            null, // primaryProviderId
            null, // areaId
            false // isClinicalTrial
        );
        
        patientManagementService.updatePatient(patientId, updateReq);

        if (req.avatarBase64() != null && !req.avatarBase64().isBlank()) {
            try {
                String base64Data = req.avatarBase64();
                String mimeType = "image/jpeg";
                if (base64Data.contains(",")) {
                    String[] parts = base64Data.split(",", 2);
                    if (parts[0].contains("image/png")) mimeType = "image/png";
                    else if (parts[0].contains("image/webp")) mimeType = "image/webp";
                    base64Data = parts[1];
                }
                byte[] decoded = Base64.getDecoder().decode(base64Data.trim());
                String extension = mimeType.contains("png") ? ".png" : (mimeType.contains("webp") ? ".webp" : ".jpg");
                String fileName = "avatar_" + patientId + extension;
                
                com.hms.application.portal.ByteArrayMultipartFile multipartFile =
                    new com.hms.application.portal.ByteArrayMultipartFile(decoded, "file", fileName, mimeType);
                attachmentService.saveAttachment(
                    multipartFile,
                    com.hms.domain.attachment.model.AttachmentType.PATIENT_PICTURE,
                    null,
                    patientId,
                    null,
                    "PATIENT_PICTURE"
                );
            } catch (Exception e) {
                log.warn("Failed to save patient profile picture for patient {}", patientId, e);
            }
        }
        
        return getProfile(patientId, tenantId, branchId);
    }

    @Transactional(readOnly = true)
    public List<PortalResponses.ConsultantSummary> listConsultants(String query, UUID departmentId) {
        List<Consultant> consultants = consultantRepo.findAll();
        String qLower = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";

        return consultants.stream()
            .filter(c -> c.getStatus() == EntityStatus.ACTIVE)
            .filter(c -> departmentId == null || departmentId.equals(c.getDepartmentId()))
            .filter(c -> {
                if (qLower.isEmpty()) return true;
                String fullName = (c.getFirstName() + " " + c.getLastName()).toLowerCase(Locale.ROOT);
                String spec = c.getSpecialisation() != null ? c.getSpecialisation().toLowerCase(Locale.ROOT) : "";
                return fullName.contains(qLower) || spec.contains(qLower);
            })
            .map(c -> {
                String deptName = null;
                if (c.getDepartmentId() != null) {
                    deptName = departmentRepo.findById(c.getDepartmentId())
                        .map(d -> d.getName())
                        .orElse(null);
                }
                String fullName = (c.getSalutation() != null ? c.getSalutation() + " " : "")
                    + c.getFirstName() + " " + c.getLastName();
                return new PortalResponses.ConsultantSummary(
                    c.getId(),
                    fullName.trim(),
                    c.getSpecialisation(),
                    c.getQualification(),
                    deptName,
                    c.getConsultantType() != null ? c.getConsultantType().name() : "INTERNAL",
                    null
                );
            })
            .toList();
    }

    @Transactional(readOnly = true)
    public List<PortalResponses.SlotAvailability> getAvailability(UUID consultantId, LocalDate date) {
        List<SlotAvailabilityResponse> slots = appointmentSchedulingService.getSlotAvailability(consultantId, date);
        return slots.stream().map(s -> new PortalResponses.SlotAvailability(
            s.slotId(),
            s.fromTime().toString(),
            s.toTime().toString(),
            s.maxPatients(),
            s.bookedCount(),
            s.availableCount(),
            s.isAvailable()
        )).toList();
    }

    @Transactional(readOnly = true)
    public PortalResponses.PageResponse<PortalResponses.AppointmentSummary> listAppointments(
            UUID patientId, String scope, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Appointment> pageResult;

        if ("upcoming".equalsIgnoreCase(scope)) {
            pageResult = appointmentRepo.findUpcomingByPatientId(patientId, pageable);
        } else if ("past".equalsIgnoreCase(scope)) {
            pageResult = appointmentRepo.findPastByPatientId(patientId, pageable);
        } else {
            pageResult = appointmentRepo.findByPatientId(patientId, pageable);
        }

        List<PortalResponses.AppointmentSummary> list = pageResult.getContent().stream()
            .map(this::toAppointmentSummary)
            .toList();

        return new PortalResponses.PageResponse<>(
            list,
            pageResult.getNumber(),
            pageResult.getSize(),
            pageResult.getTotalElements(),
            pageResult.getTotalPages()
        );
    }

    @Transactional
    public PortalResponses.AppointmentSummary bookAppointment(
            UUID patientId, UUID tenantId, UUID branchId, PortalRequests.BookAppointment body) {
        var req = new BookAppointmentRequest(
            patientId,
            body.providerId(),
            body.slotId(),
            body.appointmentDate(),
            body.notes(),
            null, null, null, null, null
        );

        AppointmentResponse response = appointmentSchedulingService.bookAppointment(req);
        Appointment saved = appointmentRepo.findById(response.id())
            .orElseThrow(() -> new ResourceNotFoundException("Appointment", response.id()));
        return toAppointmentSummary(saved);
    }

    @Transactional
    public PortalResponses.AppointmentSummary cancelAppointment(
            UUID patientId, UUID appointmentId, String reason) {
        Appointment appointment = appointmentRepo.findById(appointmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Appointment", appointmentId));

        if (!patientId.equals(appointment.getPatientId())) {
            throw new BusinessRuleViolationException("Appointment does not belong to this patient");
        }

        AppointmentResponse response = appointmentSchedulingService.cancel(appointmentId);
        Appointment saved = appointmentRepo.findById(appointmentId).orElse(appointment);
        return toAppointmentSummary(saved);
    }

    @Transactional(readOnly = true)
    public PortalResponses.PageResponse<PortalResponses.VisitSummary> listVisits(
            UUID patientId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ClinicalEncounter> pageResult = encounterRepo
            .findByPatientIdAndCancelledFalseOrderByStartedAtDesc(patientId, pageable);

        List<PortalResponses.VisitSummary> list = pageResult.getContent().stream()
            .map(e -> {
                String providerName = null;
                if (e.getPrimaryProviderId() != null) {
                    providerName = consultantRepo.findById(e.getPrimaryProviderId())
                        .map(c -> (c.getSalutation() != null ? c.getSalutation() + " " : "") + c.getFirstName() + " " + c.getLastName())
                        .orElse(null);
                }
                String encType = e.getEncounterType() == com.hms.domain.billing.model.EncounterType.INPATIENT ? "IP" : "OP";
                String status = e.getEncounterStatus() != null ? e.getEncounterStatus().name() : "CHECKED_IN";
                return new PortalResponses.VisitSummary(
                    e.getId(),
                    e.getStartedAt(),
                    providerName,
                    encType,
                    status
                );
            })
            .toList();

        return new PortalResponses.PageResponse<>(
            list,
            pageResult.getNumber(),
            pageResult.getSize(),
            pageResult.getTotalElements(),
            pageResult.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public PortalResponses.VisitDetail getVisitDetail(UUID patientId, UUID encounterId) {
        ClinicalEncounter encounter = encounterRepo.findById(encounterId)
            .orElseThrow(() -> new ResourceNotFoundException("ClinicalEncounter", encounterId));

        if (!patientId.equals(encounter.getPatientId())) {
            throw new BusinessRuleViolationException("Encounter does not belong to this patient");
        }

        String providerName = null;
        String qualification = null;
        String deptName = null;
        if (encounter.getPrimaryProviderId() != null) {
            Consultant c = consultantRepo.findById(encounter.getPrimaryProviderId()).orElse(null);
            if (c != null) {
                providerName = (c.getSalutation() != null ? c.getSalutation() + " " : "") + c.getFirstName() + " " + c.getLastName();
                qualification = c.getQualification();
                if (c.getDepartmentId() != null) {
                    deptName = departmentRepo.findById(c.getDepartmentId()).map(d -> d.getName()).orElse(null);
                }
            }
        }

        String branchName = branchRepo.findById(encounter.getBranchId()).map(b -> b.getName()).orElse(null);
        String hospitalAddress = branchRepo.findById(encounter.getBranchId()).map(b -> (String) b.getAddress())
            .orElseGet(() -> tenantRepo.findById(encounter.getTenantId()).map(t -> (String) t.getAddress()).orElse(null));
        String hospitalContact = branchRepo.findById(encounter.getBranchId()).map(b -> (String) b.getContactNumber())
            .orElseGet(() -> tenantRepo.findById(encounter.getTenantId()).map(t -> (String) t.getContactNumber()).orElse(null));

        String patientName = patientRepo.findById(encounter.getPatientId()).map(com.hms.domain.patient.model.Patient::computeFullName).orElse(null);
        String patientNumber = numberSequenceRepo.findById(encounter.getPatientId()).map(NumberSequenceEntity::getValue).orElse(null);

        int casesheetCount = caseSheetRecordRepo.findByEncounterIdAndStatus(encounterId, EntityStatus.ACTIVE).size();
        int diagReportsCount = diagnosticReportService.getReportsByEncounter(encounterId).size();
        int attachmentsCount = attachmentRepo.findByEncounterIdOrderByCreatedAtDesc(encounterId).size();

        String logoDataUrl = null;
        try {
            UUID tenantId = encounter.getTenantId();
            if (tenantId != null) {
                Optional<Attachment> logoOpt = attachmentRepo.findLatestByCategoryAndTenantOnlyNative("HOSPITAL_LOGO", tenantId);
                if (!logoOpt.isPresent()) {
                    logoOpt = attachmentRepo.findLatestByCategoryAndTenantAnyBranchNative("HOSPITAL_LOGO", tenantId);
                }
                if (logoOpt.isPresent()) {
                    Attachment logo = logoOpt.get();
                    if (logo.getFilePath() != null) {
                        java.nio.file.Path logoPath = java.nio.file.Paths.get(logo.getFilePath());
                        if (java.nio.file.Files.exists(logoPath)) {
                            byte[] logoBytes = java.nio.file.Files.readAllBytes(logoPath);
                            String base64 = Base64.getEncoder().encodeToString(logoBytes);
                            String mimeType = logo.getContentType() != null ? logo.getContentType() : "image/png";
                            logoDataUrl = "data:" + mimeType + ";base64," + base64;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load hospital logo for encounter {}", encounterId, e);
        }

        return new PortalResponses.VisitDetail(
            encounter.getId(),
            encounter.getStartedAt(),
            providerName,
            qualification,
            encounter.getEncounterType() == com.hms.domain.billing.model.EncounterType.INPATIENT ? "IP" : "OP",
            encounter.getEncounterStatus() != null ? encounter.getEncounterStatus().name() : "CHECKED_IN",
            branchName,
            deptName,
            null,
            new PortalResponses.VisitCounts(casesheetCount, 0, diagReportsCount, attachmentsCount),
            logoDataUrl,
            patientName,
            patientNumber,
            hospitalAddress,
            hospitalContact
        );
    }

    @Transactional(readOnly = true)
    public List<PortalResponses.CaseSheetSection> getCaseSheetSections(UUID patientId, UUID encounterId) {
        ClinicalEncounter encounter = encounterRepo.findById(encounterId)
            .orElseThrow(() -> new ResourceNotFoundException("ClinicalEncounter", encounterId));
        if (!patientId.equals(encounter.getPatientId())) {
            throw new BusinessRuleViolationException("Encounter does not belong to this patient");
        }

        List<CaseSheetRecord> records = caseSheetRecordRepo.findByEncounterIdAndStatus(encounterId, EntityStatus.ACTIVE);
        List<PortalResponses.CaseSheetSection> sections = new ArrayList<>();

        for (CaseSheetRecord r : records) {
            String tplName = r.getTemplate() != null ? r.getTemplate().getName() : "Clinical Note";
            String visitType = encounter.getEncounterType() == com.hms.domain.billing.model.EncounterType.INPATIENT ? "IP" : "OP";
            List<PortalResponses.CaseSheetField> fields = new ArrayList<>();

            if (r.getTemplate() != null && r.getTemplate().getFields() != null && !r.getTemplate().getFields().isEmpty()) {
                var sortedFields = r.getTemplate().getFields().stream()
                    .sorted(Comparator.comparingInt(com.hms.domain.casesheet.model.CaseSheetTemplateField::getDisplayOrder))
                    .toList();

                for (var f : sortedFields) {
                    Object val = r.getData() != null ? r.getData().get(f.getFieldKey()) : null;
                    fields.add(new PortalResponses.CaseSheetField(
                        f.getFieldKey(),
                        f.getLabel(),
                        f.getFieldType(),
                        val,
                        f.getSection(),
                        f.getDisplayOrder()
                    ));
                }
            } else if (r.getData() != null && !r.getData().isEmpty()) {
                for (var entry : r.getData().entrySet()) {
                    fields.add(new PortalResponses.CaseSheetField(
                        entry.getKey(),
                        entry.getKey(),
                        "TEXT",
                        entry.getValue(),
                        null,
                        0
                    ));
                }
            }

            sections.add(new PortalResponses.CaseSheetSection(
                tplName,
                visitType,
                null,
                r.getRecordedAt() != null ? r.getRecordedAt().toString() : null,
                fields
            ));
        }

        return sections;
    }

    @Transactional(readOnly = true)
    public List<PortalResponses.CaseSheetSection> getDischargeSummary(UUID patientId, UUID encounterId) {
        ClinicalEncounter encounter = encounterRepo.findById(encounterId)
            .orElseThrow(() -> new ResourceNotFoundException("ClinicalEncounter", encounterId));
        if (!patientId.equals(encounter.getPatientId())) {
            throw new BusinessRuleViolationException("Encounter does not belong to this patient");
        }

        List<com.hms.domain.casesheet.model.DischargeSummaryRecord> records =
            dischargeSummaryRecordRepo.findByEncounterIdAndStatus(encounterId, EntityStatus.ACTIVE);
        List<PortalResponses.CaseSheetSection> sections = new ArrayList<>();

        for (var r : records) {
            String tplName = r.getTemplate() != null ? r.getTemplate().getName() : "Discharge Summary";
            List<PortalResponses.CaseSheetField> fields = new ArrayList<>();

            if (r.getTemplate() != null && r.getTemplate().getFields() != null && !r.getTemplate().getFields().isEmpty()) {
                var sortedFields = r.getTemplate().getFields().stream()
                    .sorted(Comparator.comparingInt(com.hms.domain.casesheet.model.DischargeSummaryTemplateField::getDisplayOrder))
                    .toList();

                for (var f : sortedFields) {
                    Object val = r.getData() != null ? r.getData().get(f.getFieldKey()) : null;
                    fields.add(new PortalResponses.CaseSheetField(
                        f.getFieldKey(),
                        f.getLabel(),
                        f.getFieldType(),
                        val,
                        f.getSection(),
                        f.getDisplayOrder()
                    ));
                }
            } else if (r.getData() != null && !r.getData().isEmpty()) {
                for (var entry : r.getData().entrySet()) {
                    fields.add(new PortalResponses.CaseSheetField(
                        entry.getKey(),
                        entry.getKey(),
                        "TEXT",
                        entry.getValue(),
                        null,
                        0
                    ));
                }
            }

            sections.add(new PortalResponses.CaseSheetSection(
                tplName,
                "IP",
                null,
                r.getRecordedAt() != null ? r.getRecordedAt().toString() : null,
                fields
            ));
        }

        return sections;
    }

    @Transactional(readOnly = true)
    public List<PortalResponses.DiagnosticOrderGroup> getDiagnosticReports(UUID patientId, UUID encounterId) {
        ClinicalEncounter encounter = encounterRepo.findById(encounterId)
            .orElseThrow(() -> new ResourceNotFoundException("ClinicalEncounter", encounterId));
        if (!patientId.equals(encounter.getPatientId())) {
            throw new BusinessRuleViolationException("Encounter does not belong to this patient");
        }

        List<com.hms.domain.diagnostic.model.DiagnosticOrder> orders = diagOrderRepo.findByEncounterId(encounterId);
        List<com.hms.domain.diagnostic.model.DiagnosticTemplate> allTemplates = diagTemplateRepo.findAllNonDeleted();

        List<PortalResponses.DiagnosticOrderGroup> groups = new ArrayList<>();

        for (com.hms.domain.diagnostic.model.DiagnosticOrder ord : orders) {
            List<UUID> lineIds = ord.getLines().stream()
                .map(com.hms.domain.diagnostic.model.DiagnosticOrderLine::getId)
                .toList();
            List<com.hms.domain.diagnostic.model.DiagnosticReport> lineReports = lineIds.isEmpty()
                ? List.of()
                : diagReportRepo.findByOrderLineIds(lineIds);

            List<PortalResponses.DiagnosticReportLine> lines = new ArrayList<>();

            for (com.hms.domain.diagnostic.model.DiagnosticOrderLine line : ord.getLines()) {
                List<com.hms.domain.diagnostic.model.DiagnosticReport> reportsForLine = lineReports.stream()
                    .filter(r -> line.getId().equals(r.getDiagnosticOrderLineId()))
                    .toList();

                String cat = ord.getDiagnosticType() != null ? ord.getDiagnosticType().name() : "LAB";
                String st = line.getTestStatus() != null ? line.getTestStatus().name() : "PENDING";
                Instant orderedAt = ord.getCreatedAt() != null ? ord.getCreatedAt() : encounter.getStartedAt();

                var templateOpt = allTemplates.stream()
                    .filter(t -> t.getName() != null && line.getItemName() != null &&
                        t.getName().trim().equalsIgnoreCase(line.getItemName().trim()))
                    .findFirst();

                String templateData = reportsForLine.stream()
                    .map(com.hms.domain.diagnostic.model.DiagnosticReport::getTemplateData)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);

                List<PortalResponses.DiagnosticParameter> params = new ArrayList<>();
                if (ord.isLab()) {
                    if (templateOpt.isPresent() && templateOpt.get().getLabTemplateDetails() != null && !templateOpt.get().getLabTemplateDetails().isEmpty()) {
                        var sortedDetails = templateOpt.get().getLabTemplateDetails().stream()
                            .sorted(Comparator.comparingInt(d -> d.getOrderNumber() != null ? d.getOrderNumber() : 0))
                            .toList();

                        for (var ltd : sortedDetails) {
                            String val = reportsForLine.stream()
                                .filter(r -> ltd.getId().equals(r.getLabTemplateDetailId()))
                                .map(com.hms.domain.diagnostic.model.DiagnosticReport::getValue)
                                .filter(Objects::nonNull)
                                .findFirst()
                                .orElse("—");

                            params.add(new PortalResponses.DiagnosticParameter(
                                ltd.getResultName(),
                                val,
                                ltd.getUnit() != null ? ltd.getUnit() : "—",
                                ltd.getNormalRange() != null ? ltd.getNormalRange() : "—"
                            ));
                        }
                    } else if (!reportsForLine.isEmpty()) {
                        for (var r : reportsForLine) {
                            params.add(new PortalResponses.DiagnosticParameter(
                                line.getItemName(),
                                r.getValue() != null ? r.getValue() : "—",
                                templateOpt.map(com.hms.domain.diagnostic.model.DiagnosticTemplate::getUnit).orElse("—"),
                                templateOpt.map(com.hms.domain.diagnostic.model.DiagnosticTemplate::getReferenceRange).orElse("—")
                            ));
                        }
                    } else if (line.getResultValue() != null) {
                        params.add(new PortalResponses.DiagnosticParameter(
                            line.getItemName(),
                            line.getResultValue(),
                            line.getResultUnit() != null ? line.getResultUnit() : "—",
                            line.getReferenceRange() != null ? line.getReferenceRange() : "—"
                        ));
                    }
                }

                String singleVal = reportsForLine.isEmpty() ? line.getResultValue() : reportsForLine.get(0).getValue();
                boolean isApproved = reportsForLine.stream().anyMatch(r -> Boolean.TRUE.equals(r.getIsApproved()));

                lines.add(new PortalResponses.DiagnosticReportLine(
                    line.getId(),
                    line.getItemName(),
                    cat,
                    singleVal,
                    line.getResultUnit(),
                    line.getReferenceRange(),
                    line.getTestStatus() != null ? line.getTestStatus().name() : "RESULTED",
                    st,
                    templateData,
                    orderedAt,
                    isApproved,
                    params
                ));
            }

            groups.add(new PortalResponses.DiagnosticOrderGroup(
                ord.getId(),
                ord.getSequenceNumber() != null ? ord.getSequenceNumber() : "ORD-" + ord.getId().toString().substring(0, 8).toUpperCase(Locale.ROOT),
                ord.getOrderDate() != null ? ord.getOrderDate().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant() : encounter.getStartedAt(),
                ord.getTestStatus() != null ? ord.getTestStatus().name() : "RESULTED",
                lines
            ));
        }

        return groups;
    }

    @Transactional(readOnly = true)
    public List<PortalResponses.AttachmentMeta> getAttachments(UUID patientId, UUID encounterId) {
        ClinicalEncounter encounter = encounterRepo.findById(encounterId)
            .orElseThrow(() -> new ResourceNotFoundException("ClinicalEncounter", encounterId));
        if (!patientId.equals(encounter.getPatientId())) {
            throw new BusinessRuleViolationException("Encounter does not belong to this patient");
        }

        List<Attachment> list = attachmentRepo.findByEncounterIdOrderByCreatedAtDesc(encounterId);
        return list.stream().map(a -> {
            String cat = a.getCategory();
            if (cat != null && cat.matches("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")) {
                cat = "Scan / Attachment";
            }
            long size = 0L;
            if (a.getFilePath() != null) {
                try {
                    java.nio.file.Path p = java.nio.file.Paths.get(a.getFilePath());
                    if (java.nio.file.Files.exists(p)) {
                        size = java.nio.file.Files.size(p);
                    }
                } catch (Exception ignored) {}
            }
            return new PortalResponses.AttachmentMeta(
                a.getId(),
                a.getFileName(),
                a.getContentType(),
                cat,
                size > 0 ? size : null,
                a.getCreatedAt()
            );
        }).toList();
    }

    @Transactional(readOnly = true)
    public PortalResponses.SignedDownload getDownloadUrl(UUID patientId, UUID attachmentId) {
        Attachment attachment = attachmentRepo.findById(attachmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Attachment", attachmentId));

        return new PortalResponses.SignedDownload(
            "/portal/attachments/" + attachmentId + "/content",
            Instant.now().plusSeconds(300)
        );
    }

    @Transactional(readOnly = true)
    public Attachment getAttachmentEntity(UUID patientId, UUID attachmentId) {
        Attachment attachment = attachmentRepo.findById(attachmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Attachment", attachmentId));
        if (!attachment.getPatientId().equals(patientId)) {
            throw new com.hms.exception.BusinessRuleViolationException("Attachment does not belong to this patient");
        }
        return attachment;
    }

    @Transactional(readOnly = true)
    public org.springframework.core.io.Resource downloadAttachmentContent(UUID patientId, UUID attachmentId) {
        getAttachmentEntity(patientId, attachmentId); // validate ownership
        return attachmentService.downloadFile(attachmentId);
    }

    private PortalResponses.AppointmentSummary toAppointmentSummary(Appointment a) {
        String providerName = null;
        String deptName = null;
        if (a.getProviderId() != null) {
            Consultant c = consultantRepo.findById(a.getProviderId()).orElse(null);
            if (c != null) {
                providerName = (c.getSalutation() != null ? c.getSalutation() + " " : "")
                    + c.getFirstName() + " " + c.getLastName();
                if (c.getDepartmentId() != null) {
                    deptName = departmentRepo.findById(c.getDepartmentId()).map(d -> d.getName()).orElse(null);
                }
            }
        }

        String branchName = a.getBranchId() != null
            ? branchRepo.findById(a.getBranchId()).map(b -> b.getName()).orElse(null)
            : null;

        String dateStr = a.getAppointmentDate() != null ? a.getAppointmentDate().toString() : "";
        String fromTimeStr = a.getAppointmentTime() != null ? a.getAppointmentTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "09:00";
        String toTimeStr = fromTimeStr;

        if (a.getSlotId() != null) {
            AppointmentSlot slot = slotRepo.findById(a.getSlotId()).orElse(null);
            if (slot != null) {
                fromTimeStr = slot.getFromTime();
                toTimeStr = slot.getToTime();
            }
        }

        return new PortalResponses.AppointmentSummary(
            a.getId(),
            dateStr,
            fromTimeStr,
            toTimeStr,
            a.getAppointmentStatus() != null ? a.getAppointmentStatus().name() : "BOOKED",
            a.getProviderId(),
            providerName != null ? providerName.trim() : "Consultant",
            deptName,
            branchName,
            a.getNotes()
        );
    }

    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public List<PortalResponses.PrescriptionSummary> getPrescriptions(UUID patientId, UUID encounterId) {
        ClinicalEncounter encounter = encounterRepo.findById(encounterId)
            .orElseThrow(() -> new ResourceNotFoundException("ClinicalEncounter", encounterId));
        if (!patientId.equals(encounter.getPatientId())) {
            throw new BusinessRuleViolationException("Encounter does not belong to this patient");
        }

        if (encounter.getConsultantShareMap() == null) return List.of();
        Object raw = encounter.getConsultantShareMap().get("prescriptions");
        if (!(raw instanceof List<?> prescList) || prescList.isEmpty()) return List.of();

        List<PortalResponses.PrescriptionSummary> result = new ArrayList<>();
        for (Object item : prescList) {
            if (!(item instanceof Map<?, ?> pm)) continue;
            Map<String, Object> prxMap = (Map<String, Object>) pm;
            Object rawItems = prxMap.get("items");
            if (!(rawItems instanceof List<?> items) || ((List<?>) items).isEmpty()) continue;

            List<PortalResponses.PrescriptionItem> lineItems = new ArrayList<>();
            for (Object li : items) {
                if (!(li instanceof Map<?, ?> lm)) continue;
                Map<String, Object> l = (Map<String, Object>) lm;
                lineItems.add(new PortalResponses.PrescriptionItem(
                    str(l.get("drugName")),
                    str(l.get("frequency")),
                    str(l.get("duration")),
                    l.get("qty") instanceof Number n ? n.intValue() : 1,
                    str(l.get("instructionLabel") != null ? l.get("instructionLabel") : l.get("instructionId")),
                    str(l.get("routeLabel") != null ? l.get("routeLabel") : l.get("routeId")),
                    str(l.get("remarks"))
                ));
            }

            String consultantName = str(prxMap.get("requestedByName"));
            if ((consultantName == null || consultantName.isBlank()) && encounter.getPrimaryProviderId() != null) {
                consultantName = consultantRepo.findById(encounter.getPrimaryProviderId())
                    .map(c -> ((c.getSalutation() != null ? c.getSalutation() + " " : "") + c.getFirstName() + " " + c.getLastName()).trim())
                    .orElse("Doctor");
            }

            Instant prescribedAt = parseInstant(prxMap.get("createdAt"));
            UUID prxId = parseUUID(prxMap.get("id"));

            result.add(new PortalResponses.PrescriptionSummary(
                prxId != null ? prxId : UUID.randomUUID(),
                prescribedAt != null ? prescribedAt : encounter.getStartedAt(),
                consultantName,
                lineItems
            ));
        }

        return result;
    }

    @Transactional(readOnly = true)
    public List<PortalResponses.BillSummary> getBills(UUID patientId, UUID encounterId) {
        ClinicalEncounter encounter = encounterRepo.findById(encounterId)
            .orElseThrow(() -> new ResourceNotFoundException("ClinicalEncounter", encounterId));
        if (!patientId.equals(encounter.getPatientId())) {
            throw new BusinessRuleViolationException("Encounter does not belong to this patient");
        }

        Optional<com.hms.domain.billing.model.Bill> billOpt = billRepo.findByEncounterId(encounterId);
        if (billOpt.isEmpty()) {
            return List.of();
        }

        com.hms.domain.billing.model.Bill bill = billOpt.get();
        String billNo = bill.getBillNumber();
        if (billNo == null || billNo.isBlank()) {
            billNo = numberSequenceRepo.findById(bill.getId())
                .map(NumberSequenceEntity::getValue)
                .orElse(null);
        }
        if (bill.isDraft() || bill.getBillStatus() == com.hms.domain.billing.model.BillStatus.DRAFT || billNo == null || billNo.isBlank()) {
            billNo = "Draft";
        }

        java.math.BigDecimal total = java.math.BigDecimal.valueOf(bill.getBillAmount()).movePointLeft(2);
        java.math.BigDecimal paid = java.math.BigDecimal.valueOf(bill.getPaymentTotal()).movePointLeft(2);
        java.math.BigDecimal balance = java.math.BigDecimal.valueOf(bill.computeDueAmount()).movePointLeft(2);
        String status = bill.getBillStatus() != null ? bill.getBillStatus().name() : "PAID";

        List<com.hms.domain.billing.model.Payment> payments = paymentRepo.findAllByBill_Id(bill.getId());
        List<PortalResponses.ReceiptSummary> receipts = payments.stream()
            .filter(p -> "Active".equalsIgnoreCase(p.getStatus()))
            .map(p -> new PortalResponses.ReceiptSummary(
                p.getId(),
                p.getSequenceNumber() != null ? p.getSequenceNumber() : ("REC-" + p.getId().toString().substring(0, 8).toUpperCase(Locale.ROOT)),
                p.getPaymentDate(),
                java.math.BigDecimal.valueOf(p.getAmount()).movePointLeft(2),
                p.getPaymentMode() != null ? p.getPaymentMode().name() : "CASH",
                p.getPaymentType() != null ? p.getPaymentType().name() : "PAYMENT"
            ))
            .toList();

        return List.of(new PortalResponses.BillSummary(
            bill.getId(),
            billNo,
            encounter.getStartedAt() != null ? encounter.getStartedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate() : LocalDate.now(),
            total,
            paid,
            balance,
            status,
            receipts
        ));
    }

    @Transactional(readOnly = true)
    public com.hms.api.printtemplate.response.PrintOutputResponse getVisitPrint(
            UUID patientId, UUID encounterId, String templateType, UUID targetId) {

        ClinicalEncounter encounter = encounterRepo.findById(encounterId)
            .orElseThrow(() -> new ResourceNotFoundException("ClinicalEncounter", encounterId));
        if (!patientId.equals(encounter.getPatientId())) {
            throw new BusinessRuleViolationException("Encounter does not belong to this patient");
        }

        Map<String, String> params = new HashMap<>();
        String actualType = templateType != null ? templateType.toUpperCase(Locale.ROOT) : "BILL";

        if ("BILL".equals(actualType) || "OP_RECEIPT".equals(actualType) || "IP_RECEIPT".equals(actualType) || "PAYMENT".equals(actualType)) {
            UUID billId = null;
            UUID paymentId = null;
            if (targetId != null) {
                if ("OP_RECEIPT".equals(actualType) || "IP_RECEIPT".equals(actualType) || "PAYMENT".equals(actualType)) {
                    var payOpt = paymentRepo.findById(targetId);
                    if (payOpt.isPresent()) {
                        paymentId = payOpt.get().getId();
                        billId = payOpt.get().getBill().getId();
                    } else {
                        billId = targetId;
                    }
                } else {
                    billId = targetId;
                }
            }
            if (billId == null) {
                billId = billRepo.findByEncounterId(encounterId)
                    .map(com.hms.domain.billing.model.Bill::getId)
                    .orElse(null);
            }
            if (billId != null) {
                params.put("id", billId.toString());
            }
            if (paymentId != null) {
                params.put("paymentId", paymentId.toString());
            }
        } else if ("LAB".equals(actualType) || "RADIOLOGY".equals(actualType) || "DIAGNOSTIC_ORDER".equals(actualType) || "DIAGNOSTIC".equals(actualType)) {
            UUID orderId = targetId;
            if (orderId == null) {
                var orders = diagOrderRepo.findByEncounterId(encounterId);
                if (!orders.isEmpty()) {
                    orderId = orders.get(0).getId();
                }
            }
            if (orderId != null) {
                params.put("id", orderId.toString());
            }
            if ("DIAGNOSTIC".equals(actualType)) {
                actualType = "LAB";
            }
        } else if ("SALES".equals(actualType) || "PRESCRIPTION".equals(actualType)) {
            UUID saleId = targetId;
            if (saleId == null) {
                var sales = saleRepo.findByEncounterId(encounterId);
                if (sales != null && !sales.isEmpty()) {
                    saleId = sales.get(0).getId();
                }
            }
            if (saleId != null) {
                params.put("id", saleId.toString());
                actualType = "SALES";
            }
        } else if ("DISCHARGE_SUMMARY".equals(actualType) || "CASESHEET".equals(actualType)) {
            params.put("id", encounterId.toString());
            actualType = "DISCHARGE_SUMMARY";
        }

        return printService.print(actualType, params);
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static UUID parseUUID(Object o) {
        if (o == null) return null;
        try { return UUID.fromString(o.toString()); } catch (Exception e) { return null; }
    }

    private static Instant parseInstant(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) {
            return Instant.ofEpochMilli(n.longValue());
        }
        String s = o.toString();
        try { return Instant.parse(s); } catch (Exception ignored) {}
        try { return Instant.ofEpochMilli(Long.parseLong(s)); } catch (Exception ignored) {}
        return null;
    }
}
