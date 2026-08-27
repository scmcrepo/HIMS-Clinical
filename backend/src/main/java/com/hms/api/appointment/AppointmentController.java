package com.hms.api.appointment;
import org.springframework.security.access.prepost.PreAuthorize;

import com.hms.api.appointment.request.BookAppointmentRequest;
import com.hms.api.appointment.request.CreateSlotRequest;
import com.hms.api.appointment.request.RescheduleAppointmentRequest;
import com.hms.api.appointment.request.CreateLeaveRequest;
import com.hms.api.appointment.response.AppointmentResponse;
import com.hms.api.appointment.response.SlotAvailabilityResponse;
import com.hms.api.appointment.response.LeaveResponse;
import com.hms.api.appointment.response.DoctorCalendarResponse;
import com.hms.api.appointment.response.DateStatusResponse;
import com.hms.api.appointment.response.AvailabilityCheckResponse;
import com.hms.security.HmsUserDetails;
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
@PreAuthorize("hasPermission('APPOINTMENT','') or hasPermission('OP_QUEUE','')")
public class AppointmentController {

    private final AppointmentSchedulingService appointmentService;
    private final SlotManagementService slotService;
    private final com.hms.infrastructure.persistence.appointment.ConsultantLeaveJpaRepository consultantLeaveRepo;
    private final com.hms.infrastructure.persistence.appointment.AppointmentJpaRepository appointmentRepo;

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

