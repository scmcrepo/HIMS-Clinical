package com.hms.api.printtemplate;

import com.hms.api.printtemplate.request.PrintTemplateRequest;
import com.hms.api.printtemplate.response.PrintOutputResponse;
import com.hms.api.printtemplate.response.PrintTemplateResponse;
import com.hms.api.shared.ApiResponse;
import com.hms.application.print.PrintService;
import com.hms.domain.shared.model.PrintTemplate;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.printtemplate.PrintTemplateJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for the Print Template Engine.
 *
 * CRUD:  GET|POST /print-templates, PUT|DELETE /print-templates/{id}
 * Print: GET /print?templateType=X&id=Y  →  PrintOutputResponse
 */
@RestController
@RequiredArgsConstructor
public class PrintTemplateController {

    private final PrintTemplateJpaRepository repo;
    private final PrintService printService;

    // ── List ───────────────────────────────────────────────────────────────────

    @GetMapping("/print-templates")
    public ResponseEntity<ApiResponse<List<PrintTemplateResponse>>> getAll() {
        List<PrintTemplateResponse> list = repo.findAllActive().stream()
                .map(PrintTemplateResponse::from).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok("OK", list));
    }

    @GetMapping("/print-templates/page")
    public ResponseEntity<ApiResponse<Page<PrintTemplateResponse>>> getPaginated(
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String value) {

        List<PrintTemplate> all = repo.findAllActive();
        if (value != null && !value.isBlank()) {
            String lv = value.toLowerCase();
            all = all.stream().filter(e ->
                (e.getName() != null && e.getName().toLowerCase().contains(lv)) ||
                (e.getDocumentType() != null && e.getDocumentType().toLowerCase().contains(lv)) ||
                (e.getPrintMode() != null && e.getPrintMode().toLowerCase().contains(lv))
            ).toList();
        }
        int total = all.size();
        int from  = Math.min(start, total);
        int to    = Math.min(start + limit, total);
        List<PrintTemplateResponse> page = all.subList(from, to).stream()
                .map(PrintTemplateResponse::from).collect(Collectors.toList());
        Page<PrintTemplateResponse> result = new PageImpl<>(page,
                PageRequest.of(start / Math.max(limit, 1), Math.max(limit, 1)), total);
        return ResponseEntity.ok(ApiResponse.ok("OK", result));
    }

    @GetMapping("/print-templates/{id}")
    public ResponseEntity<ApiResponse<PrintTemplateResponse>> getById(@PathVariable UUID id) {
        PrintTemplate t = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PrintTemplate", id));
        return ResponseEntity.ok(ApiResponse.ok("OK", PrintTemplateResponse.from(t)));
    }

    @GetMapping("/print-templates/by-type/{documentType}")
    public ResponseEntity<ApiResponse<PrintTemplateResponse>> getByType(@PathVariable String documentType) {
        return repo.findDefaultByDocumentType(documentType)
                .map(t -> ResponseEntity.ok(ApiResponse.ok("OK", PrintTemplateResponse.from(t))))
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Create ─────────────────────────────────────────────────────────────────

    @PostMapping("/print-templates")
    public ResponseEntity<ApiResponse<PrintTemplateResponse>> create(@RequestBody PrintTemplateRequest req) {
        PrintTemplate t = req.toEntity(new PrintTemplate());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Print template created successfully",
                        PrintTemplateResponse.from(repo.save(t))));
    }

    // ── Update ─────────────────────────────────────────────────────────────────

    @PutMapping("/print-templates/{id}")
    public ResponseEntity<ApiResponse<PrintTemplateResponse>> update(
            @PathVariable UUID id,
            @RequestBody PrintTemplateRequest req) {
        PrintTemplate t = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PrintTemplate", id));
        req.toEntity(t);
        return ResponseEntity.ok(ApiResponse.ok("Print template updated successfully",
                PrintTemplateResponse.from(repo.save(t))));
    }

    /** Legacy PUT without path variable (backward compat with old frontend) */
    @PutMapping("/print-templates")
    public ResponseEntity<ApiResponse<PrintTemplateResponse>> updateLegacy(@RequestBody PrintTemplateRequest req) {
        // This endpoint is kept for backward compat; requires id in the body
        return ResponseEntity.badRequest().body(ApiResponse.ok("Use PUT /print-templates/{id}", null));
    }

    // ── Delete ─────────────────────────────────────────────────────────────────

    @DeleteMapping("/print-templates/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        PrintTemplate t = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PrintTemplate", id));
        t.softDelete();
        repo.save(t);
        return ResponseEntity.ok(ApiResponse.ok("Print template deleted successfully"));
    }

    // ── Print trigger ──────────────────────────────────────────────────────────

    /**
     * GET /print?templateType=BILL&id=uuid[&printerName=XYZ][&otherParams...]
     *
     * Returns compiled HTML (HTML mode) or raw ESC/POS pages (DOT_MATRIX mode)
     * plus page geometry for @page CSS injection.
     */
    @GetMapping("/print")
    public ResponseEntity<ApiResponse<PrintOutputResponse>> print(
            @RequestParam String templateType,
            @RequestParam(required = false) String printerName,
            @RequestParam Map<String, String> allParams) {

        Map<String, String> params = new java.util.HashMap<>(allParams);
        params.remove("templateType");
        if (printerName != null) params.put("printerName", printerName);

        PrintOutputResponse output = printService.print(templateType, params);
        return ResponseEntity.ok(ApiResponse.ok("OK", output));
    }
}
