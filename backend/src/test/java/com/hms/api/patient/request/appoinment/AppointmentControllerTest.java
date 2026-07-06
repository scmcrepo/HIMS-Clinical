package com.hms.api.patient.request.appoinment;

import com.hms.api.appointment.AppointmentController;
import com.hms.api.appointment.request.BookAppointmentRequest;
import com.hms.api.appointment.request.CreateSlotRequest;
import com.hms.api.appointment.request.RescheduleAppointmentRequest;
import com.hms.api.appointment.response.AppointmentResponse;
import com.hms.api.appointment.response.SlotAvailabilityResponse;
import com.hms.api.shared.ApiResponse;
import com.hms.application.appointment.AppointmentSchedulingService;
import com.hms.application.appointment.SlotManagementService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentControllerTest {

    @Mock
    private AppointmentSchedulingService appointmentService;

    @Mock
    private SlotManagementService slotService;

    @InjectMocks
    private AppointmentController controller;

    @Test
    void book_shouldReturnCreated() {
        BookAppointmentRequest req = mock(BookAppointmentRequest.class);
        AppointmentResponse response = mock(AppointmentResponse.class);

        when(appointmentService.bookAppointment(req)).thenReturn(response);

        ResponseEntity<ApiResponse<AppointmentResponse>> result = controller.book(req);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        verify(appointmentService).bookAppointment(req);
    }

    @Test
    void getById_shouldReturnAppointment() {
        UUID id = UUID.randomUUID();
        AppointmentResponse response = mock(AppointmentResponse.class);

        when(appointmentService.getById(id)).thenReturn(response);

        ResponseEntity<ApiResponse<AppointmentResponse>> result = controller.getById(id);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        verify(appointmentService).getById(id);
    }

    @Test
    void reschedule_shouldReturnUpdatedAppointment() {
        UUID id = UUID.randomUUID();
        RescheduleAppointmentRequest req = mock(RescheduleAppointmentRequest.class);
        AppointmentResponse response = mock(AppointmentResponse.class);

        when(appointmentService.reschedule(id, req)).thenReturn(response);

        ResponseEntity<ApiResponse<AppointmentResponse>> result = controller.reschedule(id, req);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        verify(appointmentService).reschedule(id, req);
    }

    @Test
    void checkIn_shouldReturnCheckedInAppointment() {
        UUID id = UUID.randomUUID();
        AppointmentResponse response = mock(AppointmentResponse.class);

        when(appointmentService.checkIn(id)).thenReturn(response);

        ResponseEntity<ApiResponse<AppointmentResponse>> result = controller.checkIn(id);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        verify(appointmentService).checkIn(id);
    }

    @Test
    void linkPatient_shouldReturnLinkedAppointment() {
        UUID appointmentId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();

        AppointmentResponse response = mock(AppointmentResponse.class);

        when(appointmentService.linkPatient(appointmentId, patientId))
                .thenReturn(response);

        ResponseEntity<ApiResponse<AppointmentResponse>> result = controller.linkPatient(appointmentId, patientId);

        assertEquals(HttpStatus.OK, result.getStatusCode());

        verify(appointmentService)
                .linkPatient(appointmentId, patientId);
    }

    @Test
    void cancel_shouldReturnCancelledAppointment() {
        UUID id = UUID.randomUUID();

        AppointmentResponse response = mock(AppointmentResponse.class);

        when(appointmentService.cancel(id)).thenReturn(response);

        ResponseEntity<ApiResponse<AppointmentResponse>> result = controller.cancel(id);

        assertEquals(HttpStatus.OK, result.getStatusCode());

        verify(appointmentService).cancel(id);
    }

    @Test
    void getByDate_shouldReturnAppointments() {

        LocalDate date = LocalDate.now();

        when(appointmentService.getByProviderAndDate(null, date))
                .thenReturn(List.of());

        ResponseEntity<ApiResponse<List<AppointmentResponse>>> result = controller.getByDate(date);

        assertEquals(HttpStatus.OK, result.getStatusCode());

        verify(appointmentService)
                .getByProviderAndDate(null, date);
    }

    @Test
    void getByProviderAndDate_shouldReturnAppointments() {

        UUID providerId = UUID.randomUUID();
        LocalDate date = LocalDate.now();

        when(appointmentService.getByProviderAndDate(providerId, date))
                .thenReturn(List.of());

        ResponseEntity<ApiResponse<List<AppointmentResponse>>> result = controller.getByProviderAndDate(providerId,
                date);

        assertEquals(HttpStatus.OK, result.getStatusCode());

        verify(appointmentService)
                .getByProviderAndDate(providerId, date);
    }

    @Test
    void getByPatient_shouldReturnPage() {

        UUID patientId = UUID.randomUUID();

        Page<AppointmentResponse> page = new PageImpl<>(List.of());

        when(appointmentService.getByPatient(eq(patientId), any()))
                .thenReturn(page);

        ResponseEntity<ApiResponse<Page<AppointmentResponse>>> result = controller.getByPatient(patientId, 0, 20);

        assertEquals(HttpStatus.OK, result.getStatusCode());

        verify(appointmentService)
                .getByPatient(eq(patientId), any());
    }

    @Test
    void getAvailability_shouldReturnSlots() {

        UUID providerId = UUID.randomUUID();
        LocalDate date = LocalDate.now();

        when(appointmentService.getSlotAvailability(providerId, date))
                .thenReturn(List.of());

        ResponseEntity<ApiResponse<List<SlotAvailabilityResponse>>> result = controller.getAvailability(providerId,
                date);

        assertEquals(HttpStatus.OK, result.getStatusCode());

        verify(appointmentService)
                .getSlotAvailability(providerId, date);
    }

    @Test
    void createSlot_shouldCreateSlot() {

        CreateSlotRequest request = mock(CreateSlotRequest.class);

        ResponseEntity<ApiResponse<Void>> result = controller.createSlot(request);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());

        verify(slotService).createSlot(request);
    }

    @Test
    void deleteSlot_shouldDeleteSlot() {

        UUID slotId = UUID.randomUUID();

        ResponseEntity<ApiResponse<Void>> result = controller.deleteSlot(slotId);

        assertEquals(HttpStatus.OK, result.getStatusCode());

        verify(slotService).deleteSlot(slotId);
    }

    @Test
    void searchByDate_withConsultant() {

        UUID consultant = UUID.randomUUID();
        LocalDate date = LocalDate.now();

        when(appointmentService.getByProviderAndDate(consultant, date))
                .thenReturn(List.of());

        controller.searchByDate(date, 0, 20, consultant);

        verify(appointmentService)
                .getByProviderAndDate(consultant, date);
    }

    @Test
    void searchByDate_withoutConsultant() {

        LocalDate date = LocalDate.now();

        controller.searchByDate(date, 0, 20, null);

        verify(appointmentService)
                .getByProviderAndDate(any(UUID.class), eq(date));
    }

    @Test
    void searchByDate_withoutDate() {

        controller.searchByDate(null, 0, 20, null);

        verify(appointmentService)
                .getByProviderAndDate(any(UUID.class), any(LocalDate.class));
    }

    @Test
    void getByPatientId_shouldReturnAppointments() {

        UUID patientId = UUID.randomUUID();

        when(appointmentService.getByPatientId(patientId))
                .thenReturn(List.of());

        ResponseEntity<ApiResponse<List<AppointmentResponse>>> result = controller.getByPatientId(patientId);

        assertEquals(HttpStatus.OK, result.getStatusCode());

        verify(appointmentService)
                .getByPatientId(patientId);
    }
}