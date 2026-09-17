package com.hms.api.payor;
import org.springframework.transaction.annotation.Transactional;
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
@Transactional(readOnly = true)
public class PayorController {
    private final PayorJpaRepository repo;
    @GetMapping public ResponseEntity<ApiResponse<List<Payor>>> getAll() { return ResponseEntity.ok(ApiResponse.ok("OK", repo.findAllOrdered())); }
    @GetMapping("/{id}") public ResponseEntity<ApiResponse<Payor>> getById(@PathVariable("id") String id) {
        return ResponseEntity.ok(ApiResponse.ok("OK", repo.findById(UUID.fromString(id)).orElseThrow(() -> new ResourceNotFoundException("Payor", UUID.fromString(id)))));
    }
    @PostMapping @PreAuthorize("hasPermission('SETTINGS_PAYERTYPE','')")
    @Transactional
    public ResponseEntity<ApiResponse<Payor>> create(@RequestBody Payor req) {
        applyGstin(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("PayorType information Saved successfully", repo.save(req)));
    }
    @PutMapping @PreAuthorize("hasPermission('SETTINGS_PAYERTYPE','')")
    @Transactional
    public ResponseEntity<ApiResponse<Payor>> update(@RequestBody Payor req) {
        if (req.getId() == null) return (ResponseEntity) ResponseEntity.badRequest().body(ApiResponse.error("id required"));
        applyGstin(req);
        return ResponseEntity.ok(ApiResponse.ok("PayorType information updated successfully", repo.save(req)));
    }

    

    /**
     * Normalises and validates the GSTIN, and derives the state code from it.
     *
     * <p>The state code is always recomputed rather than accepted from the request, so it
     * cannot drift from the GSTIN sitting above it — that pair is what decides whether a
     * supply to this payor is intra-state or inter-state.
     */
    private void applyGstin(Payor req) {
        String gstin = com.hms.domain.gst.model.Gstin.normalise(req.getGstin());
        com.hms.domain.gst.model.Gstin.requireValid(gstin);
        req.setGstin(gstin == null || gstin.isBlank() ? null : gstin);
        req.setStateCode(com.hms.domain.gst.model.Gstin.stateCode(gstin));
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
