package com.hms.application.user;

import com.hms.api.user.request.ChangePasswordRequest;
import com.hms.api.user.request.CreateUserRequest;
import com.hms.api.user.request.UpdateUserRequest;
import com.hms.api.user.response.UserResponse;
import com.hms.domain.shared.model.EntityStatus;
import com.hms.domain.shared.model.Department;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.shared.RoleEntity;
import com.hms.infrastructure.persistence.shared.UserEntity;
import com.hms.infrastructure.persistence.shared.UserJpaRepository;
import com.hms.infrastructure.persistence.role.RoleJpaRepository;
import com.hms.infrastructure.persistence.department.DepartmentJpaRepository;
import com.hms.security.HmsUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserManagementService {

    private final UserJpaRepository userRepo;
    private final RoleJpaRepository roleRepo;
    private final DepartmentJpaRepository departmentRepo;
    private final PasswordEncoder passwordEncoder;
    private final com.hms.infrastructure.persistence.consultant.ConsultantJpaRepository consultantRepo;

    @Transactional
    public UserResponse createUser(CreateUserRequest req) {
        String cleanUsername = req.username().toLowerCase().trim();
        if (userRepo.existsByUsername(cleanUsername)) {
            throw new BusinessRuleViolationException(
                "Username '" + req.username() + "' already exists");
        }

        UserEntity user = new UserEntity();
        // Username always lowercased — mirrors legacy behaviour
        user.setUsername(cleanUsername);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setFirstName(req.firstName());
        user.setLastName(req.lastName());
        user.setEmail(req.email());
        user.setStatus((short) 1); // ACTIVE
        user.setAccountLocked(false);
        user.setShowCasesheet(req.showCasesheet());
        user.setSpeechLanguage(req.speechLanguage() != null ? req.speechLanguage() : "en-IN");
        user.setTextAutoSuggest(true);
        user.setCreatedAt(Instant.now());
        user.setModifiedAt(Instant.now());
        user.setSalutation(req.salutation());
        user.setPhoneNo(req.phoneNo());

        // Assign roles
        Set<RoleEntity> roles = resolveRoles(req.roleIds());
        user.setRoles(roles);

        // Assign departments
        if (req.departmentIds() != null && !req.departmentIds().isEmpty()) {
            user.setDepartments(new HashSet<>(departmentRepo.findAllById(req.departmentIds())));
        }

        UserEntity saved = userRepo.save(user);

        return toResponse(saved);
    }

    @Transactional
    public UserResponse updateUser(UUID userId, UpdateUserRequest req) {
        UserEntity user = findOrThrow(userId);

        if (req.firstName()     != null) user.setFirstName(req.firstName());
        if (req.lastName()      != null) user.setLastName(req.lastName());
        if (req.email()         != null) user.setEmail(req.email());
        if (req.roleIds()       != null) {
            user.getRoles().clear();
            user.getRoles().addAll(resolveRoles(req.roleIds()));
        }
        if (req.speechLanguage()!= null) user.setSpeechLanguage(req.speechLanguage());
        if (req.salutation()    != null) user.setSalutation(req.salutation());
        if (req.phoneNo()       != null) user.setPhoneNo(req.phoneNo());
        user.setShowCasesheet(req.showCasesheet());
        user.setTextAutoSuggest(req.textAutoSuggest());
        user.setModifiedAt(Instant.now());

        if (req.departmentIds() != null) {
            user.getDepartments().clear();
            user.getDepartments().addAll(departmentRepo.findAllById(req.departmentIds()));
        }

        // Status change also controls account lock — mirrors legacy behaviour
        if (req.status() != null) {
            user.setStatus(req.status() == EntityStatus.ACTIVE ? (short) 1 : (short) 0);
            user.setAccountLocked(req.status() != EntityStatus.ACTIVE);
        }

        UserEntity saved = userRepo.save(user);

        return toResponse(saved);
    }

    @Transactional
    public void changeOwnPassword(ChangePasswordRequest req) {
        HmsUserDetails principal = currentUser();
        UserEntity user = findOrThrow(principal.getId());

        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new BusinessRuleViolationException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setModifiedAt(Instant.now());
        userRepo.save(user);
    }

    @Transactional
    public void adminResetPassword(UUID userId, String newPassword) {
        UserEntity user = findOrThrow(userId);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setModifiedAt(Instant.now());
        userRepo.save(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAll() {
        HmsUserDetails principal = currentUser();
        boolean isSuperAdmin = principal.isSuperAdmin() || 
                               principal.getRoleNames().contains("ROLE_SUPER_ADMIN") || 
                               principal.getRoleNames().contains("SUPERADMIN");
        return userRepo.findAll().stream()
            // Non-super-admin users cannot see super-admin accounts
            .filter(u -> isSuperAdmin || u.getRoles().stream()
                .noneMatch(r -> r.getName().equalsIgnoreCase("ROLE_SUPER_ADMIN") || r.getName().equalsIgnoreCase("SUPERADMIN")))
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getById(UUID userId) {
        return toResponse(findOrThrow(userId));
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {
        return toResponse(findOrThrow(currentUser().getId()));
    }

    @Transactional(readOnly = true)
    public boolean checkCurrentPassword(String password) {
        UserEntity user = findOrThrow(currentUser().getId());
        boolean matches = passwordEncoder.matches(password, user.getPasswordHash());
        System.out.println("[PASSWORD CHECK] User: " + user.getUsername() + ", entered: " + password + ", matches: " + matches);
        return matches;
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private Set<RoleEntity> resolveRoles(Set<UUID> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) return new HashSet<>();
        return new HashSet<>(roleRepo.findAllById(roleIds));
    }

    private UserEntity findOrThrow(UUID id) {
        return userRepo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    private HmsUserDetails currentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof HmsUserDetails details)) {
            throw new BusinessRuleViolationException("No authenticated user found");
        }
        return details;
    }

    private UserResponse toResponse(UserEntity u) {
        Set<UserResponse.RoleSummary> roleSummaries = u.getRoles().stream()
            .map(r -> new UserResponse.RoleSummary(r.getId(), r.getName()))
            .collect(Collectors.toSet());

        Set<UUID> departmentIds = u.getDepartments().stream()
            .map(Department::getId)
            .collect(Collectors.toSet());

        UUID consultantId = consultantRepo.findByUserId(u.getId())
            .map(com.hms.domain.consultant.model.Consultant::getId)
            .orElse(null);

        return new UserResponse(
            u.getId(), u.getUsername(), u.getFirstName(), u.getLastName(),
            u.getFirstName() + " " + u.getLastName(),
            u.getEmail(),
            u.getStatus() == 1 ? EntityStatus.ACTIVE : EntityStatus.INACTIVE,
            u.isAccountLocked(), roleSummaries,
            departmentIds, new HashSet<>(), consultantId,
            u.isShowCasesheet(), u.getSpeechLanguage(), u.isTextAutoSuggest(),
            u.getSalutation(), u.getPhoneNo()
        );
    }
}
