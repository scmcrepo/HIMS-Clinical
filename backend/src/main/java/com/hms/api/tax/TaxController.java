package com.hms.api.tax;
import org.springframework.security.access.prepost.PreAuthorize;
import com.hms.api.shared.ApiResponse;
import com.hms.domain.inventory.model.*;
import com.hms.infrastructure.persistence.tax.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/tax") @RequiredArgsConstructor
public class TaxController {
    private final TaxJpaRepository taxRepo;
    private final TaxCategoryJpaRepository taxCategoryRepo;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Tax>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok("OK", taxRepo.findAllActive()));
    }
    @PostMapping
    @PreAuthorize("hasPermission('SETTINGS_TAX','')")
    public ResponseEntity<ApiResponse<Tax>> create(@RequestBody Tax req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Tax created successfully", taxRepo.save(req)));
    }
    @PutMapping
    @PreAuthorize("hasPermission('SETTINGS_TAX','')")
    public ResponseEntity<ApiResponse<Tax>> update(@RequestBody Tax req) {
        return ResponseEntity.ok(ApiResponse.ok("Tax updated successfully", taxRepo.save(req)));
    }
    @GetMapping("/taxTypes")
    public ResponseEntity<ApiResponse<String[]>> getTaxTypes() {
        return ResponseEntity.ok(ApiResponse.ok("OK", new String[]{"GST","IGST","SGST","CGST","VAT","CESS","NONE"}));
    }
    @GetMapping("/{id}/details")
    public ResponseEntity<ApiResponse<List<TaxCategory>>> getDetails(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("OK", taxCategoryRepo.findByTaxId(id)));
    }

    

    @GetMapping("/page")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<Tax>>> getPaginated(
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String value) {
        
        List<Tax> all = taxRepo.findAllActive();
        
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
        List<Tax> pageContent = startIndex <= endIndex ? all.subList(startIndex, endIndex) : new java.util.ArrayList<>();
        
        org.springframework.data.domain.Page<Tax> page = new org.springframework.data.domain.PageImpl<>(
            pageContent, 
            org.springframework.data.domain.PageRequest.of(start / Math.max(limit, 1), Math.max(limit, 1)), 
            total
        );
        return ResponseEntity.ok(ApiResponse.ok("OK", page));
    }

}
