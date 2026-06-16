package com.hms.security;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class HmsUserDetails implements UserDetails {
    private final UUID id;
    private final String username;
    private final String passwordHash;
    private final boolean accountLocked;
    private final Set<String> featureKeys;
    private final Set<String> roleNames;
    private final UUID consultantId;
    private final UUID departmentId;
    private final Set<UUID> departmentIds;
    private final UUID tenantId;   // null => platform-level SUPERADMIN
    private final UUID branchId;   // null => not pinned to one branch (SUPERADMIN or HOSPITAL_ADMIN)

    public HmsUserDetails(UUID id, String username, String passwordHash,
                          boolean accountLocked, Set<String> featureKeys, Set<String> roleNames,
                          UUID consultantId, UUID departmentId, UUID tenantId, UUID branchId,
                          Set<UUID> departmentIds) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.accountLocked = accountLocked;
        this.featureKeys = featureKeys;
        this.roleNames = roleNames;
        this.consultantId = consultantId;
        this.departmentId = departmentId;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.departmentIds = departmentIds;
    }

    public HmsUserDetails(UUID id, String username, String passwordHash,
                          boolean accountLocked, Set<String> featureKeys, Set<String> roleNames,
                          UUID consultantId, UUID departmentId, UUID tenantId, UUID branchId) {
        this(id, username, passwordHash, accountLocked, featureKeys, roleNames, consultantId, departmentId, tenantId, branchId, Set.of());
    }

    public HmsUserDetails(UUID id, String username, String passwordHash,
                          boolean accountLocked, Set<String> featureKeys, Set<String> roleNames,
                          UUID consultantId, UUID departmentId, Set<UUID> departmentIds) {
        this(id, username, passwordHash, accountLocked, featureKeys, roleNames, consultantId, departmentId, null, null, departmentIds);
    }

    public UUID getId() { return id; }
    public Set<String> getFeatureKeys() { return featureKeys; }
    public Set<String> getRoleNames() { return roleNames; }
    public UUID getConsultantId() { return consultantId; }
    public UUID getDepartmentId() { return departmentId; }
    public Set<UUID> getDepartmentIds() { return departmentIds; }
    public UUID getTenantId() { return tenantId; }
    public UUID getBranchId() { return branchId; }

    /**
     * A SUPERADMIN is a platform user: it must both carry the SUPERADMIN role AND have no tenant.
     * Requiring tenantId == null prevents a tenant-scoped account named "SUPERADMIN" from gaining
     * cross-tenant powers.
     */
    public boolean isSuperAdmin() {
        return tenantId == null && roleNames.contains("SUPERADMIN");
    }

    /**
     * A hospital (tenant) admin operates across all branches of a single tenant: it belongs to a
     * tenant but is not pinned to one branch. Branch-pinned staff have a non-null branchId.
     */
    public boolean isHospitalAdmin() {
        return tenantId != null && branchId == null;
    }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<SimpleGrantedAuthority> authorities = featureKeys.stream()
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toSet());
        roleNames.forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
        return authorities;
    }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonLocked() { return !accountLocked; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
