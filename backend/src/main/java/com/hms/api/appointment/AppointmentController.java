package com.hms.api.appointment;

import com.hms.api.appointment.request.BookAppointmentRequest;
import com.hms.api.appointment.request.CreateSlotRequest;
import com.hms.api.appointment.request.RescheduleAppointmentRequest;
import com.hms.api.appointment.response.AppointmentResponse;
import com.hms.api.appointment.response.SlotAvailabilityResponse;
import com.hms.api.shared.ApiResponse;
import com.hms.application.appointment.AppointmentSchedulingService;
import com.hms.application.appointment.SlotManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentSchedulingService appointmentService;
    private final SlotManagementService slotService;

    @PostMapping
    public ResponseEntity<ApiResponse<AppointmentResponse>> book(
            @Valid @RequestBody BookAppointmentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Appointment booked", appointmentService.bookAppointment(req)));
    }

    @GetMapping("/{appointmentId}")
    public ResponseEntity<ApiResponse<AppointmentResponse>> getById(
            @PathVariable("appointmentId") UUID appointmentId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", appointmentService.getById(appointmentId)));
    }

    @PutMapping("/{appointmentId}/reschedule")
    public ResponseEntity<ApiResponse<AppointmentResponse>> reschedule(
            @PathVariable("appointmentId") UUID appointmentId,
            @Valid @RequestBody RescheduleAppointmentRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Rescheduled",
            appointmentService.reschedule(appointmentId, req)));
    }

    @PostMapping("/{appointmentId}/check-in")
    public ResponseEntity<ApiResponse<AppointmentResponse>> checkIn(
            @PathVariable("appointmentId") UUID appointmentId) {
        return ResponseEntity.ok(ApiResponse.ok("Checked in",
            appointmentService.checkIn(appointmentId)));
    }

    @PutMapping("/{appointmentId}/patient/{patientId}")
    public ResponseEntity<ApiResponse<AppointmentResponse>> linkPatient(
            @PathVariable("appointmentId") UUID appointmentId,
            @PathVariable("patientId") UUID patientId) {
        return ResponseEntity.ok(ApiResponse.ok("Patient linked",
            appointmentService.linkPatient(appointmentId, patientId)));
    }

    @DeleteMapping("/{appointmentId}")
    public ResponseEntity<ApiResponse<AppointmentResponse>> cancel(
            @PathVariable("appointmentId") UUID appointmentId) {
        return ResponseEntity.ok(ApiResponse.ok("Cancelled",
            appointmentService.cancel(appointmentId)));
    }

    @GetMapping("/by-date")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getByDate(
            @RequestParam(name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.ok("OK",
            appointmentService.getByProviderAndDate(null, date)));
    }

    @GetMapping("/provider/{providerId}")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getByProviderAndDate(
            @PathVariable("providerId") UUID providerId,
            @RequestParam(name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.ok("OK",
            appointmentService.getByProviderAndDate(providerId, date)));
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<ApiResponse<Page<AppointmentResponse>>> getByPatient(
            @PathVariable("patientId") UUID patientId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size, Sort.by("appointmentDate").descending());
        return ResponseEntity.ok(ApiResponse.ok("OK",
            appointmentService.getByPatient(patientId, pageable)));
    }

    @GetMapping("/provider/{providerId}/availability")
    public ResponseEntity<ApiResponse<List<SlotAvailabilityResponse>>> getAvailability(
            @PathVariable("providerId") UUID providerId,
            @RequestParam(name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.ok("OK",
            appointmentService.getSlotAvailability(providerId, date)));
    }

    // ── Slot management ───────────────────────────────────────────────────

    @PostMapping("/slots")
    public ResponseEntity<ApiResponse<Void>> createSlot(
            @Valid @RequestBody CreateSlotRequest req) {
        slotService.createSlot(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Slot created"));
    }

    @DeleteMapping("/slots/{slotId}")
    public ResponseEntity<ApiResponse<Void>> deleteSlot(@PathVariable("slotId") UUID slotId) {
        slotService.deleteSlot(slotId);
        return ResponseEntity.ok(ApiResponse.ok("Slot deleted"));
    }

    /** GET /appointment/searchByDate?searchDate=&start=&limit=&consultant= */
    @GetMapping("/searchByDate")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> searchByDate(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate searchDate,
            @RequestParam(name = "start", defaultValue = "0") int start,
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @RequestParam(required = false) java.util.UUID consultant) {
        java.time.LocalDate date = searchDate != null ? searchDate : java.time.LocalDate.now();
        var results = appointmentService.getByProviderAndDate(
            consultant != null ? consultant : java.util.UUID.fromString("00000000-0000-0000-0000-000000000000"),
            date
        );
        return ResponseEntity.ok(ApiResponse.ok("OK", results));
    }

    /** GET /appointment/appointmentByPatientId/{patient} */
    @GetMapping("/appointmentByPatientId/{patientId}")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getByPatientId(@PathVariable java.util.UUID patientId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", appointmentService.getByPatientId(patientId)));
    }
}
