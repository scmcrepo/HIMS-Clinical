package com.hms.api.opip;
import org.springframework.security.access.prepost.PreAuthorize;

import com.hms.api.shared.ApiResponse;
import com.hms.application.encounter.EncounterManagementService;
import com.hms.domain.diagnostic.model.DiagnosticTemplate;
import com.hms.domain.encounter.model.ClinicalEncounter;
import com.hms.domain.orderset.model.OrderSet;
import com.hms.domain.orderset.model.OrderSetItem;
import com.hms.domain.shared.model.EntityStatus;
import com.hms.infrastructure.persistence.diagtemplate.DiagnosticTemplateJpaRepository;
import com.hms.infrastructure.persistence.encounter.ClinicalEncounterJpaRepository;
import com.hms.infrastructure.persistence.orderset.OrderSetJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * OpIpController — shared quick-add and master data lookup endpoints for
 * both OP and IP casesheet flows.
 *
 * GET    /op-ip/favorites?consultantId=&type=       — consultant favorites (DRUG|TEST)
 * POST   /op-ip/favorites/item                      — save new individual favorite
 * DELETE /op-ip/favorites/{id}                      — remove a favorite
 * GET    /op-ip/frequently-used?consultantId=&type= — top-N frequently used items
 * GET    /op-ip/last-prescribed?encounterId=        — last prescribed drugs for patient
 * GET    /instruction                               — list medication instructions
 * GET    /route                                     — list administration routes
 * GET    /frequency                                 — list dosing frequencies (proxy)
 * GET    /diagnostic/test-catalog?search=           — search diagnostic tests by name
 */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasPermission('OUT_PATIENT','')")
public class OpIpController {

    private final EncounterManagementService        encounterSvc;
    private final ClinicalEncounterJpaRepository    encounterRepo;
    private final DiagnosticTemplateJpaRepository   diagTemplateRepo;
    private final OrderSetJpaRepository             orderSetRepo;

    // ── Favorites (backed by OrderSet with isFavorite=true) ───────────────────

