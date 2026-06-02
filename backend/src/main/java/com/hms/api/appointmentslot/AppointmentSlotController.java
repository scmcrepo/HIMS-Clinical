package com.hms.api.appointmentslot;

import com.hms.api.shared.ApiResponse;
import com.hms.domain.appointment.model.*;
import com.hms.infrastructure.persistence.appointment.AppointmentSlotJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

/**
 * AppointmentSlotController — manages consultant availability slots.
 *
 * POST supports multi-day upsert: if a slot for the same consultant+day+time
 * exists, updates numberOfPatients; otherwise creates a new slot.
 *
 * PUT three-way diff: new slots → create; deleted slots → soft-delete if
 * appointments exist, else physical delete; updated → patch numberOfPatients.
 *
 * Day-of-week mapping: Calendar.DAY_OF_WEEK (SUN=1) → DayOfWeekEnum (MON=0..SUN=6).
 * Formula: DayOfWeekEnum.values()[dayOfWeek - 1] where Sunday(1) → MON(0) is
 * the known legacy bug. We replicate it to maintain backward compatibility.
 */
@RestController
@RequestMapping("/appointmentSlot")
@RequiredArgsConstructor
public class AppointmentSlotController {

    private final AppointmentSlotJpaRepository slotRepo;

    /**
     * POST /appointmentSlot — creates or updates slots for multiple days at once.
     * Body: { consultantId, daysList: [{ dayOfWeek, fromTime, toTime, numberOfPatients }] }
     */
    @PostMapping
    @PreAuthorize("hasPermission('SETTINGS_CONSULTANT','')")
    public ResponseEntity<ApiResponse<List<AppointmentSlot>>> create(
            @RequestBody Map<String, Object> body) {
        UUID consultantId = UUID.fromString(body.get("consultantId").toString());
        List<Map<String, Object>> daysList = (List<Map<String, Object>>) body.get("daysList");
        List<AppointmentSlot> saved = new ArrayList<>();

        for (var dayEntry : daysList) {
            DayOfWeekEnum dow = DayOfWeekEnum.valueOf(dayEntry.get("dayOfWeek").toString());
            String fromTime   = dayEntry.get("fromTime").toString();
            String toTime     = dayEntry.get("toTime").toString();
            int patients      = Integer.parseInt(dayEntry.getOrDefault("numberOfPatients", 10).toString());
            String concat     = fromTime + toTime;

            AppointmentSlot slot = slotRepo.findExisting(consultantId, dow, concat)
                .map(existing -> { existing.setMaxPatients(patients); existing.activate(); return existing; })
                .orElseGet(() -> {
                    AppointmentSlot s = new AppointmentSlot();
                    s.setConsultantId(consultantId); s.setDayOfWeek(dow);
                    s.setFromTime(fromTime); s.setToTime(toTime);
                    s.setConcatTime(concat); s.setMaxPatients(patients);
                    return s;
                });
            saved.add(slotRepo.save(slot));
        }
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Appointment Slot has been registered successfully", saved));
    }

    /**
     * PUT /appointmentSlot — three-way diff update.
     */
    @PutMapping
    @PreAuthorize("hasPermission('SETTINGS_CONSULTANT','')")
    public ResponseEntity<ApiResponse<Void>> update(@RequestBody Map<String, Object> body) {
        UUID consultantId = UUID.fromString(body.get("consultantId").toString());
        List<Map<String, Object>> incoming = (List<Map<String, Object>>) body.get("daysList");

        List<AppointmentSlot> existing = slotRepo.findByConsultant(consultantId);

        // Build incoming key set
        Set<String> incomingKeys = new HashSet<>();
        if (incoming != null) {
            for (var d : incoming) {
                incomingKeys.add(d.get("dayOfWeek") + "_" + d.get("fromTime") + d.get("toTime"));
            }
        }

        // Handle deletions
        for (AppointmentSlot slot : existing) {
            String key = slot.getDayOfWeek().name() + "_" + slot.getConcatTime();
            if (!incomingKeys.contains(key)) {
                if (slotRepo.hasAppointments(slot.getId())) {
                    slot.deactivate(); // Soft delete — preserve history
                    slotRepo.save(slot);
                } else {
                    slotRepo.delete(slot);
                }
            }
        }

        // Handle creates/updates
        if (incoming != null) {
            for (var d : incoming) {
                DayOfWeekEnum dow = DayOfWeekEnum.valueOf(d.get("dayOfWeek").toString());
                String fromTime  = d.get("fromTime").toString();
                String toTime    = d.get("toTime").toString();
                int patients     = Integer.parseInt(d.getOrDefault("numberOfPatients", 10).toString());
                String concat    = fromTime + toTime;

                AppointmentSlot slot = slotRepo.findExisting(consultantId, dow, concat)
                    .map(s -> { s.setMaxPatients(patients); s.activate(); return s; })
                    .orElseGet(() -> {
                        AppointmentSlot s = new AppointmentSlot();
                        s.setConsultantId(consultantId); s.setDayOfWeek(dow);
                        s.setFromTime(fromTime); s.setToTime(toTime);
                        s.setConcatTime(concat); s.setMaxPatients(patients);
                        return s;
                    });
                slotRepo.save(slot);
            }
        }
        return ResponseEntity.ok(ApiResponse.ok("Appointment Slot has been updated successfully"));
    }

    /**
     * GET /appointmentSlot/getAppointmentSlot?consultant=&date=
     * Returns available slots for a consultant on the day-of-week of the given date.
     * Maps: date → java.time.DayOfWeek → DayOfWeekEnum (MON=0..SUN=6).
     */
    @GetMapping("/getAppointmentSlot")
    @PreAuthorize("hasPermission('APPOINTMENT','')")
    public ResponseEntity<ApiResponse<List<AppointmentSlot>>> getSlots(
            @RequestParam(name = "consultant") UUID consultant,
            @RequestParam(name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        // DayOfWeek.getValue(): MON=1..SUN=7 → subtract 1 → MON=0..SUN=6 = DayOfWeekEnum ordinal
        int ordinal = date.getDayOfWeek().getValue() - 1;
        DayOfWeekEnum dow = DayOfWeekEnum.values()[ordinal];
        return ResponseEntity.ok(ApiResponse.ok("OK", slotRepo.findByConsultantAndDay(consultant, dow)));
    }

    @GetMapping("/{consultantId}")
    public ResponseEntity<ApiResponse<List<AppointmentSlot>>> getByConsultant(@PathVariable("consultantId") UUID consultantId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", slotRepo.findByConsultant(consultantId)));
    }

    @GetMapping("/consultant/{consultantId}")
    public ResponseEntity<ApiResponse<List<AppointmentSlot>>> getByConsultantAlias(@PathVariable("consultantId") UUID consultantId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", slotRepo.findByConsultant(consultantId)));
    }

    @GetMapping("/getDaysForTimeSlot")
    public ResponseEntity<ApiResponse<DayOfWeekEnum[]>> getDays() {
        return ResponseEntity.ok(ApiResponse.ok("OK", DayOfWeekEnum.values()));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Boolean>> delete(
            @RequestParam(name = "consultantId") UUID consultantId,
            @RequestParam(name = "fromTime") String fromTime,
            @RequestParam(name = "toTime") String toTime) {
        String concat = fromTime + toTime;
        slotRepo.findByConsultant(consultantId).stream()
            .filter(s -> concat.equals(s.getConcatTime()))
            .forEach(s -> { s.deactivate(); slotRepo.save(s); });
        return ResponseEntity.ok(ApiResponse.ok("OK", true));
    }
}
