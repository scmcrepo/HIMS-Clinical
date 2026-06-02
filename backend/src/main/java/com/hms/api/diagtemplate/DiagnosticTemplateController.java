package com.hms.api.diagtemplate;

import com.hms.api.shared.ApiResponse;
import com.hms.domain.diagnostic.model.*;
import com.hms.infrastructure.persistence.diagtemplate.DiagnosticTemplateJpaRepository;
import com.hms.infrastructure.persistence.diagtemplate.LabTemplateDetailJpaRepository;
import com.hms.domain.shared.model.Department;
import com.hms.infrastructure.persistence.department.DepartmentJpaRepository;
import com.hms.infrastructure.persistence.charge.ChargeJpaRepository;
import com.hms.infrastructure.persistence.specimen.SpecimenJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * DiagnosticTemplateController — lab/radiology templates with departments and parameters.
 */
@RestController
@RequestMapping("/diagTemplate")
@RequiredArgsConstructor
public class DiagnosticTemplateController {

    private final DiagnosticTemplateJpaRepository templateRepo;
    private final LabTemplateDetailJpaRepository labDetailRepo;
    private final DepartmentJpaRepository departmentRepo;
    private final ChargeJpaRepository chargeRepo;
    private final SpecimenJpaRepository specimenRepo;

    private void populateTransientFields(Collection<DiagnosticTemplate> templates) {
        if (templates == null || templates.isEmpty()) return;
        for (DiagnosticTemplate t : templates) {
            if (t.getChargeId() != null) {
                chargeRepo.findById(t.getChargeId()).ifPresent(c -> t.setChargeName(c.getName()));
            }
            if (t.getSpecimenId() != null) {
                specimenRepo.findById(t.getSpecimenId()).ifPresent(s -> t.setSpecimenName(s.getName()));
            }
        }
    }

    private DiagnosticTemplate populateTransientFields(DiagnosticTemplate t) {
        if (t == null) return null;
        if (t.getChargeId() != null) {
            chargeRepo.findById(t.getChargeId()).ifPresent(c -> t.setChargeName(c.getName()));
        }
        if (t.getSpecimenId() != null) {
            specimenRepo.findById(t.getSpecimenId()).ifPresent(s -> t.setSpecimenName(s.getName()));
        }
        return t;
    }

    /** POST /diagTemplate?isNew= */
    @PostMapping
    public ResponseEntity<ApiResponse<DiagnosticTemplate>> createOrUpdate(
            @RequestParam(name = "isNew", defaultValue = "true") boolean isNew,
            @RequestBody DiagnosticTemplate req) {
        DiagnosticTemplate saved = templateRepo.save(req);
        populateTransientFields(saved);
        return ResponseEntity.status(isNew ? HttpStatus.CREATED : HttpStatus.OK)
            .body(ApiResponse.ok("Template saved successfully", saved));
    }

    /** GET /diagTemplate/getTemplatesByDiagnostic?diagnosticsId= */
    @GetMapping("/getTemplatesByDiagnostic")
    public ResponseEntity<ApiResponse<List<DiagnosticTemplate>>> getByDiagnostic(
            @RequestParam(name = "diagnosticsId") UUID diagnosticsId) {
        List<DiagnosticTemplate> list = templateRepo.findAllActive();
        populateTransientFields(list);
        return ResponseEntity.ok(ApiResponse.ok("OK", list));
    }

    /** GET /diagTemplate/getLabDetailsByCharge?chargeId= */
    @GetMapping("/getLabDetailsByCharge")
    public ResponseEntity<ApiResponse<List<DiagnosticTemplate>>> getByCharge(
            @RequestParam(name = "chargeId") UUID chargeId) {
        List<DiagnosticTemplate> list = templateRepo.findByChargeId(chargeId);
        populateTransientFields(list);
        return ResponseEntity.ok(ApiResponse.ok("OK", list));
    }

    /** GET /diagTemplate/labTemplateTypes */
    @GetMapping("/labTemplateTypes")
    public ResponseEntity<ApiResponse<String[]>> getTypes() {
        return ResponseEntity.ok(ApiResponse.ok("OK",
            new String[]{"NUMERIC", "TEXT", "HEADER", "PANEL"}));
    }

    /** POST /diagTemplate/updateLabTemplate?isNew= */
    @PostMapping("/updateLabTemplate")
    public ResponseEntity<ApiResponse<DiagnosticTemplate>> updateLabTemplate(
            @RequestParam(name = "isNew", defaultValue = "false") boolean isNew,
            @RequestBody DiagnosticTemplate req) {
        DiagnosticTemplate saved = templateRepo.save(req);
        populateTransientFields(saved);
        return ResponseEntity.ok(ApiResponse.ok("Template updated successfully", saved));
    }

    /** GET /diagTemplate/departments */
    @GetMapping("/departments")
    public ResponseEntity<ApiResponse<List<Department>>> getDepartments() {
        List<Department> diagnosticsDepartments = departmentRepo.findAllActive().stream()
                .filter(d -> "Diagnostics".equalsIgnoreCase(d.getDepartmentType()) || "DIAGNOSTICS".equalsIgnoreCase(d.getDepartmentType()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok("OK", diagnosticsDepartments));
    }

    /** GET /diagTemplate/all — returns all templates with lab details populated */
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<DiagnosticTemplate>>> getAll() {
        List<DiagnosticTemplate> list = templateRepo.findAllActive();
        populateTransientFields(list);
        return ResponseEntity.ok(ApiResponse.ok("OK", list));
    }

    @GetMapping("/page")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<DiagnosticTemplate>>> getPaginated(
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String value) {
        
        List<DiagnosticTemplate> all = templateRepo.findAllActive();
        
        if (value != null && !value.isBlank()) {
            String lowerValue = value.toLowerCase();
            all = all.stream().filter(e -> {
                try {
                    java.lang.reflect.Method m = e.getClass().getMethod("getName");
                    Object res = m.invoke(e);
                    if (res != null && res.toString().toLowerCase().contains(lowerValue)) return true;
                } catch(Exception ex) {}
                try {
                    java.lang.reflect.Method m = e.getClass().getMethod("getFirstName");
                    Object res = m.invoke(e);
                    if (res != null && res.toString().toLowerCase().contains(lowerValue)) return true;
                } catch(Exception ex) {}
                try {
                    java.lang.reflect.Method m = e.getClass().getMethod("getUsername");
                    Object res = m.invoke(e);
                    if (res != null && res.toString().toLowerCase().contains(lowerValue)) return true;
                } catch(Exception ex) {}
                try {
                    java.lang.reflect.Method m = e.getClass().getMethod("getPrefix");
                    Object res = m.invoke(e);
                    if (res != null && res.toString().toLowerCase().contains(lowerValue)) return true;
                } catch(Exception ex) {}
                return false;
            }).toList();
        }
        
        int total = all.size();
        int startIndex = Math.min(start, total);
        int endIndex = Math.min(start + limit, total);
        List<DiagnosticTemplate> pageContent = startIndex <= endIndex ? all.subList(startIndex, endIndex) : new java.util.ArrayList<>();
        
        populateTransientFields(pageContent);

        org.springframework.data.domain.Page<DiagnosticTemplate> page = new org.springframework.data.domain.PageImpl<>(
            pageContent, 
            org.springframework.data.domain.PageRequest.of(start / Math.max(limit, 1), Math.max(limit, 1)), 
            total
        );
        return ResponseEntity.ok(ApiResponse.ok("OK", page));
    }

}
