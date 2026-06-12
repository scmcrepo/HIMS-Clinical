package com.hms.api.supplier;
import org.springframework.security.access.prepost.PreAuthorize;
import com.hms.api.shared.ApiResponse;
import com.hms.domain.shared.model.ReqDataStatus;
import com.hms.infrastructure.persistence.shared.DataStatusSpec;
import com.hms.domain.inventory.model.Supplier;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.supplier.SupplierJpaRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/suppliers") @RequiredArgsConstructor
public class SupplierController {
    private final SupplierJpaRepository repo;
    @GetMapping
    public ResponseEntity<ApiResponse<List<Supplier>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok("OK", repo.findAllActive()));
    }
    @PostMapping
    @PreAuthorize("hasPermission('SETTINGS_SUPPLIER','')")
    public ResponseEntity<ApiResponse<Supplier>> create(@Valid @RequestBody Supplier req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Supplier saved successfully", repo.save(req)));
    }
    @PutMapping("/{id}")
    @PreAuthorize("hasPermission('SETTINGS_SUPPLIER','')")
    public ResponseEntity<ApiResponse<Supplier>> update(@PathVariable("id") UUID id, @RequestBody Supplier req) {
        if (!repo.existsById(id)) throw new ResourceNotFoundException("Supplier", id);
        req.setId(id);
        return ResponseEntity.ok(ApiResponse.ok("Supplier updated successfully", repo.save(req)));
    }

    

    @GetMapping("/page")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<Supplier>>> getPaginated(
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String value) {
        
        List<Supplier> all = repo.findAllOrdered();
        
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
        List<Supplier> pageContent = startIndex <= endIndex ? all.subList(startIndex, endIndex) : new java.util.ArrayList<>();
        
        org.springframework.data.domain.Page<Supplier> page = new org.springframework.data.domain.PageImpl<>(
            pageContent, 
            org.springframework.data.domain.PageRequest.of(start / Math.max(limit, 1), Math.max(limit, 1)), 
            total
        );
        return ResponseEntity.ok(ApiResponse.ok("OK", page));
    }

}
