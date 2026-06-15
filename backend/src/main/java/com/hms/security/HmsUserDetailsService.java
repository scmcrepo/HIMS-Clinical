package com.hms.security;
import com.hms.infrastructure.persistence.shared.UserJpaRepository;
import com.hms.infrastructure.persistence.consultant.ConsultantJpaRepository;
import lombok.RequiredArgsConstructor;
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
    @Override @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepo.findByUsernameWithRolesAndFeatures(username)
            .map(u -> {
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
                    consultantId, departmentId, departmentIds);
            })
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }
}
