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
        if (patient.getDateOfBirth() != null) {
            age = java.time.Period.between(patient.getDateOfBirth(), LocalDate.now()).getYears();
        }

        String photoUrl = null;
        var attachments = attachmentRepo.findByPatientIdOrderByCreatedAtDesc(patientId);
        if (attachments != null) {
            var profilePic = attachments.stream()
                .filter(a -> "PROFILE_PICTURE".equalsIgnoreCase(a.getCategory()))
                .max(Comparator.comparing(Attachment::getCreatedAt));
            if (profilePic.isPresent()) {
                photoUrl = "/portal/attachments/" + profilePic.get().getId() + "/content";
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
            patient.getDateOfBirth(),
            patient.getContactNumber(),
            patient.getEmail(),
            patient.getAddress()
        );
    }

    @Transactional
    public PortalResponses.PatientProfile updateProfile(UUID patientId, UUID tenantId, UUID branchId, PortalRequests.UpdateProfile req) {
        var updateReq = new com.hms.api.patient.request.UpdatePatientRequest(
            null, // salutation
            req.firstName(),
            req.lastName(),
            req.gender(),
            req.dateOfBirth(),
            req.dateOfBirth(),
            req.mobile(),
            req.email(),
            req.bloodGroup(),
            req.address(),
            null, // primaryProviderId
            null, // areaId
            false // isClinicalTrial
        );
        
        patientManagementService.updatePatient(patientId, updateReq);
        
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
        String deptName = null;
        if (encounter.getPrimaryProviderId() != null) {
            Consultant c = consultantRepo.findById(encounter.getPrimaryProviderId()).orElse(null);
            if (c != null) {
                providerName = (c.getSalutation() != null ? c.getSalutation() + " " : "") + c.getFirstName() + " " + c.getLastName();
                if (c.getDepartmentId() != null) {
                    deptName = departmentRepo.findById(c.getDepartmentId()).map(d -> d.getName()).orElse(null);
                }
            }
        }

        String branchName = branchRepo.findById(encounter.getBranchId()).map(b -> b.getName()).orElse(null);

        int casesheetCount = caseSheetRecordRepo.findByEncounterIdAndStatus(encounterId, EntityStatus.ACTIVE).size();
        int diagReportsCount = diagnosticReportService.getReportsByEncounter(encounterId).size();
        int attachmentsCount = attachmentRepo.findByEncounterIdOrderByCreatedAtDesc(encounterId).size();

        return new PortalResponses.VisitDetail(
            encounter.getId(),
            encounter.getStartedAt(),
            providerName,
            encounter.getEncounterType() == com.hms.domain.billing.model.EncounterType.INPATIENT ? "IP" : "OP",
            encounter.getEncounterStatus() != null ? encounter.getEncounterStatus().name() : "CHECKED_IN",
            branchName,
            deptName,
            null,
            new PortalResponses.VisitCounts(casesheetCount, 0, diagReportsCount, attachmentsCount)
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

            if (r.getData() != null && !r.getData().isEmpty()) {
                for (var entry : r.getData().entrySet()) {
                    fields.add(new PortalResponses.CaseSheetField(
                        entry.getKey(),
                        entry.getKey(),
                        "TEXT",
                        entry.getValue()
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
    public List<PortalResponses.DiagnosticOrderGroup> getDiagnosticReports(UUID patientId, UUID encounterId) {
        ClinicalEncounter encounter = encounterRepo.findById(encounterId)
            .orElseThrow(() -> new ResourceNotFoundException("ClinicalEncounter", encounterId));
        if (!patientId.equals(encounter.getPatientId())) {
            throw new BusinessRuleViolationException("Encounter does not belong to this patient");
        }

        List<DiagnosticReport> reports = diagnosticReportService.getReportsByEncounter(encounterId);
        List<PortalResponses.DiagnosticReportLine> lines = reports.stream().map(r -> new PortalResponses.DiagnosticReportLine(
            r.getId(),
            "Diagnostic Test",
            r.getValue(),
            null,
            null,
            r.getResult(),
            Boolean.TRUE.equals(r.getIsApproved())
        )).toList();

        if (lines.isEmpty()) {
            return List.of();
        }

        return List.of(new PortalResponses.DiagnosticOrderGroup(
            encounterId,
            "ORD-" + encounterId.toString().substring(0, 8).toUpperCase(Locale.ROOT),
            encounter.getStartedAt(),
            "RESULTED",
            lines
        ));
    }

    @Transactional(readOnly = true)
    public List<PortalResponses.AttachmentMeta> getAttachments(UUID patientId, UUID encounterId) {
        ClinicalEncounter encounter = encounterRepo.findById(encounterId)
            .orElseThrow(() -> new ResourceNotFoundException("ClinicalEncounter", encounterId));
        if (!patientId.equals(encounter.getPatientId())) {
            throw new BusinessRuleViolationException("Encounter does not belong to this patient");
        }

        List<Attachment> list = attachmentRepo.findByEncounterIdOrderByCreatedAtDesc(encounterId);
        return list.stream().map(a -> new PortalResponses.AttachmentMeta(
            a.getId(),
            a.getFileName(),
            a.getContentType(),
            a.getCategory(),
            null,
            a.getCreatedAt()
        )).toList();
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
}
