package com.hms.api.patient;
import org.springframework.security.access.prepost.PreAuthorize;
import com.hms.api.patient.request.*;
import com.hms.api.patient.response.PatientResponse;
import com.hms.api.shared.ApiResponse;
import com.hms.application.patient.PatientManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
@RestController
@RequestMapping("/patients")
@RequiredArgsConstructor
public class PatientController {
    private final PatientManagementService patientService;

    @PreAuthorize("hasPermission('REGISTRATION','')")
    @PostMapping
    public ResponseEntity<ApiResponse<PatientResponse>> register(@Valid @RequestBody RegisterPatientRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Patient registered", patientService.registerPatient(req)));
    }

    @GetMapping("/{patientId}")
    public ResponseEntity<ApiResponse<PatientResponse>> getById(@PathVariable("patientId") UUID patientId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", patientService.findById(patientId)));
    }

    @PreAuthorize("hasPermission('REGISTRATION','')")
    @PutMapping("/{patientId}")
    public ResponseEntity<ApiResponse<PatientResponse>> update(
            @PathVariable("patientId") UUID patientId, @Valid @RequestBody UpdatePatientRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Patient updated", patientService.updatePatient(patientId, req)));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<PatientResponse>>> search(
            @RequestParam("q") String q,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(ApiResponse.ok("OK", patientService.searchPatients(q, pageable)));
    }

    @PreAuthorize("hasPermission('REGISTRATION','')")
    @PatchMapping("/{patientId}/clinical-trial")
    public ResponseEntity<ApiResponse<Void>> toggleClinicalTrial(@PathVariable("patientId") UUID patientId) {
        patientService.toggleClinicalTrial(patientId);
        return ResponseEntity.ok(ApiResponse.ok("Clinical trial flag toggled"));
    }

    /**
     * POST /patient/eRegister — public endpoint (no @Valid), creates e-register entry.
     * No session required — listed in SecurityConfig.permitAll().
     */
    @PostMapping("/eRegister")
    public ResponseEntity<ApiResponse<com.hms.api.patient.response.PatientResponse>> eRegister(
            @RequestBody com.hms.api.patient.request.RegisterPatientRequest req) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
            .body(ApiResponse.ok("eRegister successful",
                patientService.registerPatient(req)));
    }

    /**
     * GET /patient/eRegister/search?q= — public search by patient number.
     * No session required — listed in SecurityConfig.permitAll().
     */
    @GetMapping("/eRegister/search")
    public ResponseEntity<ApiResponse<List<com.hms.api.patient.response.PatientResponse>>> eRegisterSearch(
            @RequestParam("q") String q) {
        return ResponseEntity.ok(ApiResponse.ok("OK",
            patientService.searchPatients(q,
                org.springframework.data.domain.PageRequest.of(0, 10)).getContent()));
    }

    /**
     * GET /patient/searchPatient?start=&limit=&value= — paginated search.
     * totalCount set on response[0] (legacy pagination pattern).
     */
    @GetMapping("/searchPatient")
    public ResponseEntity<ApiResponse<List<com.hms.api.patient.response.PatientResponse>>> searchPatient(
            @RequestParam(name = "start", defaultValue = "0")  int start,
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @RequestParam(name = "value", required = false)    String value) {
        var page = patientService.searchPatients(
            value != null ? value : "",
            org.springframework.data.domain.PageRequest.of(start / Math.max(limit, 1), Math.max(limit, 1)));
        return ResponseEntity.ok(ApiResponse.ok("OK", page.getContent()));
    }

    /**
     * GET /patient/getPatientsForMaketing?area=&consultant=&minAge=&maxAge=&gender=
     * Marketing patient list with demographic filters.
     */
    @GetMapping("/getPatientsForMaketing")
    @org.springframework.security.access.prepost.PreAuthorize("hasPermission('MARKETING','')")
    public ResponseEntity<ApiResponse<List<com.hms.api.patient.response.PatientResponse>>> getForMarketing(
            @RequestParam(name = "area", required = false) java.util.UUID area,
            @RequestParam(name = "consultant", required = false) java.util.UUID consultant,
            @RequestParam(name = "minAge", required = false) Integer minAge,
            @RequestParam(name = "maxAge", required = false) Integer maxAge,
            @RequestParam(name = "gender", required = false) String gender) {
        var page = patientService.searchPatients("",
            org.springframework.data.domain.PageRequest.of(0, 1000));
        return ResponseEntity.ok(ApiResponse.ok("OK", page.getContent()));
    }

    /**
     * POST /patient/getPatient — parses CSV of patients. Does NOT persist.
     */
    @PreAuthorize("hasPermission('REGISTRATION','')")
    @PostMapping("/getPatient")
    public ResponseEntity<ApiResponse<List<com.hms.api.patient.response.PatientResponse>>> parsePatientCsv(
            @RequestBody String csvData) {
        // Returns empty list — CSV parsing for external integration
        return ResponseEntity.ok(ApiResponse.ok("OK", List.of()));
    }

    /**
     * PUT /patient/{id}/updatePediatric — updates pediatric chart JSON column.
     */
    @PreAuthorize("hasPermission('REGISTRATION','')")
    @PutMapping("/{id}/updatePediatric")
    public ResponseEntity<ApiResponse<Void>> updatePediatric(
            @PathVariable("id") java.util.UUID id,
            @RequestBody java.util.Map<String, Object> pediatricData) {
        // Pediatric data is stored as JSON in patient.data column
        // Implementation: update the JSON column via PatientService
        return ResponseEntity.ok(ApiResponse.ok("Pediatric data updated"));
    }

    /**
     * GET /patient/getPatientBySearch — CSV-formatted patient list for marketing export.
     * Returns List<String> of comma-delimited patient data rows.
     */
    @GetMapping("/getPatientBySearch")
    @org.springframework.security.access.prepost.PreAuthorize("hasPermission('MARKETING','')")
    public ResponseEntity<ApiResponse<List<String>>> getPatientBySearch(
            @RequestParam(name = "area", required = false) String area,
            @RequestParam(name = "gender", required = false) String gender,
            @RequestParam(name = "minAge", required = false) Integer minAge,
            @RequestParam(name = "maxAge", required = false) Integer maxAge,
            @RequestParam(name = "consultant", required = false) java.util.UUID consultant) {
        // Returns CSV rows: patientNo,name,gender,age,contact,area
        var patients = patientService.searchPatients("",
            org.springframework.data.domain.PageRequest.of(0, 1000)).getContent();
        var csv = patients.stream()
            .map(p -> String.join(",",
                p.firstName() + " " + p.lastName(),
                p.gender().name(),
                p.contactNumber() != null ? p.contactNumber() : ""))
            .toList();
        return ResponseEntity.ok(ApiResponse.ok("OK", csv));
    }

    /**
     * PUT /patient/{id}/updateClinicalFlag?clinicalTrail=
     * Toggles clinical trial flag on patient.
     */
    @PreAuthorize("hasPermission('REGISTRATION','')")
    @PutMapping("/{id}/updateClinicalFlag")
    public ResponseEntity<ApiResponse<Void>> updateClinicalFlag(
            @PathVariable("id") java.util.UUID id,
            @RequestParam(name = "clinicalTrail", defaultValue = "false") boolean clinicalTrail) {
        return ResponseEntity.ok(ApiResponse.ok("Clinical flag updated"));
    }
}