    /**
     * Enhanced availability check that returns a reason when slots are empty.
     * reason = "ON_LEAVE" | "NO_SLOTS" | null (normal availability).
     */
    @GetMapping("/provider/{providerId}/availability-check")
    public ResponseEntity<ApiResponse<AvailabilityCheckResponse>> getAvailabilityCheck(
            @PathVariable("providerId") UUID providerId,
            @RequestParam(name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.ok("OK",
            appointmentService.getSlotAvailabilityCheck(providerId, date)));
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

    // ── Consultant Leaves & Availability Calendar Management ──────────────────────

    @PostMapping("/consultant/leaves")
    @PreAuthorize("hasPermission('OP_QUEUE','') or hasPermission('SETTINGS_CONSULTANT','') or hasPermission('APPOINTMENT','')")
    public ResponseEntity<ApiResponse<LeaveResponse>> createLeave(
            @Valid @RequestBody CreateLeaveRequest req) {
        
        java.util.UUID consultantId = req.consultantId();
        if (consultantId == null) {
            HmsUserDetails principal = (HmsUserDetails) org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
            consultantId = principal.getConsultantId();
        }
        if (consultantId == null) {
            throw new com.hms.exception.BusinessRuleViolationException("Consultant ID must be specified");
        }
        
        if (req.startDate().isAfter(req.endDate())) {
            throw new com.hms.exception.BusinessRuleViolationException("Start date cannot be after end date");
        }
        
        // 1. Check for overlapping leaves
        List<com.hms.domain.appointment.model.ConsultantLeave> overlaps = consultantLeaveRepo.findActiveByConsultantAndDateRange(
            consultantId, req.startDate(), req.endDate());
        if (!overlaps.isEmpty()) {
            throw new com.hms.exception.BusinessRuleViolationException(
                "This leave range overlaps with an existing leave from " + 
                overlaps.get(0).getStartDate() + " to " + overlaps.get(0).getEndDate());
        }
        
        // 2. Create the leave record
        com.hms.domain.appointment.model.ConsultantLeave leave = new com.hms.domain.appointment.model.ConsultantLeave();
        leave.setConsultantId(consultantId);
        leave.setStartDate(req.startDate());
        leave.setEndDate(req.endDate());
        leave.setReason(req.reason());
        leave.setStatus(com.hms.domain.shared.model.EntityStatus.ACTIVE);
        
        com.hms.domain.appointment.model.ConsultantLeave saved = consultantLeaveRepo.save(leave);
        
        // 3. Cancel existing appointments falling in this leave range
        List<com.hms.domain.appointment.model.Appointment> appointments = appointmentRepo.findByProviderAndDateRange(
            consultantId, req.startDate(), req.endDate());
        int cancelledCount = 0;
        for (com.hms.domain.appointment.model.Appointment appt : appointments) {
            if (appt.isBooked()) {
                appt.cancel();
                appt.setNotes((appt.getNotes() != null ? appt.getNotes() + "\n" : "") + 
                    "Cancelled due to doctor leave from " + req.startDate() + " to " + req.endDate());
                appointmentRepo.save(appt);
                cancelledCount++;
            }
        }
        
        LeaveResponse response = new LeaveResponse(
            saved.getId(),
            saved.getConsultantId(),
            saved.getStartDate(),
            saved.getEndDate(),
            saved.getReason(),
            saved.getStatus().name()
        );
        
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Leave marked successfully. " + cancelledCount + " appointments cancelled.", response));
    }

    /**
     * GET /appointments/consultant/{consultantId}/leaves — Admin endpoint to fetch
     * a specific consultant's active leaves (for disabling leave dates in slot config calendar).
     */
    @GetMapping("/consultant/{consultantId}/leaves")
    @PreAuthorize("hasPermission('APPOINTMENT','') or hasPermission('SETTINGS_CONSULTANT','')")
    public ResponseEntity<ApiResponse<List<LeaveResponse>>> getLeavesByConsultantId(
            @PathVariable("consultantId") java.util.UUID consultantId) {
        List<LeaveResponse> list = consultantLeaveRepo.findActiveByConsultantOrderByStartDateDesc(consultantId).stream()
            .map(l -> new LeaveResponse(
                l.getId(),
                l.getConsultantId(),
                l.getStartDate(),
                l.getEndDate(),
                l.getReason(),
                l.getStatus().name()
            ))
            .toList();
        return ResponseEntity.ok(ApiResponse.ok("OK", list));
    }

    @GetMapping("/consultant/leaves")
    @PreAuthorize("hasPermission('OP_QUEUE','')")
    public ResponseEntity<ApiResponse<List<LeaveResponse>>> getLeaves() {
        HmsUserDetails principal = (HmsUserDetails) org.springframework.security.core.context.SecurityContextHolder.getContext()
            .getAuthentication().getPrincipal();
        java.util.UUID consultantId = principal.getConsultantId();
        if (consultantId == null) {
            throw new com.hms.exception.BusinessRuleViolationException("User is not registered as a consultant");
        }
        
        List<LeaveResponse> list = consultantLeaveRepo.findActiveByConsultantOrderByStartDateDesc(consultantId).stream()
            .map(l -> new LeaveResponse(
                l.getId(),
                l.getConsultantId(),
                l.getStartDate(),
                l.getEndDate(),
                l.getReason(),
                l.getStatus().name()
            ))
            .toList();
            
        return ResponseEntity.ok(ApiResponse.ok("OK", list));
    }

    @DeleteMapping("/consultant/leaves/{leaveId}")
    @PreAuthorize("hasPermission('OP_QUEUE','') or hasPermission('SETTINGS_CONSULTANT','') or hasPermission('APPOINTMENT','')")
    public ResponseEntity<ApiResponse<Void>> deleteLeave(@PathVariable("leaveId") java.util.UUID leaveId) {
        HmsUserDetails principal = (HmsUserDetails) org.springframework.security.core.context.SecurityContextHolder.getContext()
            .getAuthentication().getPrincipal();
        java.util.UUID consultantId = principal.getConsultantId();
        
        com.hms.domain.appointment.model.ConsultantLeave leave = consultantLeaveRepo.findById(leaveId)
            .orElseThrow(() -> new com.hms.exception.ResourceNotFoundException("ConsultantLeave", leaveId));
            
        if (consultantId != null && !leave.getConsultantId().equals(consultantId)) {
            throw new com.hms.exception.BusinessRuleViolationException("Leave record does not belong to this consultant");
        }
        
        leave.softDelete(); // sets status to DELETED (ordinal 2)
        consultantLeaveRepo.save(leave);
        
        return ResponseEntity.ok(ApiResponse.ok("Leave cancelled successfully."));
    }

    @GetMapping("/consultant/calendar")
    @PreAuthorize("hasPermission('OP_QUEUE','') or hasPermission('SETTINGS_CONSULTANT','') or hasPermission('APPOINTMENT','')")
    public ResponseEntity<ApiResponse<DoctorCalendarResponse>> getDoctorCalendar(
            @RequestParam(value = "consultantId", required = false) java.util.UUID inputConsultantId,
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate endDate) {
        
        java.util.UUID consultantId = inputConsultantId;
        if (consultantId == null) {
            HmsUserDetails principal = (HmsUserDetails) org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
            consultantId = principal.getConsultantId();
        }
        
        if (consultantId == null) {
            // Return empty response rather than throwing exception to prevent admin UI crash
            return ResponseEntity.ok(ApiResponse.ok("No consultant selected", 
                new DoctorCalendarResponse(java.util.List.of(), java.util.List.of(), java.util.List.of())));
        }
        
        // 1. Fetch appointments for this range
        List<AppointmentResponse> appointments = appointmentService.getByProviderAndDateRange(consultantId, startDate, endDate);
        
        // 2. Fetch leaves for this range
        List<LeaveResponse> leaves = consultantLeaveRepo.findActiveByConsultantAndDateRange(consultantId, startDate, endDate).stream()
            .map(l -> new LeaveResponse(
                l.getId(),
                l.getConsultantId(),
                l.getStartDate(),
                l.getEndDate(),
                l.getReason(),
                l.getStatus().name()
            ))
            .toList();
            
        // 3. Compute daily status for each date in the range
        java.util.List<DateStatusResponse> dateStatuses = new java.util.ArrayList<>();
        
        // Find all active slots for the consultant
        List<com.hms.domain.appointment.model.AppointmentSlot> slots = slotService.getSlotsByConsultant(consultantId);
        
        java.time.LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            final java.time.LocalDate finalCurrent = current;
            
            // A. Check if on leave
            boolean onLeave = leaves.stream().anyMatch(l -> 
                !finalCurrent.isBefore(l.startDate()) && !finalCurrent.isAfter(l.endDate()));
                
            if (onLeave) {
                dateStatuses.add(new DateStatusResponse(current, "LEAVE", 0, 0));
            } else {
                // B. Find slots active on this date (date-specific slots take priority)
                int dow = current.getDayOfWeek().getValue() - 1; // 0=MON
                var specificDateSlots = slots.stream()
                    .filter(s -> s.getSpecificDate() != null && s.getSpecificDate().equals(finalCurrent) && s.getStatus() == com.hms.domain.shared.model.EntityStatus.ACTIVE)
                    .toList();
 
                var daySlots = !specificDateSlots.isEmpty() ? specificDateSlots : slots.stream()
                    .filter(s -> s.getSpecificDate() == null 
                        && s.getDayOfWeek().ordinal() == dow 
                        && (s.getEffectiveFrom() == null || !finalCurrent.isBefore(s.getEffectiveFrom()))
                        && (s.getEffectiveTo() == null || !finalCurrent.isAfter(s.getEffectiveTo()))
                        && s.getStatus() == com.hms.domain.shared.model.EntityStatus.ACTIVE)
                    .toList();
                    
                if (daySlots.isEmpty()) {
                    dateStatuses.add(new DateStatusResponse(current, "UNAVAILABLE", 0, 0));
                } else {
                    int maxCapacity = daySlots.stream().mapToInt(com.hms.domain.appointment.model.AppointmentSlot::getMaxPatients).sum();
                    
                    // Count booked appointments on this day
                    long bookedCount = appointments.stream()
                        .filter(a -> a.appointmentDate().equals(finalCurrent) && 
                            (a.status().equals("BOOKED") || a.status().equals("CHECKED_IN")))
                        .count();
                        
                    String status = "AVAILABLE";
                    if (bookedCount >= maxCapacity) {
                        status = "FULLY_BOOKED";
                    } else if (bookedCount > 0) {
                        status = "HAS_APPOINTMENTS";
                    }
                    
                    dateStatuses.add(new DateStatusResponse(current, status, (int) bookedCount, maxCapacity));
                }
            }
            current = current.plusDays(1);
        }
        
        DoctorCalendarResponse response = new DoctorCalendarResponse(appointments, leaves, dateStatuses);
        return ResponseEntity.ok(ApiResponse.ok("OK", response));
    }
}
