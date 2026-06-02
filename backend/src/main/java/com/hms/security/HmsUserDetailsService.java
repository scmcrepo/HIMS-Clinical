package com.hms.security;
import com.hms.infrastructure.persistence.shared.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @RequiredArgsConstructor
public class HmsUserDetailsService implements UserDetailsService {
    private final UserJpaRepository userRepo;
    @Override @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepo.findByUsernameWithRolesAndFeatures(username)
            .map(u -> new HmsUserDetails(u.getId(), u.getUsername(), u.getPasswordHash(),
                u.isAccountLocked(), u.collectAllFeatureKeys(), u.collectAllRoleNames()))
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }
}
