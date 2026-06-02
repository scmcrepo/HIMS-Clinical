package com.hms.api.payor;
import com.hms.api.shared.ApiResponse;
import com.hms.domain.shared.model.ReqDataStatus;
import com.hms.infrastructure.persistence.shared.DataStatusSpec;
import com.hms.domain.patient.model.Payor;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.payor.PayorJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*; import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/payerType") @RequiredArgsConstructor
public class PayorController {
    private final PayorJpaRepository repo;
    @GetMapping public ResponseEntity<ApiResponse<List<Payor>>> getAll() { return ResponseEntity.ok(ApiResponse.ok("OK", repo.findAllOrdered())); }
    @GetMapping("/{id}") public ResponseEntity<ApiResponse<Payor>> getById(@PathVariable("id") String id) {
        return ResponseEntity.ok(ApiResponse.ok("OK", repo.findById(UUID.fromString(id)).orElseThrow(() -> new ResourceNotFoundException("Payor", UUID.fromString(id)))));
    }
    @PostMapping @PreAuthorize("hasPermission('SETTINGS_PAYERTYPE','')")
    public ResponseEntity<ApiResponse<Payor>> create(@RequestBody Payor req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("PayerType information Saved successfully", repo.save(req)));
    }
    @PutMapping @PreAuthorize("hasPermission('SETTINGS_PAYERTYPE','')")
    public ResponseEntity<ApiResponse<Payor>> update(@RequestBody Payor req) {
        if (req.getId() == null) return (ResponseEntity) ResponseEntity.badRequest().body(ApiResponse.error("id required"));
        return ResponseEntity.ok(ApiResponse.ok("PayerType information updated successfully", repo.save(req)));
    }

    

    @GetMapping("/page")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<Payor>>> getPaginated(
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String value) {
        
        List<Payor> all = repo.findAllOrdered();
        
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
        List<Payor> pageContent = startIndex <= endIndex ? all.subList(startIndex, endIndex) : new java.util.ArrayList<>();
        
        org.springframework.data.domain.Page<Payor> page = new org.springframework.data.domain.PageImpl<>(
            pageContent, 
            org.springframework.data.domain.PageRequest.of(start / Math.max(limit, 1), Math.max(limit, 1)), 
            total
        );
        return ResponseEntity.ok(ApiResponse.ok("OK", page));
    }

}
