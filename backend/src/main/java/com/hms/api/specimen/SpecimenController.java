package com.hms.api.specimen;
import com.hms.api.shared.ApiResponse;
import com.hms.domain.shared.model.ReqDataStatus;
import com.hms.infrastructure.persistence.shared.DataStatusSpec;
import com.hms.domain.diagnostic.model.Specimen;
import com.hms.infrastructure.persistence.specimen.SpecimenJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/specimen") @RequiredArgsConstructor
@PreAuthorize("hasPermission('SETTINGS_SPECIMEN','')")
public class SpecimenController {
    private final SpecimenJpaRepository repo;

    /** Management page — returns ALL non-deleted specimens (ACTIVE + INACTIVE) */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Specimen>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok("OK", repo.findAllNonDeleted()));
    }

    /** Clinical dropdowns — returns only ACTIVE specimens */
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<Specimen>>> getActive() {
        return ResponseEntity.ok(ApiResponse.ok("OK", repo.findAllActive()));
    }

    @PostMapping public ResponseEntity<ApiResponse<Specimen>> create(@RequestBody Specimen req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Specimen saved successfully", repo.save(req)));
    }
    @PutMapping public ResponseEntity<ApiResponse<Specimen>> update(@RequestBody Specimen req) {
        return ResponseEntity.ok(ApiResponse.ok("Specimen updated successfully", repo.save(req)));
    }

    

    @GetMapping("/page")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<Specimen>>> getPaginated(
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String value) {
        
        List<Specimen> all = repo.findAllNonDeleted();
        
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
        List<Specimen> pageContent = startIndex <= endIndex ? all.subList(startIndex, endIndex) : new java.util.ArrayList<>();
        
        org.springframework.data.domain.Page<Specimen> page = new org.springframework.data.domain.PageImpl<>(
            pageContent, 
            org.springframework.data.domain.PageRequest.of(start / Math.max(limit, 1), Math.max(limit, 1)), 
            total
        );
        return ResponseEntity.ok(ApiResponse.ok("OK", page));
    }

}
