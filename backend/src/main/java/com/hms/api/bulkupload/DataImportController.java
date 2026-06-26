package com.hms.api.bulkupload;
import org.springframework.security.access.prepost.PreAuthorize;

import com.hms.api.shared.ApiResponse;
import com.hms.application.bulkupload.BulkImportService;
import com.hms.application.bulkupload.ImportResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * DataImportController — bulk CSV import for 16 entity types.
 *
 * Supported entityType values (mirrors legacy BulkUploadController):
 *   bed, bed_type, consultant, patient, charge, item, diagnostic_template,
 *   referral, payor, user, staff, department, molecule, category, order_set, stock
 *
 * Returns an ImportResult showing created/skipped/error counts and any row-level errors.
 */
@RestController
@RequestMapping({"/bulk-upload", "/bulkUpload"})
@RequiredArgsConstructor
@PreAuthorize("hasPermission('DATA_IMPORT','')")
public class DataImportController {

    private final BulkImportService importService;

    @PostMapping(value = "/{entityType}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<?>> importCsv(
            @PathVariable("entityType") String entityType,
            @RequestPart("file") MultipartFile file) {

        if (file.isEmpty()) {
            return (ResponseEntity) ResponseEntity.badRequest()
                .body(ApiResponse.error("CSV file is empty"));
        }

        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".csv") && !filename.endsWith(".CSV"))) {
            return (ResponseEntity) ResponseEntity.badRequest()
                .body(ApiResponse.error("Only CSV files are supported"));
        }

        java.util.UUID jobId = importService.submitImportJob(entityType.toLowerCase(), file);

        java.util.Map<String, Object> responseData = new java.util.HashMap<>();
        responseData.put("jobId", jobId);
        responseData.put("status", "PENDING");

        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(ApiResponse.ok("Import job submitted", responseData));
    }

    @GetMapping("/job/{jobId}")
    public ResponseEntity<ApiResponse<?>> getJobStatus(@PathVariable("jobId") java.util.UUID jobId) {
        return importService.getJob(jobId)
            .<ResponseEntity<ApiResponse<?>>>map(job -> ResponseEntity.ok(ApiResponse.ok("Job status", job)))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Job not found")));
    }

    /**
     * Returns the expected CSV column headers for a given entity type.
     * Clients use this to generate import templates.
     */
    @GetMapping("/{entityType}/template")
    public ResponseEntity<ApiResponse<java.util.List<String>>> getTemplate(
            @PathVariable("entityType") String entityType) {
        return ResponseEntity.ok(ApiResponse.ok("OK",
            importService.getExpectedHeaders(entityType.toLowerCase())));
    }

    /**
     * GET /bulkUpload/downloadCSV?name= — returns a blank CSV template for the entity type.
     * Duplicate of /{entityType}/template but at the SRS-specified URL.
     */
    @GetMapping("/downloadCSV")
    public ResponseEntity<String> downloadCsv(@RequestParam(name = "name") String name) {
        try {
            var headers = importService.getExpectedHeaders(name.toLowerCase());
            String csv = String.join(",", headers) + "\n";
            return ResponseEntity.ok()
                .header("Content-Type", "text/csv")
                .header("Content-Disposition", "attachment; filename=\"" + name + "_template.csv\"")
                .body(csv);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Unknown entity type: " + name);
        }
    }
}
