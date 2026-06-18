package com.hms.security;
import com.hms.infrastructure.persistence.shared.UserEntity;
import com.hms.infrastructure.persistence.shared.UserJpaRepository;
import com.hms.infrastructure.persistence.consultant.ConsultantJpaRepository;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class HmsUserDetailsService implements UserDetailsService {
    private final UserJpaRepository userRepo;
    private final ConsultantJpaRepository consultantRepo;

    public HmsUserDetailsService(
            @org.springframework.context.annotation.Lazy UserJpaRepository userRepo,
            @org.springframework.context.annotation.Lazy ConsultantJpaRepository consultantRepo) {
        this.userRepo = userRepo;
        this.consultantRepo = consultantRepo;
    }

    /**
     * Resolve a login by username alone. Usernames are globally unique, so there is no ambiguity
     * and the user does not pick a hospital/branch at login. The user's tenant and branch are read
     * off the resolved row and carried in the principal; the request filter uses them to scope data.
     */
    @Override @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepo.findByUsernameWithRolesAndFeatures(username)
            .map(u -> {
                if (u.getStatus() != 1) {
                    throw new org.springframework.security.authentication.DisabledException("User account is inactive");
                }
                if (u.getTenantId() != null && u.getTenant() != null && !u.getTenant().isActive()) {
                    throw new org.springframework.security.authentication.DisabledException("Hospital/Tenant is inactive");
                }
                if (u.getBranchId() != null && u.getBranch() != null && !u.getBranch().isActive()) {
                    throw new org.springframework.security.authentication.DisabledException("Branch is inactive");
                }
                UUID consultantId = null;
                UUID departmentId = null;
                var consultantOpt = consultantRepo.findByUserId(u.getId());
                if (consultantOpt.isPresent()) {
                    var consultant = consultantOpt.get();
                    consultantId = consultant.getId();
                    departmentId = consultant.getDepartmentId();
                }
                
                java.util.Set<UUID> departmentIds = u.getDepartments() != null
                    ? u.getDepartments().stream()
                        .map(com.hms.domain.shared.model.Department::getId)
                        .collect(java.util.stream.Collectors.toSet())
                    : java.util.Set.of();

                if (departmentId == null && !departmentIds.isEmpty()) {
                    departmentId = departmentIds.iterator().next();
                }

                return new HmsUserDetails(u.getId(), u.getUsername(), u.getPasswordHash(),
                    u.isAccountLocked(), u.collectAllFeatureKeys(), u.collectAllRoleNames(),
                    consultantId, departmentId, u.getTenantId(), u.getBranchId(), departmentIds);
            })
            .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }
}
