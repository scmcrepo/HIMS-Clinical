package com.hms.api.accountunit;
import com.hms.api.shared.ApiResponse;
import com.hms.domain.shared.model.ReqDataStatus;
import com.hms.infrastructure.persistence.shared.DataStatusSpec;
import com.hms.domain.shared.model.AccountUnit;
import com.hms.infrastructure.persistence.accountunit.AccountUnitJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/accountUnit") @RequiredArgsConstructor
public class AccountUnitController {
    private final AccountUnitJpaRepository repo;
    @GetMapping @PreAuthorize("hasPermission('SETTINGS_ACCOUNTUNIT','')")
    public ResponseEntity<ApiResponse<List<AccountUnit>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok("OK", repo.findAllActive()));
    }
    @PostMapping @PreAuthorize("hasPermission('SETTINGS_ACCOUNTUNIT','')")
    public ResponseEntity<ApiResponse<AccountUnit>> create(@RequestBody AccountUnit req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Account Unit information saved successfully", repo.save(req)));
    }
    @PutMapping @PreAuthorize("hasPermission('SETTINGS_ACCOUNTUNIT','')")
    public ResponseEntity<ApiResponse<AccountUnit>> update(@RequestBody AccountUnit req) {
        return ResponseEntity.ok(ApiResponse.ok("Account Unit information updated successfully", repo.save(req)));
    }

    

    @GetMapping("/page")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<AccountUnit>>> getPaginated(
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String value) {
        
        List<AccountUnit> all = repo.findAllActive();
        
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
        List<AccountUnit> pageContent = startIndex <= endIndex ? all.subList(startIndex, endIndex) : new java.util.ArrayList<>();
        
        org.springframework.data.domain.Page<AccountUnit> page = new org.springframework.data.domain.PageImpl<>(
            pageContent, 
            org.springframework.data.domain.PageRequest.of(start / Math.max(limit, 1), Math.max(limit, 1)), 
            total
        );
        return ResponseEntity.ok(ApiResponse.ok("OK", page));
    }

}
