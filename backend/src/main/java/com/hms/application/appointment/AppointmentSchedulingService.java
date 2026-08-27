package com.hms.application.appointment;

import com.hms.api.appointment.request.BookAppointmentRequest;
import com.hms.api.appointment.request.RescheduleAppointmentRequest;
import com.hms.api.appointment.response.AppointmentResponse;
import com.hms.api.appointment.response.SlotAvailabilityResponse;
import com.hms.api.appointment.response.AvailabilityCheckResponse;
import com.hms.application.encounter.EncounterManagementService;
import com.hms.api.encounter.request.CreateEncounterRequest;
import com.hms.domain.appointment.model.Appointment;
import com.hms.domain.appointment.model.AppointmentSlot;
import com.hms.domain.encounter.model.VisitMode;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.appointment.AppointmentJpaRepository;
import com.hms.infrastructure.persistence.encounter.ClinicalEncounterJpaRepository;
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
    private final ClinicalEncounterJpaRepository encounterRepo;
    private final com.hms.infrastructure.persistence.appointment.ConsultantLeaveJpaRepository consultantLeaveRepo;

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
            .map(s -> parseTimeSafely(s.getToTime()))
            .orElse(null);
    }

    @Transactional
    public AppointmentResponse bookAppointment(BookAppointmentRequest req) {
        // Block booking if provider has active leave on requested date
        if (!consultantLeaveRepo.findActiveByConsultantAndDate(req.providerId(), req.appointmentDate()).isEmpty()) {
            throw new BusinessRuleViolationException("Doctor is unavailable on this date.");
        }

        // Block booking if patient already has an active IP encounter
        if (req.patientId() != null && !encounterRepo.findActiveInpatientByPatientId(req.patientId()).isEmpty()) {
            throw new BusinessRuleViolationException(
                "Patient already has an active Inpatient (IP) encounter. " +
                "Cannot book an appointment while the patient is admitted. " +
                "Please discharge the patient first.");
        }

        // Block duplicate booking for the same patient, slot, and date
        if (req.patientId() != null && appointmentRepo.countByPatientAndSlotAndDate(req.patientId(), req.slotId(), req.appointmentDate()) > 0) {
            throw new BusinessRuleViolationException(
                "You have already booked an appointment for this time slot on " + req.appointmentDate() +
                ". Duplicate bookings for the same slot on the same date are not allowed.");
        }

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
        appointment.setAppointmentTime(parseTimeSafely(slot.getFromTime()));
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

    private java.time.LocalTime parseTimeSafely(String timeStr) {
        if (timeStr == null || timeStr.isBlank()) return java.time.LocalTime.of(9, 0);
        String s = timeStr.trim();
        try {
            if (s.contains(" ")) {
                java.time.format.DateTimeFormatter fmt = new java.time.format.DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .appendPattern("[hh:mm a][h:mm a][hh:mm:ss a][h:mm:ss a]")
                    .toFormatter(java.util.Locale.ENGLISH);
                return java.time.LocalTime.parse(s, fmt);
            }
            if (s.length() == 5) return java.time.LocalTime.parse(s);
            if (s.length() == 8) return java.time.LocalTime.parse(s);
            return java.time.LocalTime.parse(s.substring(0, 5));
        } catch (Exception e) {
            try {
                String clean = s.replaceAll("[^0-9:]", "");
                if (clean.length() >= 4) {
                    String[] parts = clean.split(":");
                    int h = Integer.parseInt(parts[0]);
                    int m = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                    return java.time.LocalTime.of(h, m);
                }
            } catch (Exception ignored) {}
            return java.time.LocalTime.of(9, 0);
        }
    }

    @Transactional
    public AppointmentResponse reschedule(UUID appointmentId, RescheduleAppointmentRequest req) {
        Appointment oldAppointment = appointmentRepo.findById(appointmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Appointment", appointmentId));

        // Block rescheduling if provider has active leave on the new date
        if (!consultantLeaveRepo.findActiveByConsultantAndDate(oldAppointment.getProviderId(), req.newDate()).isEmpty()) {
            throw new BusinessRuleViolationException("Doctor is unavailable on this date.");
        }

        if (oldAppointment.isCancelled()) {
            throw new BusinessRuleViolationException("Cannot reschedule a cancelled appointment");
        }
        if (oldAppointment.isCheckedIn()) {
            throw new BusinessRuleViolationException("Cannot reschedule — patient has already checked in");
        }

        UUID newSlotId = req.newSlotId() != null ? req.newSlotId() : oldAppointment.getSlotId();
        if (newSlotId == null) {
            throw new BusinessRuleViolationException("No slot is available for reschedule");
        }
        AppointmentSlot newSlot = slotRepo.findById(newSlotId)
            .orElseThrow(() -> new ResourceNotFoundException("AppointmentSlot", newSlotId));

        if (oldAppointment.getAppointmentDate().equals(req.newDate()) && newSlotId.equals(oldAppointment.getSlotId())) {
            throw new BusinessRuleViolationException("Cannot reschedule to the same date and slot");
        }

        if (oldAppointment.getPatientId() != null && appointmentRepo.countByPatientAndSlotAndDate(oldAppointment.getPatientId(), newSlotId, req.newDate()) > 0) {
            throw new BusinessRuleViolationException("You already have an active appointment for this slot on " + req.newDate() + ".");
        }

        // Always mark old appointment as RESCHEDULED and create a new BOOKED appointment
        oldAppointment.reschedule();

        long booked = appointmentRepo.countBookedForSlotAndDate(newSlotId, req.newDate());
        if (booked >= newSlot.getMaxPatients()) {
            throw new BusinessRuleViolationException("New slot is fully booked");
        }

        // Save old appointment marked as RESCHEDULED (keeps original date/time for history)
        appointmentRepo.save(oldAppointment);

        // Create new appointment on the new date/time/slot
        Appointment newAppointment = new Appointment();
        newAppointment.setPatientId(oldAppointment.getPatientId());
        newAppointment.setProviderId(oldAppointment.getProviderId());
        newAppointment.setSlotId(newSlotId);
        newAppointment.setAppointmentStatus(com.hms.domain.appointment.model.AppointmentStatus.BOOKED);
        newAppointment.setAppointmentDate(req.newDate());
        newAppointment.setAppointmentTime(parseTimeSafely(newSlot.getFromTime()));
        newAppointment.setVisitMode(oldAppointment.getVisitMode());
        newAppointment.setNotes(oldAppointment.getNotes());
        newAppointment.setTempPatientName(oldAppointment.getTempPatientName());
        newAppointment.setTempPatientSalutation(oldAppointment.getTempPatientSalutation());
        newAppointment.setTempPatientGender(oldAppointment.getTempPatientGender());
        newAppointment.setTempPatientPhone(oldAppointment.getTempPatientPhone());
        newAppointment.setTempPatientAge(oldAppointment.getTempPatientAge());

        Appointment savedNew = appointmentRepo.save(newAppointment);

        return appointmentMapper.toResponse(
            savedNew,
            resolvePatientName(savedNew),
            resolvePatientNumber(savedNew.getPatientId()),
            resolvePatientPhone(savedNew),
            resolveProviderName(savedNew.getProviderId()),
            resolveSlotEndTime(savedNew.getSlotId()),
            (int) booked + 1,
            newSlot.getMaxPatients()
        );
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

        // Block check-in if patient already has an active IP encounter
        if (appointment.getPatientId() != null && !encounterRepo.findActiveInpatientByPatientId(appointment.getPatientId()).isEmpty()) {
            throw new BusinessRuleViolationException(
                "Patient already has an active Inpatient (IP) encounter. " +
                "Cannot check in for an OP appointment while the patient is admitted. " +
                "Please discharge the patient first.");
        }

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
    public List<AppointmentResponse> getByProviderAndDateRange(UUID providerId, LocalDate from, LocalDate to) {
        return appointmentRepo.findByProviderAndDateRange(providerId, from, to).stream()
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
        // If doctor is on leave on this date, return no slots
        if (!consultantLeaveRepo.findActiveByConsultantAndDate(providerId, date).isEmpty()) {
            return List.of();
        }

        // 1. Check if there are date-specific slots configured for this exact date
        List<AppointmentSlot> specificSlots = slotRepo.findSpecificDateSlots(providerId, date);
        List<AppointmentSlot> slots;
        if (!specificSlots.isEmpty()) {
            slots = specificSlots;
        } else {
            // 2. Fall back to recurring slots for the day of week within validity period
            int dow = date.getDayOfWeek().getValue() - 1;
            slots = slotRepo.findActiveRecurringSlots(providerId,
                com.hms.domain.appointment.model.DayOfWeekEnum.values()[dow], date);
        }

        return slots.stream().map(slot -> {
            long booked = appointmentRepo.countBookedForSlotAndDate(slot.getId(), date);
            int available = slot.getMaxPatients() - (int) booked;
            return new SlotAvailabilityResponse(
                slot.getId(),
                parseTimeSafely(slot.getFromTime()),
                parseTimeSafely(slot.getToTime()),
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

    /**
     * Enhanced slot availability check that returns a reason when slots are empty.
     * Used by the admin booking page to show contextual warnings.
     */
    @Transactional(readOnly = true)
    public AvailabilityCheckResponse getSlotAvailabilityCheck(UUID providerId, LocalDate date) {
        String dayOfWeek = date.getDayOfWeek().name(); // e.g. "MONDAY"

        // Check leave first
        if (!consultantLeaveRepo.findActiveByConsultantAndDate(providerId, date).isEmpty()) {
            return new AvailabilityCheckResponse(List.of(), "ON_LEAVE", dayOfWeek);
        }

        // 1. Check if there are date-specific slots configured for this exact date
        List<AppointmentSlot> specificSlots = slotRepo.findSpecificDateSlots(providerId, date);
        List<AppointmentSlot> slots;
        if (!specificSlots.isEmpty()) {
            slots = specificSlots;
        } else {
            // 2. Fall back to recurring slots for the day of week within validity period
            int dow = date.getDayOfWeek().getValue() - 1;
            slots = slotRepo.findActiveRecurringSlots(providerId,
                com.hms.domain.appointment.model.DayOfWeekEnum.values()[dow], date);
        }

        if (slots.isEmpty()) {
            return new AvailabilityCheckResponse(List.of(), "NO_SLOTS", dayOfWeek);
        }

        List<SlotAvailabilityResponse> slotResponses = slots.stream().map(slot -> {
            long booked = appointmentRepo.countBookedForSlotAndDate(slot.getId(), date);
            int available = slot.getMaxPatients() - (int) booked;
            return new SlotAvailabilityResponse(
                slot.getId(),
                parseTimeSafely(slot.getFromTime()),
                parseTimeSafely(slot.getToTime()),
                slot.getMaxPatients(),
                (int) booked,
                Math.max(0, available),
                available > 0
            );
        }).toList();

        return new AvailabilityCheckResponse(slotResponses, null, dayOfWeek);
    }
}
