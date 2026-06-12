package com.hms.api.casesheet;

import com.hms.api.casesheet.request.CreateDischargeTemplateRequest;
import com.hms.api.casesheet.request.UpdateDischargeTemplateRequest;
import com.hms.api.casesheet.response.DischargeTemplateDetail;
import com.hms.api.casesheet.response.DischargeTemplateSummary;
import com.hms.api.shared.ApiResponse;
import com.hms.application.casesheet.DischargeSummaryService;
import com.hms.domain.shared.model.EntityStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/discharge-summary-templates")
@RequiredArgsConstructor
@PreAuthorize("hasPermission('SETTINGS_CASESHEET_TEMPLATE','')")
public class DischargeSummaryTemplateController {

    private final DischargeSummaryService svc;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DischargeTemplateSummary>>> list(
            @RequestParam(required = false) String specialization,
            @RequestParam(required = false) EntityStatus status) {
        return ResponseEntity.ok(ApiResponse.ok("OK", svc.listTemplates(specialization, status)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DischargeTemplateDetail>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("OK", svc.getTemplate(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DischargeTemplateDetail>> create(
            @Valid @RequestBody CreateDischargeTemplateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Discharge template created", svc.createTemplate(req)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DischargeTemplateDetail>> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateDischargeTemplateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Discharge template updated", svc.updateTemplate(id, req)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        svc.deleteTemplate(id);
        return ResponseEntity.ok(ApiResponse.ok("Discharge template deleted"));
    }
}
