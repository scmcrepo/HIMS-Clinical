package com.hms.api.bulkupload;

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
public class DataImportController {

    private final BulkImportService importService;

    @PostMapping(value = "/{entityType}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ImportResult>> importCsv(
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

        ImportResult result = importService.importCsv(entityType.toLowerCase(), file);

        // Return 207 Multi-Status if there were partial failures
        int status = result.errorCount() > 0 && result.createdCount() > 0
            ? 207
            : result.errorCount() > 0 && result.createdCount() == 0
                ? HttpStatus.UNPROCESSABLE_ENTITY.value()
                : HttpStatus.CREATED.value();

        return ResponseEntity.status(status)
            .body(ApiResponse.ok(
                String.format("Import complete: %d created, %d skipped, %d errors",
                    result.createdCount(), result.skippedCount(), result.errorCount()),
                result));
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
