package com.hms.api.tenant;

import com.hms.api.shared.ApiResponse;
import com.hms.application.tenant.TenantService;
import com.hms.infrastructure.persistence.tenant.TenantEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    // ── Public: tenant picker on the login screen (active tenants only) ─────────
    @GetMapping("/public")
    public ResponseEntity<ApiResponse<List<PublicTenant>>> publicList() {
        List<PublicTenant> tenants = tenantService.listActivePublic().stream()
            .map(t -> new PublicTenant(t.getSlug(), t.getName()))
            .toList();
        return ResponseEntity.ok(ApiResponse.ok("OK", tenants));
    }

    // ── Platform admin (SUPERADMIN only) ────────────────────────────────────────
    @PostMapping
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<ApiResponse<TenantView>> create(@RequestBody CreateTenantRequest req) {
        // Automatically generate a slug from the hospital name.
        String generatedSlug = slugify(req.name());
        
        // Single onboarding flow: hospital + default branch + RBAC seed + Hospital Admin login.
        TenantEntity t = tenantService.onboard(generatedSlug, req.name(), req.description(),
            req.address(), req.contactNumber(),
            req.adminUsername(), req.adminPassword(), req.adminFirstName(), req.adminLastName());
        return ResponseEntity.ok(ApiResponse.ok("Hospital onboarded", TenantView.from(t)));
    }

    private String slugify(String name) {
        if (name == null || name.isBlank()) {
            return "tenant-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        }
        String clean = name.trim().toLowerCase()
            .replaceAll("[^a-z0-9\\s-]", "")
            .replaceAll("[\\s-]+", "-");
        if (clean.length() > 50) {
            clean = clean.substring(0, 50);
        }
        if (clean.endsWith("-")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return clean + "-" + java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<ApiResponse<List<TenantView>>> list() {
        return ResponseEntity.ok(ApiResponse.ok("OK",
            tenantService.listAll().stream().map(TenantView::from).toList()));
    }

    @GetMapping("/{tenantId}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<ApiResponse<TenantView>> get(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", TenantView.from(tenantService.get(tenantId))));
    }

    @PutMapping("/{tenantId}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<ApiResponse<TenantView>> update(@PathVariable UUID tenantId,
                                                          @RequestBody UpdateTenantRequest req) {
        TenantEntity t = tenantService.update(tenantId, req.name(), req.description(), req.address(), req.contactNumber(), req.status());
        return ResponseEntity.ok(ApiResponse.ok("Tenant updated", TenantView.from(t)));
    }

    @PostMapping("/{tenantId}/seed")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<ApiResponse<Void>> seed(@PathVariable UUID tenantId) {
        tenantService.seedRbac(tenantId);
        return ResponseEntity.ok(ApiResponse.ok("RBAC seeded for tenant"));
    }

    @PutMapping("/{tenantId}/reset-admin-password")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<ApiResponse<Void>> resetAdminPassword(
            @PathVariable UUID tenantId,
            @RequestParam(name = "password") String password) {
        tenantService.resetAdminPassword(tenantId, password);
        return ResponseEntity.ok(ApiResponse.ok("Hospital admin password reset successfully"));
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────────
    // Admin fields are optional but recommended: providing them onboards the hospital with its
    // first Hospital Admin login in one call (audit 17.5).
    public record CreateTenantRequest(String name, String description, String address, String contactNumber,
        String adminUsername, String adminPassword, String adminFirstName, String adminLastName) {}
    public record UpdateTenantRequest(String name, String description, String address, String contactNumber, Short status) {}
    public record PublicTenant(String slug, String name) {}
    public record TenantView(UUID id, String slug, String name, String description, String address, String contactNumber, short status) {
        static TenantView from(TenantEntity t) {
            return new TenantView(t.getId(), t.getSlug(), t.getName(), t.getDescription(), t.getAddress(), t.getContactNumber(), t.getStatus());
        }
    }
}
