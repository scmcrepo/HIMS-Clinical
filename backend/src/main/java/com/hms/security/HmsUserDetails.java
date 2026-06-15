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
 
    public HmsUserDetails(UUID id, String username, String passwordHash,
                          boolean accountLocked, Set<String> featureKeys, Set<String> roleNames,
                          UUID consultantId, UUID departmentId) {
        this(id, username, passwordHash, accountLocked, featureKeys, roleNames, consultantId, departmentId, Set.of());
    }

    public HmsUserDetails(UUID id, String username, String passwordHash,
                          boolean accountLocked, Set<String> featureKeys, Set<String> roleNames,
                          UUID consultantId, UUID departmentId, Set<UUID> departmentIds) {
        this.id = id; this.username = username; this.passwordHash = passwordHash;
        this.accountLocked = accountLocked; this.featureKeys = featureKeys;
        this.roleNames = roleNames;
        this.consultantId = consultantId;
        this.departmentId = departmentId;
        this.departmentIds = departmentIds;
    }
 
    public UUID getId() { return id; }
    public Set<String> getFeatureKeys() { return featureKeys; }
    public Set<String> getRoleNames() { return roleNames; }
    public UUID getConsultantId() { return consultantId; }
    public UUID getDepartmentId() { return departmentId; }
    public Set<UUID> getDepartmentIds() { return departmentIds; }

    public boolean isSuperAdmin() {
        return roleNames.contains("SUPERADMIN");
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
