package com.hms.api.tenant;

import com.hms.api.shared.ApiResponse;
import com.hms.application.tenant.BranchService;
import com.hms.infrastructure.persistence.tenant.BranchEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Branch management within the caller's tenant. A HOSPITAL_ADMIN manages the branches of their own
 * hospital (scoped automatically via TenantContext); a SUPERADMIN may manage any tenant's branches
 * by impersonating it with the {@code X-Tenant-Id} header.
 */
@RestController
@RequestMapping("/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService branchService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<BranchView>>> list() {
        return ResponseEntity.ok(ApiResponse.ok("OK",
            branchService.listForCurrentTenant().stream().map(BranchView::from).toList()));
    }

    @GetMapping("/{branchId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<BranchView>> get(@PathVariable UUID branchId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", BranchView.from(branchService.get(branchId))));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN','SUPERADMIN')")
    public ResponseEntity<ApiResponse<BranchView>> create(@RequestBody CreateBranchRequest req) {
        BranchEntity b = branchService.create(req.code(), req.name(), req.address(), req.contactNumber(),
            req.adminUsername(), req.adminPassword());
        return ResponseEntity.ok(ApiResponse.ok("Branch created", BranchView.from(b)));
    }

    @PutMapping("/{branchId}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN','SUPERADMIN')")
    public ResponseEntity<ApiResponse<BranchView>> update(@PathVariable UUID branchId,
                                                          @RequestBody UpdateBranchRequest req) {
        BranchEntity b = branchService.update(branchId, req.name(), req.address(), req.contactNumber(), req.status());
        return ResponseEntity.ok(ApiResponse.ok("Branch updated", BranchView.from(b)));
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────────
    // Admin fields are optional: providing them provisions a BRANCH_ADMIN login alongside the branch.
    public record CreateBranchRequest(String code, String name, String address, String contactNumber,
                                      String adminUsername, String adminPassword) {}
    public record UpdateBranchRequest(String name, String address, String contactNumber, Short status) {}
    public record BranchView(UUID id, String code, String name, String address, String contactNumber, boolean isDefault, short status) {
        static BranchView from(BranchEntity b) {
            return new BranchView(b.getId(), b.getCode(), b.getName(), b.getAddress(), b.getContactNumber(), b.isDefault(), b.getStatus());
        }
    }
}
