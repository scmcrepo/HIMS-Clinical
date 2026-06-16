package com.hms.application.appointment;

import com.hms.api.appointment.request.BookAppointmentRequest;
import com.hms.api.appointment.request.RescheduleAppointmentRequest;
import com.hms.api.appointment.response.AppointmentResponse;
import com.hms.api.appointment.response.SlotAvailabilityResponse;
import com.hms.application.encounter.EncounterManagementService;
import com.hms.api.encounter.request.CreateEncounterRequest;
import com.hms.domain.appointment.model.Appointment;
import com.hms.domain.appointment.model.AppointmentSlot;
import com.hms.domain.encounter.model.VisitMode;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.appointment.AppointmentJpaRepository;
import com.hms.infrastructure.persistence.appointment.AppointmentSlotJpaRepository;
import com.hms.infrastructure.mapper.AppointmentMapper;
import com.hms.infrastructure.persistence.consultant.ConsultantJpaRepository;
import com.hms.infrastructure.persistence.patient.PatientJpaRepository;
import com.hms.infrastructure.sequence.NumberSequenceJpaRepository;
import com.hms.infrastructure.sequence.NumberSequenceEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AppointmentSchedulingService {

    private final AppointmentJpaRepository appointmentRepo;
    private final AppointmentSlotJpaRepository slotRepo;
    private final PatientJpaRepository patientRepo;
    private final ConsultantJpaRepository consultantRepo;
    private final NumberSequenceJpaRepository numberSequenceRepo;
    private final EncounterManagementService encounterService;
    private final AppointmentMapper appointmentMapper;

    private String resolvePatientName(Appointment a) {
        if (a.getPatientId() == null) {
            if (a.getTempPatientName() == null) return "Walk-in";
            String salutation = a.getTempPatientSalutation();
            return (salutation != null && !salutation.isEmpty() ? salutation + " " : "") + a.getTempPatientName();
        }
        return patientRepo.findById(a.getPatientId())
            .map(com.hms.domain.patient.model.Patient::computeFullName)
            .orElse("Unknown Patient");
    }

    private String resolvePatientNumber(UUID patientId) {
        if (patientId == null) return "N/A";
        return numberSequenceRepo.findById(patientId)
            .map(NumberSequenceEntity::getValue)
            .orElse("NEW");
    }

    private String resolveProviderName(UUID providerId) {
        if (providerId == null) return "Unknown";
        return consultantRepo.findById(providerId)
            .map(c -> c.getSalutation() + " " + c.getFirstName() + " " + c.getLastName())
            .orElse("Unknown Consultant");
    }

    private String resolvePatientPhone(Appointment a) {
        if (a.getPatientId() == null) return a.getTempPatientPhone() != null ? a.getTempPatientPhone() : "—";
        return patientRepo.findById(a.getPatientId())
            .map(com.hms.domain.patient.model.Patient::getContactNumber)
            .orElse("—");
    }

    private java.time.LocalTime resolveSlotEndTime(UUID slotId) {
        if (slotId == null) return null;
        return slotRepo.findById(slotId)
            .map(s -> java.time.LocalTime.parse(s.getToTime()))
            .orElse(null);
    }

    @Transactional
    public AppointmentResponse bookAppointment(BookAppointmentRequest req) {
        // Validate slot exists and belongs to provider
        AppointmentSlot slot = slotRepo.findById(req.slotId())
            .orElseThrow(() -> new ResourceNotFoundException("AppointmentSlot", req.slotId()));

        if (!slot.getConsultantId().equals(req.providerId())) {
            throw new BusinessRuleViolationException(
                "Slot does not belong to the specified provider");
        }

        // Validate the requested date falls on the correct day of week
        DayOfWeek requestedDay = req.appointmentDate().getDayOfWeek();
        // slot.dayOfWeek: 0=MON matches DayOfWeek.MONDAY.getValue()-1
        int slotDay = slot.getDayOfWeek().ordinal();
        if (requestedDay.getValue() - 1 != slotDay) {
            throw new BusinessRuleViolationException(
                "Appointment date " + req.appointmentDate() + " does not fall on the slot's day of week");
        }

        // Check capacity
        long booked = appointmentRepo.countBookedForSlotAndDate(req.slotId(), req.appointmentDate());
        if (booked >= slot.getMaxPatients()) {
            throw new BusinessRuleViolationException(
                "Slot is fully booked for " + req.appointmentDate() +
                " (max: " + slot.getMaxPatients() + ")");
        }

        Appointment appointment = new Appointment();
        appointment.setPatientId(req.patientId());
        appointment.setProviderId(req.providerId());
        appointment.setSlotId(req.slotId());
        appointment.setAppointmentDate(req.appointmentDate());
        appointment.setAppointmentTime(java.time.LocalTime.parse(slot.getFromTime()));
        appointment.setVisitMode(VisitMode.APPOINTMENT);
        appointment.setNotes(req.notes());
        appointment.setTempPatientName(req.tempPatientName());
        appointment.setTempPatientSalutation(req.tempPatientSalutation());
        appointment.setTempPatientGender(req.tempPatientGender());
        appointment.setTempPatientPhone(req.tempPatientPhone());
        appointment.setTempPatientAge(req.tempPatientAge());

        Appointment saved = appointmentRepo.save(appointment);
        return appointmentMapper.toResponse(saved, resolvePatientName(saved), resolvePatientNumber(saved.getPatientId()), resolvePatientPhone(saved), resolveProviderName(saved.getProviderId()), resolveSlotEndTime(saved.getSlotId()), (int) booked + 1, slot.getMaxPatients());
    }

    @Transactional
    public AppointmentResponse reschedule(UUID appointmentId, RescheduleAppointmentRequest req) {
        Appointment appointment = appointmentRepo.findById(appointmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Appointment", appointmentId));

        appointment.reschedule(req.newDate(), req.newTime());

        // Re-validate capacity if slot changes
        if (req.newSlotId() != null && !req.newSlotId().equals(appointment.getSlotId())) {
            long booked = appointmentRepo.countBookedForSlotAndDate(req.newSlotId(), req.newDate());
            AppointmentSlot newSlot = slotRepo.findById(req.newSlotId())
                .orElseThrow(() -> new ResourceNotFoundException("AppointmentSlot", req.newSlotId()));
            if (booked >= newSlot.getMaxPatients()) {
                throw new BusinessRuleViolationException("New slot is fully booked");
            }
            appointment.setSlotId(req.newSlotId());
            appointment.setAppointmentTime(java.time.LocalTime.parse(newSlot.getFromTime()));
        }

        Appointment saved = appointmentRepo.save(appointment);
        return appointmentMapper.toResponse(saved, resolvePatientName(saved), resolvePatientNumber(saved.getPatientId()), resolvePatientPhone(saved), resolveProviderName(saved.getProviderId()), resolveSlotEndTime(saved.getSlotId()), 0, 0);
    }

    @Transactional
    public AppointmentResponse linkPatient(UUID appointmentId, UUID patientId) {
        Appointment appointment = appointmentRepo.findById(appointmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Appointment", appointmentId));
        appointment.setPatientId(patientId);
        Appointment saved = appointmentRepo.save(appointment);
        return appointmentMapper.toResponse(saved, resolvePatientName(saved), resolvePatientNumber(saved.getPatientId()), resolvePatientPhone(saved), resolveProviderName(saved.getProviderId()), resolveSlotEndTime(saved.getSlotId()), 0, 0);
    }

    @Transactional
    public AppointmentResponse checkIn(UUID appointmentId) {
        Appointment appointment = appointmentRepo.findById(appointmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Appointment", appointmentId));

        appointment.checkIn();
        appointmentRepo.save(appointment);

        // Create outpatient encounter from appointment
        var encounterCmd = new CreateEncounterRequest(
            appointment.getPatientId(),
            appointment.getProviderId(),
            appointmentId,
            VisitMode.APPOINTMENT
        );
        encounterService.createOutpatientEncounter(encounterCmd);

        return appointmentMapper.toResponse(appointment, resolvePatientName(appointment), resolvePatientNumber(appointment.getPatientId()), resolvePatientPhone(appointment), resolveProviderName(appointment.getProviderId()), resolveSlotEndTime(appointment.getSlotId()), 0, 0);
    }

    @Transactional
    public AppointmentResponse cancel(UUID appointmentId) {
        Appointment appointment = appointmentRepo.findById(appointmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Appointment", appointmentId));
        appointment.cancel();
        Appointment saved = appointmentRepo.save(appointment);
        return appointmentMapper.toResponse(saved, resolvePatientName(saved), resolvePatientNumber(saved.getPatientId()), resolvePatientPhone(saved), resolveProviderName(saved.getProviderId()), resolveSlotEndTime(saved.getSlotId()), 0, 0);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getByProviderAndDate(UUID providerId, LocalDate date) {
        List<Appointment> appointments;
        if (providerId == null || providerId.equals(UUID.fromString("00000000-0000-0000-0000-000000000000"))) {
            appointments = appointmentRepo.findByDate(date);
        } else {
            appointments = appointmentRepo.findByProviderAndDate(providerId, date);
        }
        
        return appointments.stream()
            .map(a -> appointmentMapper.toResponse(a, resolvePatientName(a), resolvePatientNumber(a.getPatientId()), resolvePatientPhone(a), resolveProviderName(a.getProviderId()), resolveSlotEndTime(a.getSlotId()), 0, 0))
            .toList();
    }

    @Transactional(readOnly = true)
    public Page<AppointmentResponse> getByPatient(UUID patientId, Pageable pageable) {
        return appointmentRepo.findByPatientId(patientId, pageable)
            .map(a -> appointmentMapper.toResponse(a, resolvePatientName(a), resolvePatientNumber(a.getPatientId()), resolvePatientPhone(a), resolveProviderName(a.getProviderId()), resolveSlotEndTime(a.getSlotId()), 0, 0));
    }

    @Transactional(readOnly = true)
    public List<SlotAvailabilityResponse> getSlotAvailability(UUID providerId, LocalDate date) {
        // Find day of week for requested date (0=MON)
        int dow = date.getDayOfWeek().getValue() - 1;
        List<AppointmentSlot> slots = slotRepo.findActiveByProviderAndDay(providerId,
            com.hms.domain.appointment.model.DayOfWeekEnum.values()[dow]);

        return slots.stream().map(slot -> {
            long booked = appointmentRepo.countBookedForSlotAndDate(slot.getId(), date);
            int available = slot.getMaxPatients() - (int) booked;
            return new SlotAvailabilityResponse(
                slot.getId(),
                java.time.LocalTime.parse(slot.getFromTime()),
                java.time.LocalTime.parse(slot.getToTime()),
                slot.getMaxPatients(),
                (int) booked,
                Math.max(0, available),
                available > 0
            );
        }).toList();
    }

    @Transactional(readOnly = true)
    public AppointmentResponse getById(UUID appointmentId) {
        Appointment a = appointmentRepo.findById(appointmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Appointment", appointmentId));
        return appointmentMapper.toResponse(a, resolvePatientName(a), resolvePatientNumber(a.getPatientId()), resolvePatientPhone(a), resolveProviderName(a.getProviderId()), resolveSlotEndTime(a.getSlotId()), 0, 0);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getByPatientId(UUID patientId) {
        return appointmentRepo.findByPatientIdOrderByDateDesc(patientId).stream()
            .map(a -> appointmentMapper.toResponse(a, resolvePatientName(a), resolvePatientNumber(a.getPatientId()), resolvePatientPhone(a), resolveProviderName(a.getProviderId()), resolveSlotEndTime(a.getSlotId()), 0, 0)).toList();
    }
}
