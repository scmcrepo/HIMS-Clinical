package com.hms.security;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class HmsUserDetails implements UserDetails {
    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final String username;
    private final String passwordHash;
    private final boolean accountLocked;
    private final Set<String> featureKeys;
    private final Set<String> roleNames;
    private final Set<UUID> roleIds;
    private final java.util.Map<UUID, Set<UUID>> branchRoleIds;
    private final UUID consultantId;
    private final UUID departmentId;
    private final Set<UUID> departmentIds;
    private final UUID tenantId;   // null => platform-level SUPERADMIN
    private final UUID branchId;   // null => not pinned to one branch (SUPERADMIN or HOSPITAL_ADMIN)
    private final Set<UUID> authorizedBranchIds;

    public HmsUserDetails(UUID id, String username, String passwordHash,
                          boolean accountLocked, Set<String> featureKeys, Set<String> roleNames, Set<UUID> roleIds,
                          java.util.Map<UUID, Set<UUID>> branchRoleIds,
                          UUID consultantId, UUID departmentId, UUID tenantId, UUID branchId,
                          Set<UUID> departmentIds, Set<UUID> authorizedBranchIds) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.accountLocked = accountLocked;
        this.featureKeys = featureKeys;
        this.roleNames = roleNames;
        this.roleIds = roleIds != null ? roleIds : Set.of();
        this.branchRoleIds = branchRoleIds != null ? branchRoleIds : java.util.Map.of();
        this.consultantId = consultantId;
        this.departmentId = departmentId;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.departmentIds = departmentIds;
        this.authorizedBranchIds = authorizedBranchIds != null ? authorizedBranchIds : Set.of();
    }

    public HmsUserDetails(UUID id, String username, String passwordHash,
                          boolean accountLocked, Set<String> featureKeys, Set<String> roleNames, Set<UUID> roleIds,
                          UUID consultantId, UUID departmentId, UUID tenantId, UUID branchId,
                          Set<UUID> departmentIds, Set<UUID> authorizedBranchIds) {
        this(id, username, passwordHash, accountLocked, featureKeys, roleNames, roleIds, java.util.Map.of(), consultantId, departmentId, tenantId, branchId, departmentIds, authorizedBranchIds);
    }

    public HmsUserDetails(UUID id, String username, String passwordHash,
                          boolean accountLocked, Set<String> featureKeys, Set<String> roleNames, Set<UUID> roleIds,
                          UUID consultantId, UUID departmentId, UUID tenantId, UUID branchId,
                          Set<UUID> departmentIds) {
        this(id, username, passwordHash, accountLocked, featureKeys, roleNames, roleIds, java.util.Map.of(), consultantId, departmentId, tenantId, branchId, departmentIds, Set.of());
    }

    public HmsUserDetails(UUID id, String username, String passwordHash,
                          boolean accountLocked, Set<String> featureKeys, Set<String> roleNames, Set<UUID> roleIds,
                          UUID consultantId, UUID departmentId, UUID tenantId, UUID branchId) {
        this(id, username, passwordHash, accountLocked, featureKeys, roleNames, roleIds, java.util.Map.of(), consultantId, departmentId, tenantId, branchId, Set.of(), Set.of());
    }

    public HmsUserDetails(UUID id, String username, String passwordHash,
                          boolean accountLocked, Set<String> featureKeys, Set<String> roleNames, Set<UUID> roleIds,
                          UUID consultantId, UUID departmentId, Set<UUID> departmentIds) {
        this(id, username, passwordHash, accountLocked, featureKeys, roleNames, roleIds, java.util.Map.of(), consultantId, departmentId, null, null, departmentIds, Set.of());
    }

    public UUID getId() { return id; }
    public Set<String> getFeatureKeys() { return featureKeys; }
    public Set<String> getRoleNames() { return roleNames; }
    public Set<UUID> getRoleIds() { return roleIds; }
    public java.util.Map<UUID, Set<UUID>> getBranchRoleIds() { return branchRoleIds; }
    
    public Set<UUID> getActiveRoleIds(UUID currentBranchId) {
        Set<UUID> active = new java.util.HashSet<>();
        if (branchRoleIds.containsKey(null)) active.addAll(branchRoleIds.get(null));
        if (currentBranchId != null && branchRoleIds.containsKey(currentBranchId)) {
            active.addAll(branchRoleIds.get(currentBranchId));
        }
        // If they have no mapped branch roles (legacy mode/tests), fallback to all roleIds
        if (active.isEmpty() && branchRoleIds.isEmpty()) {
            return roleIds;
        }
        return active;
    }

    public UUID getConsultantId() { return consultantId; }
    public UUID getDepartmentId() { return departmentId; }
    public Set<UUID> getDepartmentIds() { return departmentIds; }
    public UUID getTenantId() { return tenantId; }
    public UUID getBranchId() { return branchId; }
    public Set<UUID> getAuthorizedBranchIds() { return authorizedBranchIds; }
    public boolean isAccountLocked() { return accountLocked; }

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
        return tenantId != null && (branchId == null || roleNames.contains("HOSPITAL_ADMIN"));
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HmsUserDetails that = (HmsUserDetails) o;
        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        return username != null ? username.equals(that.username) : that.username == null;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (username != null ? username.hashCode() : 0);
        return result;
    }
}
