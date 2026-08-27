package com.hms.application.appointment;

import com.hms.api.appointment.request.RescheduleAppointmentRequest;
import com.hms.api.appointment.response.AppointmentResponse;
import com.hms.domain.appointment.model.Appointment;
import com.hms.domain.appointment.model.AppointmentSlot;
import com.hms.domain.appointment.model.AppointmentStatus;
import com.hms.domain.consultant.model.Consultant;
import com.hms.domain.patient.model.Patient;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.infrastructure.mapper.AppointmentMapper;
import com.hms.infrastructure.persistence.appointment.AppointmentJpaRepository;
import com.hms.infrastructure.persistence.appointment.AppointmentSlotJpaRepository;
import com.hms.infrastructure.persistence.consultant.ConsultantJpaRepository;
import com.hms.infrastructure.persistence.patient.PatientJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentSchedulingServiceTest {

    @Mock private AppointmentJpaRepository appointmentRepo;
    @Mock private AppointmentSlotJpaRepository slotRepo;
    @Mock private PatientJpaRepository patientRepo;
    @Mock private ConsultantJpaRepository consultantRepo;
    @Mock private com.hms.infrastructure.sequence.NumberSequenceJpaRepository numberSequenceRepo;
    @Mock private com.hms.application.encounter.EncounterManagementService encounterService;
    @Mock private AppointmentMapper appointmentMapper;
    @Mock private com.hms.infrastructure.persistence.appointment.ConsultantLeaveJpaRepository consultantLeaveRepo;

    @InjectMocks
    private AppointmentSchedulingService service;

    @Test
    void testReschedule_Success() {
        UUID appointmentId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        UUID oldSlotId = UUID.randomUUID();
        UUID newSlotId = UUID.randomUUID();

        Appointment oldAppointment = new Appointment();
        oldAppointment.setId(appointmentId);
        oldAppointment.setPatientId(patientId);
        oldAppointment.setProviderId(providerId);
        oldAppointment.setSlotId(oldSlotId);
        oldAppointment.setAppointmentStatus(AppointmentStatus.BOOKED);
        oldAppointment.setAppointmentDate(LocalDate.of(2026, 7, 4));
        oldAppointment.setAppointmentTime(LocalTime.of(10, 0));
        oldAppointment.setTempPatientName("John Doe");

        RescheduleAppointmentRequest req = new RescheduleAppointmentRequest(
                LocalDate.of(2026, 7, 5),
                LocalTime.of(11, 0),
                newSlotId
        );

        AppointmentSlot newSlot = new AppointmentSlot();
        newSlot.setId(newSlotId);
        newSlot.setFromTime("11:00");
        newSlot.setToTime("11:30");
        newSlot.setMaxPatients(10);

        Patient patient = new Patient();
        patient.setFirstName("John");
        patient.setLastName("Doe");

        Consultant consultant = new Consultant();
        consultant.setSalutation("Dr.");
        consultant.setFirstName("House");
        consultant.setLastName("M.D.");

        when(appointmentRepo.findById(appointmentId)).thenReturn(Optional.of(oldAppointment));
        when(slotRepo.findById(newSlotId)).thenReturn(Optional.of(newSlot));
        when(appointmentRepo.countBookedForSlotAndDate(newSlotId, req.newDate())).thenReturn(2L);
        when(patientRepo.findById(patientId)).thenReturn(Optional.of(patient));
        when(consultantRepo.findById(providerId)).thenReturn(Optional.of(consultant));

        when(appointmentRepo.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AppointmentResponse mockResponse = mock(AppointmentResponse.class);

        when(appointmentMapper.toResponse(any(Appointment.class), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(mockResponse);

        AppointmentResponse response = service.reschedule(appointmentId, req);

        assertNotNull(response);

        // Verify old appointment was updated to RESCHEDULED, but date/time remain original
        assertEquals(AppointmentStatus.RESCHEDULED, oldAppointment.getAppointmentStatus());
        assertEquals(LocalDate.of(2026, 7, 4), oldAppointment.getAppointmentDate());
        assertEquals(LocalTime.of(10, 0), oldAppointment.getAppointmentTime());

        // Capture saved appointments to check new appointment creation
        ArgumentCaptor<Appointment> captor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepo, times(2)).save(captor.capture());

        Appointment firstSaved = captor.getAllValues().get(0);
        Appointment secondSaved = captor.getAllValues().get(1);

        assertEquals(oldAppointment, firstSaved);

        assertEquals(patientId, secondSaved.getPatientId());
        assertEquals(providerId, secondSaved.getProviderId());
        assertEquals(newSlotId, secondSaved.getSlotId());
        assertEquals(AppointmentStatus.BOOKED, secondSaved.getAppointmentStatus());
        assertEquals(LocalDate.of(2026, 7, 5), secondSaved.getAppointmentDate());
        assertEquals(LocalTime.of(11, 0), secondSaved.getAppointmentTime());
        assertEquals("John Doe", secondSaved.getTempPatientName());
    }

    @Test
    void testReschedule_SameDate_Success() {
        UUID appointmentId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        UUID oldSlotId = UUID.randomUUID();
        UUID newSlotId = UUID.randomUUID();

        Appointment appointment = new Appointment();
        appointment.setId(appointmentId);
        appointment.setPatientId(patientId);
        appointment.setProviderId(providerId);
        appointment.setSlotId(oldSlotId);
        appointment.setAppointmentStatus(AppointmentStatus.BOOKED);
        appointment.setAppointmentDate(LocalDate.of(2026, 7, 4));
        appointment.setAppointmentTime(LocalTime.of(10, 0));
        appointment.setTempPatientName("John Doe");

        RescheduleAppointmentRequest req = new RescheduleAppointmentRequest(
                LocalDate.of(2026, 7, 4),
                LocalTime.of(11, 0),
                newSlotId
        );

        AppointmentSlot newSlot = new AppointmentSlot();
        newSlot.setId(newSlotId);
        newSlot.setFromTime("11:00");
        newSlot.setToTime("11:30");
        newSlot.setMaxPatients(10);

        Patient patient = new Patient();
        patient.setFirstName("John");
        patient.setLastName("Doe");

        Consultant consultant = new Consultant();
        consultant.setSalutation("Dr.");
        consultant.setFirstName("House");
        consultant.setLastName("M.D.");

        when(appointmentRepo.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(slotRepo.findById(newSlotId)).thenReturn(Optional.of(newSlot));
        when(appointmentRepo.countBookedForSlotAndDate(newSlotId, req.newDate())).thenReturn(2L);
        when(patientRepo.findById(patientId)).thenReturn(Optional.of(patient));
        when(consultantRepo.findById(providerId)).thenReturn(Optional.of(consultant));
        when(appointmentRepo.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AppointmentResponse mockResponse = mock(AppointmentResponse.class);
        when(appointmentMapper.toResponse(any(Appointment.class), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(mockResponse);

        AppointmentResponse response = service.reschedule(appointmentId, req);

        assertNotNull(response);

        // Verify old appointment was updated to RESCHEDULED
        assertEquals(AppointmentStatus.RESCHEDULED, appointment.getAppointmentStatus());
        assertEquals(LocalDate.of(2026, 7, 4), appointment.getAppointmentDate());
        assertEquals(LocalTime.of(10, 0), appointment.getAppointmentTime());

        // Capture saved appointments to check new appointment creation
        ArgumentCaptor<Appointment> captor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepo, times(2)).save(captor.capture());

        Appointment firstSaved = captor.getAllValues().get(0);
        Appointment secondSaved = captor.getAllValues().get(1);

        assertEquals(appointment, firstSaved);
        assertEquals(AppointmentStatus.BOOKED, secondSaved.getAppointmentStatus());
        assertEquals(LocalDate.of(2026, 7, 4), secondSaved.getAppointmentDate());
        assertEquals(LocalTime.of(11, 0), secondSaved.getAppointmentTime());
        assertEquals(newSlotId, secondSaved.getSlotId());
    }

    @Test
    void testReschedule_SameDateAndSlot_Failure() {
        UUID appointmentId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();

        Appointment appointment = new Appointment();
        appointment.setId(appointmentId);
        appointment.setPatientId(patientId);
        appointment.setProviderId(providerId);
        appointment.setSlotId(slotId);
        appointment.setAppointmentStatus(AppointmentStatus.BOOKED);
        appointment.setAppointmentDate(LocalDate.of(2026, 7, 4));
        appointment.setAppointmentTime(LocalTime.of(10, 0));

        RescheduleAppointmentRequest req = new RescheduleAppointmentRequest(
                LocalDate.of(2026, 7, 4),
                LocalTime.of(10, 0),
                slotId
        );

        AppointmentSlot slot = new AppointmentSlot();
        slot.setId(slotId);
        slot.setFromTime("10:00");
        slot.setToTime("10:30");

        when(appointmentRepo.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(slotRepo.findById(slotId)).thenReturn(Optional.of(slot));

        assertThrows(BusinessRuleViolationException.class, () -> {
            service.reschedule(appointmentId, req);
        });
    }

    @Test
    void testBookAppointment_DoctorOnLeave_ThrowsException() {
        UUID providerId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 8, 28);
        
        com.hms.api.appointment.request.BookAppointmentRequest req = new com.hms.api.appointment.request.BookAppointmentRequest(
            UUID.randomUUID(), providerId, slotId, date, "notes", null, null, null, null, null
        );
        
        com.hms.domain.appointment.model.ConsultantLeave leave = new com.hms.domain.appointment.model.ConsultantLeave();
        leave.setConsultantId(providerId);
        leave.setStartDate(date);
        leave.setEndDate(date);
        
        when(consultantLeaveRepo.findActiveByConsultantAndDate(providerId, date)).thenReturn(java.util.List.of(leave));
        
        assertThrows(BusinessRuleViolationException.class, () -> {
            service.bookAppointment(req);
        });
    }

    @Test
    void testReschedule_DoctorOnLeave_ThrowsException() {
        UUID appointmentId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        
        Appointment appointment = new Appointment();
        appointment.setId(appointmentId);
        appointment.setPatientId(patientId);
        appointment.setProviderId(providerId);
        appointment.setSlotId(slotId);
        appointment.setAppointmentStatus(AppointmentStatus.BOOKED);
        appointment.setAppointmentDate(LocalDate.of(2026, 7, 4));
        appointment.setAppointmentTime(LocalTime.of(10, 0));
        
        RescheduleAppointmentRequest req = new RescheduleAppointmentRequest(
                LocalDate.of(2026, 8, 28),
                LocalTime.of(11, 0),
                slotId
        );
        
        com.hms.domain.appointment.model.ConsultantLeave leave = new com.hms.domain.appointment.model.ConsultantLeave();
        leave.setConsultantId(providerId);
        leave.setStartDate(LocalDate.of(2026, 8, 28));
        leave.setEndDate(LocalDate.of(2026, 8, 29));
        
        when(appointmentRepo.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(consultantLeaveRepo.findActiveByConsultantAndDate(providerId, req.newDate())).thenReturn(java.util.List.of(leave));
        
        assertThrows(BusinessRuleViolationException.class, () -> {
            service.reschedule(appointmentId, req);
        });
    }

    @Test
    void testGetSlotAvailability_DoctorOnLeave_ReturnsEmpty() {
        UUID providerId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 8, 28);
        
        com.hms.domain.appointment.model.ConsultantLeave leave = new com.hms.domain.appointment.model.ConsultantLeave();
        leave.setConsultantId(providerId);
        leave.setStartDate(date);
        leave.setEndDate(date);
        
        when(consultantLeaveRepo.findActiveByConsultantAndDate(providerId, date)).thenReturn(java.util.List.of(leave));
        
        java.util.List<com.hms.api.appointment.response.SlotAvailabilityResponse> availability = service.getSlotAvailability(providerId, date);
        
        assertNotNull(availability);
        assertTrue(availability.isEmpty());
    }

    @Test
    void testGetSlotAvailability_DateSpecificSlot_TakesPriorityOverRecurring() {
        UUID providerId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 8, 14); // Friday
        
        AppointmentSlot dateSlot = new AppointmentSlot();
        dateSlot.setId(UUID.randomUUID());
        dateSlot.setConsultantId(providerId);
        dateSlot.setSpecificDate(date);
        dateSlot.setFromTime("06:00");
        dateSlot.setToTime("08:00");
        dateSlot.setMaxPatients(10);
        
        when(consultantLeaveRepo.findActiveByConsultantAndDate(providerId, date)).thenReturn(java.util.List.of());
        when(slotRepo.findSpecificDateSlots(providerId, date)).thenReturn(java.util.List.of(dateSlot));
        when(appointmentRepo.countBookedForSlotAndDate(dateSlot.getId(), date)).thenReturn(2L);
        
        java.util.List<com.hms.api.appointment.response.SlotAvailabilityResponse> availability = service.getSlotAvailability(providerId, date);
        
        assertNotNull(availability);
        assertEquals(1, availability.size());
        assertEquals(LocalTime.of(6, 0), availability.get(0).fromTime());
        assertEquals(LocalTime.of(8, 0), availability.get(0).toTime());
        assertEquals(8, availability.get(0).availableCount());
    }

    @Test
    void testGetSlotAvailabilityCheck_DateSpecificSlot_ReturnsCustomSlot() {
        UUID providerId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 8, 15);
        
        AppointmentSlot dateSlot = new AppointmentSlot();
        dateSlot.setId(UUID.randomUUID());
        dateSlot.setConsultantId(providerId);
        dateSlot.setSpecificDate(date);
        dateSlot.setFromTime("06:00");
        dateSlot.setToTime("08:00");
        dateSlot.setMaxPatients(10);
        
        when(consultantLeaveRepo.findActiveByConsultantAndDate(providerId, date)).thenReturn(java.util.List.of());
        when(slotRepo.findSpecificDateSlots(providerId, date)).thenReturn(java.util.List.of(dateSlot));
        when(appointmentRepo.countBookedForSlotAndDate(dateSlot.getId(), date)).thenReturn(0L);
        
        com.hms.api.appointment.response.AvailabilityCheckResponse response = service.getSlotAvailabilityCheck(providerId, date);
        
        assertNotNull(response);
        assertNull(response.reason());
        assertEquals(1, response.slots().size());
        assertEquals(LocalTime.of(6, 0), response.slots().get(0).fromTime());
        assertEquals(LocalTime.of(8, 0), response.slots().get(0).toTime());
    }
}
