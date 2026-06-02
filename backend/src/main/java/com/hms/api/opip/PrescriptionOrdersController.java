package com.hms.api.opip;

import com.hms.api.opip.response.PrescriptionResponse;
import com.hms.api.shared.ApiResponse;
import com.hms.domain.encounter.model.ClinicalEncounter;
import com.hms.infrastructure.persistence.encounter.ClinicalEncounterJpaRepository;
import com.hms.infrastructure.persistence.patient.PatientJpaRepository;
import com.hms.infrastructure.sequence.NumberSequenceJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * PrescriptionOrdersController — pharmacy-facing view of pending prescription orders.
 *
 * GET /prescription-orders          — all prescriptions from today's encounters (OP) + active IP
 * GET /prescription-orders/patient  — prescriptions for a specific patient
 * GET /prescription-orders/encounter/{encounterId} — prescriptions for a specific encounter
 *
 * Used by the Pharmacy module to identify pending orders, dispense drugs, and create sales.
 */
@RestController
@RequestMapping("/prescription-orders")
@RequiredArgsConstructor
public class PrescriptionOrdersController {

    private final ClinicalEncounterJpaRepository encounterRepo;
    private final PatientJpaRepository patientRepo;
    private final NumberSequenceJpaRepository numberSequenceRepo;

    record PrescriptionOrderRow(
        UUID                            encounterId,
        String                          encounterType,
        UUID                            patientId,
        String                          patientName,
        String                          patientNumber,
        String                          consultantName,
        Instant                         prescribedAt,
        List<PrescriptionResponse.PrescriptionLineResponse> items
    ) {}

    /**
     * GET /prescription-orders?date=&patientId=&type=OP|IP
     * Returns all prescription orders for today (OP) or active (IP) encounters.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<PrescriptionOrderRow>>> getPendingPrescriptions(
            @RequestParam(value = "patientId", required = false) String patientId,
            @RequestParam(value = "type", required = false, defaultValue = "ALL") String type) {

        List<ClinicalEncounter> encounters = new ArrayList<>();
        Instant startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);

        if ("IP".equals(type)) {
            encounters = encounterRepo.findActiveInpatients();
        } else if ("OP".equals(type)) {
            encounters = encounterRepo
                .findTodayOutpatients(startOfDay, PageRequest.of(0, 200, Sort.by("startedAt").descending()))
                .getContent();
        } else {
            // ALL: today's OP + active IP
            encounters.addAll(encounterRepo.findActiveInpatients());
            encounters.addAll(encounterRepo
                .findTodayOutpatients(startOfDay, PageRequest.of(0, 200, Sort.by("startedAt").descending()))
                .getContent());
        }

        // Filter by patient if requested
        if (patientId != null && !patientId.isBlank()) {
            UUID pid;
            try { pid = UUID.fromString(patientId); } catch (Exception e) { return ok(List.of()); }
            encounters = encounters.stream()
                .filter(e -> pid.equals(e.getPatientId()))
                .collect(Collectors.toList());
        }

        // Remove duplicates (encounter could appear in both lists)
        encounters = encounters.stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(ClinicalEncounter::getId, e -> e, (a, b) -> a))
            .values().stream().toList();

        List<PrescriptionOrderRow> rows = encounters.stream()
            .flatMap(enc -> extractPrescriptions(enc).stream())
            .collect(Collectors.toList());

        return ok(rows);
    }

    /**
     * GET /prescription-orders/encounter/{encounterId}
     * Returns prescriptions for a specific encounter.
     */
    @GetMapping("/encounter/{encounterId}")
    public ResponseEntity<ApiResponse<List<PrescriptionOrderRow>>> getForEncounter(
            @PathVariable("encounterId") UUID encounterId) {
        ClinicalEncounter enc = encounterRepo.findById(encounterId).orElse(null);
        if (enc == null) return ok(List.of());
        return ok(extractPrescriptions(enc));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<PrescriptionOrderRow> extractPrescriptions(ClinicalEncounter enc) {
        if (enc.getConsultantShareMap() == null) return List.of();
        Object raw = enc.getConsultantShareMap().get("prescriptions");
        if (!(raw instanceof List<?> prescList) || prescList.isEmpty()) return List.of();

        List<PrescriptionOrderRow> result = new ArrayList<>();
        for (Object item : prescList) {
            if (!(item instanceof Map<?,?> pm)) continue;
            Map<String, Object> prxMap = (Map<String, Object>) pm;
            Object rawItems = prxMap.get("items");
            if (!(rawItems instanceof List<?> items) || ((List<?>) items).isEmpty()) continue;

            List<PrescriptionResponse.PrescriptionLineResponse> lines = new ArrayList<>();
            for (Object li : items) {
                if (!(li instanceof Map<?,?> lm)) continue;
                Map<String, Object> l = (Map<String, Object>) lm;
                lines.add(new PrescriptionResponse.PrescriptionLineResponse(
                    parseUUID(l.get("id")),
                    str(l.get("drugItemId")),
                    str(l.get("drugName")),
                    str(l.get("frequency")),
                    str(l.get("duration")),
                    l.get("qty") instanceof Number n ? n.intValue() : 1,
                    str(l.get("instructionId")),
                    str(l.get("instructionLabel")),
                    str(l.get("routeId")),
                    str(l.get("routeLabel")),
                    str(l.get("remarks"))
                ));
            }

            String patientName = "Unknown Patient";
            String patientNumber = null;
            if (enc.getPatientId() != null) {
                patientName = patientRepo.findById(enc.getPatientId())
                    .map(p -> p.getFirstName() + " " + p.getLastName())
                    .orElse(str(enc.getPatientId()));
                patientNumber = numberSequenceRepo.findById(enc.getPatientId())
                    .map(com.hms.infrastructure.sequence.NumberSequenceEntity::getValue)
                    .orElse(null);
            }

            result.add(new PrescriptionOrderRow(
                enc.getId(),
                enc.getEncounterType() != null ? enc.getEncounterType().name() : "OP",
                enc.getPatientId(),
                patientName,
                patientNumber,
                str(prxMap.get("requestedByName")),
                parseInstant(prxMap.get("createdAt")),
                lines
            ));
        }
        return result;
    }

    private static UUID parseUUID(Object o) {
        if (o == null) return null;
        try { return UUID.fromString(o.toString()); } catch (Exception e) { return null; }
    }
    private static Instant parseInstant(Object o) {
        if (o == null) return null;
        try { return Instant.parse(o.toString()); } catch (Exception e) { return null; }
    }
    private static String str(Object o) { return o == null ? null : o.toString(); }

    private ResponseEntity<ApiResponse<List<PrescriptionOrderRow>>> ok(List<PrescriptionOrderRow> data) {
        return ResponseEntity.ok(ApiResponse.ok("OK", data));
    }
}
