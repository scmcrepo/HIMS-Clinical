package com.hms.application.visit;

import com.hms.domain.visit.model.*;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.visit.VisitJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VisitService {

    private final VisitJpaRepository visitRepo;

    /**
     * Creates an OP visit.
     * visitType ALWAYS forced to OP — IP visits are created only by BedAllocationService.
     */
    @Transactional
    public Visit createOpVisit(UUID patientId, UUID consultantId, UUID appointmentId, VisitMode mode) {
        if (patientId == null)    throw new BusinessRuleViolationException("patientId is required");
        if (consultantId == null) throw new BusinessRuleViolationException("consultantId is required");

        Visit visit = new Visit();
        visit.setPatientId(patientId);
        visit.setConsultantId(consultantId);
        visit.setAppointmentId(appointmentId);
        visit.setVisitType(VisitType.OP);     // ALWAYS OP via this path
        visit.setVisitDate(LocalDate.now());
        visit.setCheckedTime(LocalTime.now());
        visit.setVisitMode(mode != null ? mode : VisitMode.WALK_IN);
        visit.setVisitStatus(VisitStatus.CHECKEDIN);
        return visitRepo.save(visit);
    }

    /**
     * Creates an IP visit — called ONLY by BedAllocationService.
     */
    @Transactional
    public Visit createIpVisit(UUID patientId, UUID consultantId, LocalDate admissionDate, UUID bedId) {
        Visit visit = new Visit();
        visit.setPatientId(patientId);
        visit.setConsultantId(consultantId);
        visit.setVisitType(VisitType.IP);
        visit.setVisitDate(admissionDate != null ? admissionDate : LocalDate.now());
        visit.setCheckedTime(LocalTime.now());
        visit.setVisitStatus(VisitStatus.CHECKEDIN);
        visit.setVisitMode(VisitMode.WALK_IN);
        visit.setBedStatus(true);
        if (bedId != null) visit.setLastBedId(bedId);
        return visitRepo.save(visit);
    }

    @Transactional
    public Visit updateVisit(Visit req) {
        Visit existing = findOrThrow(req.getId());
        // If appointment cleared → set to null to prevent orphan FK
        if (req.getAppointmentId() == null) existing.setAppointmentId(null);
        if (req.getDiagnosis() != null) existing.setDiagnosis(req.getDiagnosis());
        if (req.getVisitStatus() != null) existing.setVisitStatus(req.getVisitStatus());
        return visitRepo.save(existing);
    }

    /** Called by DiagnosticService when first OP diagnostic order is placed */
    @Transactional
    public void stampCasesheetDate(UUID visitId) {
        Visit visit = findOrThrow(visitId);
        visit.stampCasesheetDate();
        visitRepo.save(visit);
    }

    /** Called by BillService.generateBill() to clear bill flag after IP bill finalised */
    @Transactional
    public void clearBillStatus(UUID visitId) {
        Visit visit = findOrThrow(visitId);
        visit.setBillStatus(false);
        visitRepo.save(visit);
    }

    /** Called by BillService when a draft IP bill is created */
    @Transactional
    public void markBillDraft(UUID visitId) {
        Visit visit = findOrThrow(visitId);
        visit.setBillStatus(true);
        visitRepo.save(visit);
    }

    @Transactional(readOnly = true)
    public Optional<Visit> getActiveIpVisit(UUID patientId) {
        return visitRepo.findActiveIPVisit(patientId);
    }

    @Transactional(readOnly = true)
    public Visit getById(UUID id) { return findOrThrow(id); }

    @Transactional(readOnly = true)
    public List<Visit> getByPatient(UUID patientId) {
        return visitRepo.findByPatientId(patientId);
    }

    @Transactional(readOnly = true)
    public List<Visit> getByDate(LocalDate date) {
        return visitRepo.findByDate(date);
    }

    @Transactional(readOnly = true)
    public Optional<Visit> getByBillId(UUID billId) {
        return visitRepo.findByBillId(billId);
    }

    @Transactional(readOnly = true)
    public long countForDate(UUID patientId, UUID consultantId, LocalDate date) {
        return visitRepo.countByPatientConsultantDate(patientId, consultantId, date);
    }

    @Transactional(readOnly = true)
    public List<Visit> getByTypeAndDate(VisitType type, LocalDate date, int start, int limit) {
        return visitRepo.findByTypeAndDate(type, date, PageRequest.of(start / limit, limit)).getContent();
    }

    @Transactional(readOnly = true)
    public Optional<Visit> getByPatientAndDischargeDate(UUID patientId, LocalDate dischargeDate) {
        return visitRepo.findByPatientAndDischargeDate(patientId, dischargeDate);
    }

    private Visit findOrThrow(UUID id) {
        return visitRepo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Visit", id));
    }
}
