package com.hms.api.template;

import com.hms.api.shared.ApiResponse;
import com.hms.application.template.TemplateService;
import com.hms.domain.shared.model.Template;
import com.hms.domain.shared.model.CommonTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/template")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    @GetMapping("/getTemplatesByName")
    public ResponseEntity<ApiResponse<List<Template>>> getTemplatesByName(
            @RequestParam("type") String type,
            @RequestParam(value = "name", defaultValue = "") String name) {
        
        CommonTemplate templateType = CommonTemplate.CLINICAL;
        try {
            templateType = CommonTemplate.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            // fallback
        }
        
        List<Template> templates = templateService.getTemplateByTypeAndName(templateType, name);
        return ResponseEntity.ok(ApiResponse.ok("OK", templates));
    }

    @GetMapping("/getDepartmentTemplateByDepartmentId/{id}")
    public ResponseEntity<ApiResponse<List<com.hms.domain.casesheet.model.CaseSheetTemplate>>> getDepartmentTemplateByDepartmentId(@PathVariable UUID id) {
        List<com.hms.domain.casesheet.model.CaseSheetTemplate> templates = templateService.getDepartmentTemplateByDepartmentId(id);
        return ResponseEntity.ok(ApiResponse.ok("OK", templates));
    }

    @GetMapping("/removeDepartmentTemplates/{id}/{dptId}")
    public ResponseEntity<ApiResponse<Void>> removeDepartmentTemplates(
            @PathVariable UUID id, 
            @PathVariable UUID dptId) {
        templateService.removeDepartmentTemplates(id, dptId);
        return ResponseEntity.ok(ApiResponse.ok("Removed successfully"));
    }
}
