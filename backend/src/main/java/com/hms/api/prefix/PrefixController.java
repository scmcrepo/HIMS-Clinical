package com.hms.api.prefix;
import com.hms.api.prefix.request.CreateSequenceGeneratorRequest;
import com.hms.api.prefix.request.UpdateSequenceGeneratorRequest;
import com.hms.api.prefix.response.SequenceGeneratorResponse;
import com.hms.api.shared.ApiResponse;
import com.hms.application.prefix.SequenceGeneratorService;
import com.hms.domain.billing.model.DocumentType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
@RestController @RequestMapping("/prefix") @RequiredArgsConstructor
public class PrefixController {
    private final SequenceGeneratorService prefixService;

    @PostMapping
    public ResponseEntity<ApiResponse<SequenceGeneratorResponse>> create(@Valid @RequestBody CreateSequenceGeneratorRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Sequence generator created", prefixService.create(req)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SequenceGeneratorResponse>> update(@PathVariable UUID id, @Valid @RequestBody UpdateSequenceGeneratorRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Sequence generator updated", prefixService.update(id, req)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SequenceGeneratorResponse>>> getSummary() {
        return ResponseEntity.ok(ApiResponse.ok("OK", prefixService.getSummaryByDocumentType()));
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<SequenceGeneratorResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok("OK", prefixService.getAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SequenceGeneratorResponse>> getById(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("OK", prefixService.getById(id)));
    }

    @GetMapping("/history/{documentType}")
    public ResponseEntity<ApiResponse<List<SequenceGeneratorResponse>>> getHistory(@PathVariable("documentType") DocumentType documentType) {
        return ResponseEntity.ok(ApiResponse.ok("OK", prefixService.getHistory(documentType)));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<SequenceGeneratorResponse>> activate(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Sequence generator activated", prefixService.activate(id)));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<SequenceGeneratorResponse>> deactivate(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Sequence generator deactivated", prefixService.deactivate(id)));
    }

    /** GET /prefix/getPreviousPrefix — previous (deactivated) prefix configurations */
    @GetMapping("/getPreviousPrefix")
    public ResponseEntity<ApiResponse<List<SequenceGeneratorResponse>>> getPreviousPrefix() {
        return ResponseEntity.ok(ApiResponse.ok("OK",
            prefixService.getAll().stream()
                .filter(sg -> sg.deactivatedAt() != null)
                .toList()));
    }

    

    @GetMapping("/page")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<SequenceGeneratorResponse>>> getPaginated(
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String value) {
        
        List<SequenceGeneratorResponse> all = prefixService.getSummaryByDocumentType();
        
        if (value != null && !value.isBlank()) {
            String lowerValue = value.toLowerCase();
            all = all.stream().filter(e -> 
                (e.prefixString() != null && e.prefixString().toLowerCase().contains(lowerValue)) ||
                (e.documentType() != null && e.documentType().name().toLowerCase().contains(lowerValue))
            ).toList();
        }
        
        int total = all.size();
        int startIndex = Math.min(start, total);
        int endIndex = Math.min(start + limit, total);
        List<SequenceGeneratorResponse> pageContent = startIndex <= endIndex ? all.subList(startIndex, endIndex) : new java.util.ArrayList<>();
        
        org.springframework.data.domain.Page<SequenceGeneratorResponse> page = new org.springframework.data.domain.PageImpl<>(
            pageContent, 
            org.springframework.data.domain.PageRequest.of(start / Math.max(limit, 1), Math.max(limit, 1)), 
            total
        );
        return ResponseEntity.ok(ApiResponse.ok("OK", page));
    }

}