    @GetMapping("/op-ip/favorites")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getFavorites(
            @RequestParam(required = false) String consultantId,
            @RequestParam(defaultValue = "DRUG") String type) {
        if (consultantId == null || consultantId.isBlank())
            return ResponseEntity.ok(ApiResponse.ok("OK", List.of()));
        UUID cid;
        try { cid = UUID.fromString(consultantId); } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.ok("OK", List.of()));
        }
        List<OrderSet> favSets = orderSetRepo.findFavoritesByConsultant(cid);
        String targetItemType = "DRUG".equals(type) ? "PHARMACY" : "DIAGNOSTIC";
        List<Map<String, Object>> result = favSets.stream()
            .flatMap(os -> os.getItems().stream()
                .filter(i -> targetItemType.equalsIgnoreCase(i.getItemType()))
                .map(i -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id",              os.getId().toString());
                    m.put("itemId",          i.getServiceCatalogItemId() != null ? i.getServiceCatalogItemId().toString() : "");
                    m.put("itemName",        i.getItemName());
                    m.put("favoriteType",    type);
                    m.put("consultantId",    consultantId);
                    m.put("frequency",       i.getFrequency());
                    m.put("duration",        i.getDuration());
                    m.put("instructionLabel",i.getInstruction());
                    m.put("routeLabel",      i.getRouteLabel());
                    return m;
                })
            ).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok("OK", result));
    }

    @PostMapping("/op-ip/favorites/item")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addFavoriteItem(
            @RequestBody Map<String, Object> payload) {
        String consultantId  = str(payload.get("consultantId"));
        String itemType      = str(payload.getOrDefault("favoriteType", "DRUG"));
        String backendType   = "DRUG".equals(itemType) ? "PHARMACY" : "DIAGNOSTIC";
        String itemName      = str(payload.get("itemName"));

        OrderSet fav = new OrderSet();
        fav.setName("⭐ " + (itemName != null ? itemName : "Favorite"));
        fav.setSetType("DRUG".equals(itemType) ? "PRESCRIPTION" : "DIAGNOSTICS");
        fav.setIsFavorite(true);
        fav.setIsOutpatient(true);
        fav.setScope("CONSULTANT");
        fav.setStatus(EntityStatus.ACTIVE);
        if (consultantId != null) {
            try { fav.setConsultantId(UUID.fromString(consultantId)); } catch (Exception ignored) {}
        }
        OrderSetItem item = new OrderSetItem();
        item.setOrderSet(fav); item.setItemType(backendType); item.setItemName(itemName);
        if (payload.get("itemId") != null) {
            try { item.setServiceCatalogItemId(UUID.fromString(str(payload.get("itemId")))); } catch (Exception ignored) {}
        }
        item.setQuantity(1);
        item.setFrequency(str(payload.get("frequency")));
        item.setDuration(str(payload.get("duration")));
        item.setInstruction(str(payload.get("instructionLabel")));
        item.setRouteLabel(str(payload.get("routeLabel")));
        fav.getItems().add(item);

        OrderSet saved = orderSetRepo.save(fav);
        Map<String, Object> resp = new LinkedHashMap<>(payload);
        resp.put("id", saved.getId().toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Favorite saved", resp));
    }

    @DeleteMapping("/op-ip/favorites/{id}")
    public ResponseEntity<ApiResponse<Void>> removeFavorite(@PathVariable String id) {
        try {
            UUID uid = UUID.fromString(id);
            orderSetRepo.findById(uid).ifPresent(o -> { o.setStatus(EntityStatus.DELETED); orderSetRepo.save(o); });
        } catch (Exception ignored) {}
        return ResponseEntity.ok(ApiResponse.ok("Favorite removed", null));
    }

    // ── Frequently Used ───────────────────────────────────────────────────────

    @GetMapping("/op-ip/frequently-used")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getFrequentlyUsed(
            @RequestParam(required = false) String consultantId,
            @RequestParam(defaultValue = "DRUG") String type) {
        List<Map<String, Object>> frequent = computeFrequentlyUsed(consultantId, type);
        return ResponseEntity.ok(ApiResponse.ok("OK", frequent));
    }

    // ── Last Prescribed ───────────────────────────────────────────────────────

    @GetMapping("/op-ip/last-prescribed")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getLastPrescribed(
            @RequestParam String encounterId) {
        List<Map<String, Object>> lines = computeLastPrescribed(encounterId);
        return ResponseEntity.ok(ApiResponse.ok("OK", lines));
    }

    // ── Instruction Master ────────────────────────────────────────────────────

    @GetMapping("/instruction")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getInstructions() {
        List<Map<String, Object>> instructions = List.of(
            masterItem("INS-1",  "Before Food"),
            masterItem("INS-2",  "After Food"),
            masterItem("INS-3",  "With Food"),
            masterItem("INS-4",  "At Bedtime"),
            masterItem("INS-5",  "In the Morning"),
            masterItem("INS-6",  "In the Evening"),
            masterItem("INS-7",  "Empty Stomach"),
            masterItem("INS-8",  "Twice Daily"),
            masterItem("INS-9",  "Three Times Daily"),
            masterItem("INS-10", "Four Times Daily"),
            masterItem("INS-11", "As Required"),
            masterItem("INS-12", "Stat (Immediately)")
        );
        return ResponseEntity.ok(ApiResponse.ok("OK", instructions));
    }

    // ── Route Master ──────────────────────────────────────────────────────────

    @GetMapping("/route")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getRoutes() {
        List<Map<String, Object>> routes = List.of(
            masterItem("RT-1",  "Oral"),
            masterItem("RT-2",  "Intravenous (IV)"),
            masterItem("RT-3",  "Intramuscular (IM)"),
            masterItem("RT-4",  "Subcutaneous (SC)"),
            masterItem("RT-5",  "Topical"),
            masterItem("RT-6",  "Sublingual"),
            masterItem("RT-7",  "Inhalation"),
            masterItem("RT-8",  "Intranasal"),
            masterItem("RT-9",  "Ophthalmic"),
            masterItem("RT-10", "Otic (Ear)"),
            masterItem("RT-11", "Rectal"),
            masterItem("RT-12", "Transdermal")
        );
        return ResponseEntity.ok(ApiResponse.ok("OK", routes));
    }

    // ── Diagnostic Test Catalog Search ────────────────────────────────────────

    @GetMapping("/diagnostic/test-catalog")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> searchTestCatalog(
            @RequestParam(required = false, defaultValue = "") String search,
            @RequestParam(defaultValue = "20") int limit) {
        List<DiagnosticTemplate> all = diagTemplateRepo.findAllActive();
        String q = search.trim().toLowerCase();
        List<Map<String, Object>> results = all.stream()
            .filter(t -> q.isEmpty() || t.getName().toLowerCase().contains(q))
            .limit(limit)
            .map(t -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id",       t.getId().toString());
                m.put("name",     t.getName());
                m.put("testCode", "");
                m.put("category", t.getDiagnosticType() != null ? t.getDiagnosticType().name() : "LAB");
                return m;
            })
            .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok("OK", results));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Map<String, Object> masterItem(String id, String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id); m.put("name", name);
        return m;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> computeFrequentlyUsed(String consultantId, String type) {
        if (consultantId == null || consultantId.isBlank()) return List.of();
        Map<String, Map<String, Object>> countMap = new LinkedHashMap<>();
        List<ClinicalEncounter> allEncounters;
        try {
            allEncounters = encounterRepo.findAll(
                PageRequest.of(0, 500, Sort.by("startedAt").descending())).getContent();
        } catch (Exception e) { return List.of(); }
        String mapKey = "DRUG".equals(type) ? "prescriptions" : "diagnostic_orders";
        for (ClinicalEncounter enc : allEncounters) {
            if (enc.getConsultantShareMap() == null) continue;
            Object raw = enc.getConsultantShareMap().get(mapKey);
            if (!(raw instanceof List<?> list)) continue;
            for (Object item : list) {
                if (!(item instanceof Map<?,?> m)) continue;
                Map<String, Object> mm = (Map<String, Object>) m;
                String reqById = str(mm.get("requestedById"));
                if (reqById != null && !reqById.equals(consultantId)) continue;
                Object rawItems = mm.get("items");
                if (!(rawItems instanceof List<?> items)) continue;
                for (Object li : items) {
                    if (!(li instanceof Map<?,?> lm)) continue;
                    Map<String, Object> l = (Map<String, Object>) lm;
                    String itemId   = "DRUG".equals(type) ? str(l.get("drugItemId"))   : str(l.get("diagnosticTestId"));
                    String itemName = "DRUG".equals(type) ? str(l.get("drugName"))      : str(l.get("testName"));
                    if (itemId == null || itemId.isBlank()) continue;
                    final Map<String, Object> lineRef = (Map<String, Object>) lm;
                    countMap.compute(itemId, (k, existing) -> {
                        if (existing == null) {
                            Map<String, Object> entry = new LinkedHashMap<>();
                            entry.put("itemId", itemId); entry.put("itemName", itemName); entry.put("count", 1);
                            if ("DRUG".equals(type)) {
                                entry.put("frequency",        str(lineRef.get("frequency")));
                                entry.put("duration",         str(lineRef.get("duration")));
                                entry.put("instructionLabel", str(lineRef.get("instructionLabel")));
                                entry.put("routeLabel",       str(lineRef.get("routeLabel")));
                            }
                            return entry;
                        } else { existing.put("count", ((int) existing.get("count")) + 1); return existing; }
                    });
                }
            }
        }
        return countMap.values().stream()
            .sorted((a, b) -> Integer.compare((int) b.get("count"), (int) a.get("count")))
            .limit(10).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> computeLastPrescribed(String encounterId) {
        if (encounterId == null || encounterId.isBlank()) return List.of();
        UUID eid;
        try { eid = UUID.fromString(encounterId); } catch (Exception e) { return List.of(); }
        ClinicalEncounter current;
        try { current = encounterRepo.findById(eid).orElse(null); } catch (Exception e) { return List.of(); }
        if (current == null || current.getPatientId() == null) return List.of();
        List<ClinicalEncounter> priorEncounters = encounterRepo
            .findByPatientIdPaged(current.getPatientId(),
                PageRequest.of(0, 10, Sort.by("startedAt").descending())).getContent();
        for (ClinicalEncounter enc : priorEncounters) {
            if (enc.getId().equals(eid)) continue;
            if (enc.getConsultantShareMap() == null) continue;
            Object raw = enc.getConsultantShareMap().get("prescriptions");
            if (!(raw instanceof List<?> plist) || plist.isEmpty()) continue;
            Object lastPrx = plist.get(plist.size() - 1);
            if (!(lastPrx instanceof Map<?,?> pm)) continue;
            Map<String, Object> prxMap = (Map<String, Object>) pm;
            Object rawItems = prxMap.get("items");
            if (!(rawItems instanceof List<?> items)) continue;
            List<Map<String, Object>> lines = new ArrayList<>();
            for (Object li : items) {
                if (li instanceof Map<?,?> lm) lines.add(new LinkedHashMap<>((Map<String, Object>) lm));
            }
            return lines;
        }
        return List.of();
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }
}
