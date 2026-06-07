package com.hms.api.casesheet;
import org.springframework.security.access.prepost.PreAuthorize;

import com.hms.api.casesheet.request.CreateTemplateRequest;
import com.hms.api.casesheet.request.UpdateTemplateRequest;
import com.hms.api.casesheet.response.CaseSheetTemplateDetail;
import com.hms.api.casesheet.response.CaseSheetTemplateSummary;
import com.hms.api.shared.ApiResponse;
import com.hms.application.casesheet.CaseSheetService;
import com.hms.domain.casesheet.model.CaseSheetVisitType;
import com.hms.domain.shared.model.EntityStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Admin API for managing case sheet templates (form layouts).
 * GET  /case-sheet-templates                           — list all (filter by ?specialization=&visitType=)
 * GET  /case-sheet-templates/default?spec=&visitType= — get default for specialization+visitType
 * GET  /case-sheet-templates/{id}                     — full template with fields
 * POST /case-sheet-templates                          — create
 * PUT  /case-sheet-templates/{id}                     — update
 * DELETE /case-sheet-templates/{id}                   — soft delete
 */
@RestController
@RequestMapping("/case-sheet-templates")
@RequiredArgsConstructor
@PreAuthorize("hasPermission('SETTINGS_CASESHEET_TEMPLATE','')")
public class CaseSheetTemplateController {

    private final CaseSheetService svc;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CaseSheetTemplateSummary>>> list(
            @RequestParam(required = false) String specialization,
            @RequestParam(required = false) CaseSheetVisitType visitType,
            @RequestParam(required = false) EntityStatus status) {
        return ResponseEntity.ok(ApiResponse.ok("OK", svc.listTemplates(specialization, visitType, status)));
    }

    @GetMapping("/default")
    public ResponseEntity<ApiResponse<CaseSheetTemplateDetail>> getDefault(
            @RequestParam String specialization,
            @RequestParam CaseSheetVisitType visitType) {
        return ResponseEntity.ok(ApiResponse.ok("OK", svc.getDefaultTemplate(specialization, visitType)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CaseSheetTemplateDetail>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("OK", svc.getTemplate(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CaseSheetTemplateDetail>> create(
            @Valid @RequestBody CreateTemplateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Template created", svc.createTemplate(req)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CaseSheetTemplateDetail>> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateTemplateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Template updated", svc.updateTemplate(id, req)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        svc.deleteTemplate(id);
        return ResponseEntity.ok(ApiResponse.ok("Template deleted"));
    }
}
