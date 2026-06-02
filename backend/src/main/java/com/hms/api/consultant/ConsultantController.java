package com.hms.api.consultant;
import com.hms.api.shared.ApiResponse;
import com.hms.domain.shared.model.ReqDataStatus;
import com.hms.infrastructure.persistence.shared.DataStatusSpec;
import com.hms.application.consultant.ConsultantService;
import com.hms.domain.consultant.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;
@RestController @RequestMapping("/consultant") @RequiredArgsConstructor
public class ConsultantController {
    private final ConsultantService consultantService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasPermission('SETTINGS_CONSULTANT','')")
    public ResponseEntity<ApiResponse<Consultant>> create(
            @RequestPart("consultant") Consultant req,
            @RequestPart(value = "photo", required = false) MultipartFile photo) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Consultant saved successfully", consultantService.create(req, photo)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Consultant>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok("OK", consultantService.getAll()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('SETTINGS_CONSULTANT','')")
    public ResponseEntity<ApiResponse<Consultant>> getById(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("OK", consultantService.getById(id)));
    }

    @GetMapping("/types")
    public ResponseEntity<ApiResponse<ConsultantType[]>> getTypes() {
        return ResponseEntity.ok(ApiResponse.ok("OK", ConsultantType.values()));
    }

    @GetMapping("/getConsultantByName")
    public ResponseEntity<ApiResponse<List<Consultant>>> searchByName(@RequestParam(name = "name") String name) {
        return ResponseEntity.ok(ApiResponse.ok("OK", consultantService.searchByName(name)));
    }

    @GetMapping("/getConsultantByType")
    public ResponseEntity<ApiResponse<List<Consultant>>> getByType(@RequestParam(name = "type") ConsultantType type) {
        return ResponseEntity.ok(ApiResponse.ok("OK", consultantService.getByType(type)));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasPermission('SETTINGS_CONSULTANT','')")
    public ResponseEntity<ApiResponse<Consultant>> update(
            @PathVariable("id") UUID id,
            @RequestPart("consultant") Consultant req,
            @RequestPart(value = "photo", required = false) MultipartFile photo) throws IOException {
        return ResponseEntity.ok(ApiResponse.ok("Consultant updated successfully", consultantService.update(id, req, photo)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('SETTINGS_CONSULTANT','')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable("id") UUID id) {
        consultantService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Consultant deleted successfully"));
    }

    

    @GetMapping("/page")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<Consultant>>> getPaginated(
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String value) {
        
        List<Consultant> all = (value != null && !value.isBlank())
            ? consultantService.searchNonDeletedByName(value)
            : consultantService.getAllNonDeleted();
        
        int total = all.size();
        int startIndex = Math.min(start, total);
        int endIndex = Math.min(start + limit, total);
        List<Consultant> pageContent = startIndex <= endIndex ? all.subList(startIndex, endIndex) : new java.util.ArrayList<>();
        
        org.springframework.data.domain.Page<Consultant> page = new org.springframework.data.domain.PageImpl<>(
            pageContent, 
            org.springframework.data.domain.PageRequest.of(start / Math.max(limit, 1), Math.max(limit, 1)), 
            total
        );
        return ResponseEntity.ok(ApiResponse.ok("OK", page));
    }

}
