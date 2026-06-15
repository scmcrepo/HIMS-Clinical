package com.hms.api.role;
import com.hms.api.role.request.CreateRoleRequest;
import com.hms.api.role.response.RoleResponse;
import com.hms.api.feature.response.FeatureResponse;
import com.hms.api.shared.ApiResponse;
import com.hms.application.role.RoleManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/roles") @RequiredArgsConstructor
public class RoleController {
    private final RoleManagementService roleService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok("OK", roleService.getAll()));
    }

    @PostMapping
    @PreAuthorize("hasPermission('SETTINGS_ROLE', '')")
    public ResponseEntity<ApiResponse<RoleResponse>> create(@Valid @RequestBody CreateRoleRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Role saved successfully", roleService.createRole(req)));
    }

    @PutMapping("/{roleId}")
    @PreAuthorize("hasPermission('SETTINGS_ROLE', '')")
    public ResponseEntity<ApiResponse<RoleResponse>> update(
            @PathVariable("roleId") UUID roleId, @Valid @RequestBody CreateRoleRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Role updated successfully", roleService.updateRole(roleId, req)));
    }

    @GetMapping("/features")
    @PreAuthorize("hasPermission('SETTINGS_ROLE', '')")
    public ResponseEntity<ApiResponse<List<FeatureResponse>>> getAllFeatures() {
        return ResponseEntity.ok(ApiResponse.ok("OK", roleService.getAllFeatures()));
    }

    @GetMapping("/features/me")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> getMyFeatures(
            @RequestParam(name = "module", required = false) String module) {
        return ResponseEntity.ok(ApiResponse.ok("OK", roleService.getFeaturesForCurrentUser(module)));
    }

    

    @GetMapping("/page")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<RoleResponse>>> getPaginated(
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String value) {
        
        List<RoleResponse> all = roleService.getAll();
        
        if (value != null && !value.isBlank()) {
            String lowerValue = value.toLowerCase();
            all = all.stream().filter(e -> {
                try {
                    java.lang.reflect.Method m;
                    try {
                        m = e.getClass().getMethod("getName");
                    } catch(Exception ex) {
                        m = e.getClass().getMethod("name");
                    }
                    Object res = m.invoke(e);
                    if (res != null && res.toString().toLowerCase().contains(lowerValue)) return true;
                } catch(Exception ex) {}
                try {
                    java.lang.reflect.Method m;
                    try {
                        m = e.getClass().getMethod("getDescription");
                    } catch(Exception ex) {
                        m = e.getClass().getMethod("description");
                    }
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
        List<RoleResponse> pageContent = startIndex <= endIndex ? all.subList(startIndex, endIndex) : new java.util.ArrayList<>();
        
        org.springframework.data.domain.Page<RoleResponse> page = new org.springframework.data.domain.PageImpl<>(
            pageContent, 
            org.springframework.data.domain.PageRequest.of(start / Math.max(limit, 1), Math.max(limit, 1)), 
            total
        );
        return ResponseEntity.ok(ApiResponse.ok("OK", page));
    }

}
