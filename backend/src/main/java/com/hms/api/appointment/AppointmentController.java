package com.hms.api.appointment;
import org.springframework.security.access.prepost.PreAuthorize;

import com.hms.api.appointment.request.BookAppointmentRequest;
import com.hms.api.appointment.request.CreateSlotRequest;
import com.hms.api.appointment.request.RescheduleAppointmentRequest;
import com.hms.api.appointment.request.CreateLeaveRequest;
import com.hms.api.appointment.request.CreateTimeBlockRequest;
import com.hms.api.appointment.response.AppointmentResponse;
import com.hms.api.appointment.response.SlotAvailabilityResponse;
import com.hms.api.appointment.response.LeaveResponse;
import com.hms.api.appointment.response.DoctorCalendarResponse;
import com.hms.api.appointment.response.DayBoardResponse;
import com.hms.api.appointment.response.DateStatusResponse;
import com.hms.api.appointment.response.AvailabilityCheckResponse;
import com.hms.security.HmsUserDetails;
import com.hms.api.shared.ApiResponse;
import com.hms.application.appointment.AppointmentSchedulingService;
import com.hms.application.appointment.ConsultantAvailabilityService;
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
    private final ConsultantAvailabilityService availabilityService;
    private final com.hms.infrastructure.persistence.appointment.ConsultantLeaveJpaRepository consultantLeaveRepo;

    @PostMapping
    public ResponseEntity<ApiResponse<AppointmentResponse>> book(
            @Valid @RequestBody BookAppointmentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Appointment booked", appointmentService.bookAppointment(req)));
    }

    /**
     * GET /appointments/day-board?date= — the whole clinic's sessions and load for a day.
     *
     * <p>Declared ahead of {@code /{appointmentId}} for readability only; the literal
     * segment already takes precedence over the template when Spring matches.
     */
    @GetMapping("/day-board")
    public ResponseEntity<ApiResponse<DayBoardResponse>> dayBoard(
            @RequestParam(name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.ok("OK", appointmentService.getDayBoard(date)));
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
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate startDate = from != null ? from : (date != null ? date : LocalDate.now());
        LocalDate endDate = to != null ? to : startDate;
        return ResponseEntity.ok(ApiResponse.ok("OK",
            appointmentService.getByDateRange(null, startDate, endDate)));
    }

    @GetMapping("/provider/{providerId}")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getByProviderAndDate(
            @PathVariable("providerId") UUID providerId,
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate startDate = from != null ? from : (date != null ? date : LocalDate.now());
        LocalDate endDate = to != null ? to : startDate;
        return ResponseEntity.ok(ApiResponse.ok("OK",
            appointmentService.getByDateRange(providerId, startDate, endDate)));
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

        java.util.UUID consultantId = resolveConsultantId(req.consultantId());
        var result = availabilityService.createFullDayLeave(req, consultantId);

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(
                "Leave marked successfully. " + result.cancelledAppointments() + " appointments cancelled.",
                result.blocks().get(0)));
    }

    /**
     * POST /appointments/consultant/time-blocks — block part of a day rather
     * than the whole of it.
     *
     * <p>Takes a list of windows so several non-contiguous blocks on the same
     * dates commit together; each becomes its own leave row and is removed
     * through the same {@code DELETE /consultant/leaves/{id}} as a full-day
     * leave.
     */
    @PostMapping("/consultant/time-blocks")
    @PreAuthorize("hasPermission('OP_QUEUE','') or hasPermission('SETTINGS_CONSULTANT','') or hasPermission('APPOINTMENT','')")
    public ResponseEntity<ApiResponse<List<LeaveResponse>>> createTimeBlocks(
            @Valid @RequestBody CreateTimeBlockRequest req) {

        java.util.UUID consultantId = resolveConsultantId(req.consultantId());
        var result = availabilityService.createTimeBlocks(req, consultantId);

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(
                "Time blocked successfully. " + result.cancelledAppointments() + " appointments cancelled.",
                result.blocks()));
    }

    /**
     * The consultant a block is being written against: whoever the caller named,
     * or the caller themselves when they are a consultant. Reception books for
     * doctors and so must pass an id; a doctor managing their own calendar does
     * not have one to hand.
     */
    private java.util.UUID resolveConsultantId(java.util.UUID requested) {
        if (requested != null) return requested;
        HmsUserDetails principal = (HmsUserDetails) org.springframework.security.core.context.SecurityContextHolder
            .getContext().getAuthentication().getPrincipal();
        java.util.UUID fromPrincipal = principal.getConsultantId();
        if (fromPrincipal == null) {
            throw new com.hms.exception.BusinessRuleViolationException("Consultant ID must be specified");
        }
        return fromPrincipal;
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
            .map(LeaveResponse::from)
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
            .map(LeaveResponse::from)
            .toList();
            
        return ResponseEntity.ok(ApiResponse.ok("OK", list));
    }

    @DeleteMapping("/consultant/leaves/{leaveId}")
    @PreAuthorize("hasPermission('OP_QUEUE','') or hasPermission('SETTINGS_CONSULTANT','') or hasPermission('APPOINTMENT','')")
    public ResponseEntity<ApiResponse<Void>> deleteLeave(@PathVariable("leaveId") java.util.UUID leaveId) {
        HmsUserDetails principal = (HmsUserDetails) org.springframework.security.core.context.SecurityContextHolder
            .getContext().getAuthentication().getPrincipal();

        // A doctor may only unblock their own calendar; reception has no
        // consultantId and is trusted with anyone's, exactly as before.
        availabilityService.deleteBlock(leaveId, principal.getConsultantId());

        return ResponseEntity.ok(ApiResponse.ok("Leave cancelled successfully."));
    }

    /**
     * GET /appointments/consultant/leaves/by-range?startDate=&endDate=&consultantId=
     *
     * <p>Every consultant's blocks across a date range, or one consultant's when
     * filtered. The appointment calendar draws these as background events, so it
     * needs the whole clinic in one request rather than one request per doctor.
     */
    @GetMapping("/consultant/leaves/by-range")
    @PreAuthorize("hasPermission('OP_QUEUE','') or hasPermission('SETTINGS_CONSULTANT','') or hasPermission('APPOINTMENT','')")
    public ResponseEntity<ApiResponse<List<LeaveResponse>>> getLeavesInRange(
            @RequestParam(value = "consultantId", required = false) java.util.UUID consultantId,
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<LeaveResponse> list = consultantLeaveRepo
            .findActiveInDateRange(consultantId, startDate, endDate).stream()
            .map(LeaveResponse::from)
            .toList();
        return ResponseEntity.ok(ApiResponse.ok("OK", list));
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
        
        // 2. Fetch every block for this range — full-day leaves and partial-day
        //    time blocks alike; the UI draws both, and the day status below
        //    treats them differently.
        List<com.hms.domain.appointment.model.ConsultantLeave> blocks =
            consultantLeaveRepo.findActiveByConsultantAndDateRange(consultantId, startDate, endDate);
        List<LeaveResponse> leaves = blocks.stream().map(LeaveResponse::from).toList();

        // 3. Compute daily status for each date in the range
        java.util.List<DateStatusResponse> dateStatuses = new java.util.ArrayList<>();

        // Find all active slots for the consultant
        List<com.hms.domain.appointment.model.AppointmentSlot> slots = slotService.getSlotsByConsultant(consultantId);

        java.time.LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            final java.time.LocalDate finalCurrent = current;

            // A. A full-day leave takes the date out entirely
            boolean onLeave = blocks.stream()
                .filter(com.hms.domain.appointment.model.ConsultantLeave::isFullDay)
                .anyMatch(b -> !finalCurrent.isBefore(b.getStartDate()) && !finalCurrent.isAfter(b.getEndDate()));

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
                    // C. Subtract any partial-day blocks covering this date
                    var blocksToday = blocks.stream()
                        .filter(b -> !b.isFullDay())
                        .filter(b -> !finalCurrent.isBefore(b.getStartDate()) && !finalCurrent.isAfter(b.getEndDate()))
                        .toList();
                    var bookableSlots = appointmentService.removeBlockedSlots(daySlots, blocksToday);

                    int maxCapacity = bookableSlots.stream().mapToInt(com.hms.domain.appointment.model.AppointmentSlot::getMaxPatients).sum();

                    // Count booked appointments on this day
                    long bookedCount = appointments.stream()
                        .filter(a -> a.appointmentDate().equals(finalCurrent) &&
                            (a.status().equals("BOOKED") || a.status().equals("CHECKED_IN")))
                        .count();

                    // PARTIAL outranks FULLY_BOOKED: when hours are blocked, that
                    // is the thing the doctor needs to see on the tile, and a
                    // "fully booked" badge on a morning they blocked themselves
                    // reads as a scheduling problem rather than their own decision.
                    String status;
                    if (bookableSlots.size() < daySlots.size()) {
                        status = "PARTIAL";
                    } else if (bookedCount >= maxCapacity) {
                        status = "FULLY_BOOKED";
                    } else if (bookedCount > 0) {
                        status = "HAS_APPOINTMENTS";
                    } else {
                        status = "AVAILABLE";
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
