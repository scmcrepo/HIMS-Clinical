package com.hms.api.feature;
import com.hms.api.feature.response.FeatureResponse;
import com.hms.api.shared.ApiResponse;
import com.hms.application.role.RoleManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/feature") @RequiredArgsConstructor
public class FeatureController {
    private final RoleManagementService roleService;
    @GetMapping @PreAuthorize("hasPermission('SETTINGS_ROLE','')")
    public ResponseEntity<ApiResponse<List<FeatureResponse>>> getAllFeatures() {
        return ResponseEntity.ok(ApiResponse.ok("OK", roleService.getAllFeatures()));
    }
    @GetMapping("/getFeaturesByCurrentUser")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> getByCurrentUser(
            @RequestParam(name = "module", required=false) String module) {
        return ResponseEntity.ok(ApiResponse.ok("OK", roleService.getFeaturesForCurrentUser(module)));
    }
}
