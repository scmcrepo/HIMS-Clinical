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
import com.hms.infrastructure.persistence.tenant.BranchEntity;
import com.hms.infrastructure.persistence.tenant.BranchJpaRepository;
import com.hms.infrastructure.tenant.TenantContext;
import com.hms.infrastructure.tenant.BranchContext;
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
    private final com.hms.security.FeaturePermissionCacheService permissionCache;
    private final BranchJpaRepository branchRepo;

    @Transactional
    public UserResponse createUser(CreateUserRequest req) {
        String cleanUsername = req.username().toLowerCase().trim();
        if (userRepo.existsByUsername(cleanUsername)) {
            throw new BusinessRuleViolationException(
                "Username '" + req.username() + "' already exists");
        }
        UUID tenantId = TenantContext.get();
        if (req.phoneNo() != null && !req.phoneNo().isBlank() && tenantId != null
                && userRepo.existsByPhoneNoAndTenantId(req.phoneNo().trim(), tenantId)) {
            throw new BusinessRuleViolationException(
                "Contact number '" + req.phoneNo() + "' already exists");
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

        // Audit finding 17.1: stamp tenant + branch from the creator's context so the user inherits
        // the hierarchy and is never saved tenant/branch-less. UserEntity is not an AuditableEntity,
        // so this is NOT done automatically by the Hibernate listener — it must be explicit here.
        stampScope(user, roles, req.branchId());

        UserEntity saved = userRepo.save(user);

        // Feedback 2.2: the server permission cache must rebuild immediately when accounts/roles
        // change, so a newly created user can act on their roles without waiting for TTL expiry.
        rebuildPermissionCache(saved);
        return toResponse(saved);
    }

    @Transactional
    public UserResponse updateUser(UUID userId, UpdateUserRequest req) {
        UserEntity user = findInScopeOrThrow(userId);

        if (req.firstName()     != null) user.setFirstName(req.firstName());
        if (req.lastName()      != null) user.setLastName(req.lastName());
        if (req.email()         != null) user.setEmail(req.email());
        if (req.roleIds()       != null) {
            user.getRoles().clear();
            user.getRoles().addAll(resolveRoles(req.roleIds()));
        }
        if (req.speechLanguage()!= null) user.setSpeechLanguage(req.speechLanguage());
        if (req.salutation()    != null) user.setSalutation(req.salutation());
        if (req.phoneNo()       != null) {
            if (!req.phoneNo().isBlank()) {
                boolean exists = user.getTenantId() != null
                    ? userRepo.existsByPhoneNoAndTenantIdAndIdNot(req.phoneNo().trim(), user.getTenantId(), userId)
                    : false;
                if (exists) {
                    throw new BusinessRuleViolationException(
                        "Contact number '" + req.phoneNo() + "' already exists");
                }
            }
            user.setPhoneNo(req.phoneNo());
        }
        user.setShowCasesheet(req.showCasesheet());
        user.setTextAutoSuggest(req.textAutoSuggest());
        user.setModifiedAt(Instant.now());

        if (req.departmentIds() != null) {
            user.getDepartments().clear();
            user.getDepartments().addAll(departmentRepo.findAllById(req.departmentIds()));
        }

        // Re-evaluate branch placement if roles or branch changed (keeps tenant-wide admins
        // branchless and branch users non-null). Tenant is preserved (never cross-tenant).
        if (req.roleIds() != null || req.branchId() != null) {
            stampScope(user, user.getRoles(), req.branchId());
        }

        // Status change also controls account lock — mirrors legacy behaviour
        if (req.status() != null) {
            user.setStatus(req.status() == EntityStatus.ACTIVE ? (short) 1 : (short) 0);
            user.setAccountLocked(req.status() != EntityStatus.ACTIVE);
        }

        UserEntity saved = userRepo.save(user);

        // Feedback 2.2: rebuild the permission cache so role/account changes take effect at once.
        rebuildPermissionCache(saved);
        return toResponse(saved);
    }

    /**
     * Rebuild the server-side RBAC cache after a user mutation. Scoped to the user's tenant when
     * known (cheaper); falls back to a full rebuild for platform users with no tenant.
     */
    private void rebuildPermissionCache(UserEntity user) {
        UUID tenantId = user.getTenantId();
        if (tenantId != null) {
            permissionCache.rebuildCacheForTenant(tenantId);
        } else {
            permissionCache.rebuildAll();
        }
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
        UserEntity user = findInScopeOrThrow(userId);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setModifiedAt(Instant.now());
        userRepo.save(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAll() {
        HmsUserDetails principal = currentUser();
        boolean isSuperAdmin = principal.isSuperAdmin();

        // Audit finding 17.2: users must be tenant/branch filtered. UserEntity bypasses the
        // Hibernate @Filters, so scope explicitly from the request context:
        //   SUPERADMIN (no impersonation) -> all users
        //   SUPERADMIN impersonating / HOSPITAL_ADMIN -> their tenant (all branches)
        //   BRANCH_ADMIN / branch staff   -> their tenant + branch only
        UUID tenantId = TenantContext.get();
        UUID branchId = BranchContext.get();

        List<UserEntity> scoped;
        if (isSuperAdmin && tenantId == null) {
            scoped = userRepo.findAll();
        } else if (tenantId == null) {
            // Fail closed: a non-superadmin with no tenant context sees nothing.
            scoped = List.of();
        } else if (branchId != null) {
            scoped = userRepo.findAllByTenantIdAndBranchId(tenantId, branchId);
        } else {
            scoped = userRepo.findAllByTenantId(tenantId);
        }

        return scoped.stream()
            // Non-super-admins never see platform SUPERADMIN accounts.
            .filter(u -> isSuperAdmin || u.getRoles().stream()
                .noneMatch(r -> r.getName().equalsIgnoreCase("SUPERADMIN")))
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getById(UUID userId) {
        return toResponse(findInScopeOrThrow(userId));
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {
        return toResponse(findOrThrow(currentUser().getId()));
    }

    @Transactional(readOnly = true)
    public boolean checkCurrentPassword(String password) {
        UserEntity user = findOrThrow(currentUser().getId());
        // (Removed a debug log here that printed the entered plaintext password — a security gap.)
        return passwordEncoder.matches(password, user.getPasswordHash());
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private Set<RoleEntity> resolveRoles(Set<UUID> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) return new HashSet<>();
        return new HashSet<>(roleRepo.findAllById(roleIds));
    }

    /**
     * Stamp tenant + branch on a user from the creator's request context (audit 17.1 / 17.6).
     * Rules:
     *  - tenant is always the creator's tenant — users can never be created across tenants;
     *  - a tenant-wide admin (HOSPITAL_ADMIN) is kept branchless (sees all branches);
     *  - a BRANCH_ADMIN/branch-staff creator forces the new user into the creator's own branch;
     *  - a HOSPITAL_ADMIN creator may pick a branch (validated to the tenant), else the tenant's
     *    default branch is used — so a branch-scoped user is never saved with a null branch.
     */
    private void stampScope(UserEntity user, Set<RoleEntity> roles, UUID requestedBranchId) {
        UUID creatorTenant = TenantContext.get();
        UUID creatorBranch = BranchContext.get();

        if (creatorTenant == null) {
            throw new BusinessRuleViolationException(
                "Cannot create or move a user without a hospital context. A platform admin must "
                + "act within a hospital (X-Tenant-Id) to manage users.");
        }
        user.setTenantId(creatorTenant);

        boolean tenantWideAdmin = roles.stream()
            .anyMatch(r -> "HOSPITAL_ADMIN".equalsIgnoreCase(r.getName()));
        if (tenantWideAdmin) {
            user.setBranchId(null);
            return;
        }

        // If the creator is pinned to a specific branch, force the target user into that branch.
        HmsUserDetails principal = currentUser();
        if (principal.getBranchId() != null) {
            user.setBranchId(principal.getBranchId());
            return;
        }

        UUID branch = null;
        if (requestedBranchId != null
                && branchRepo.findByIdAndTenantId(requestedBranchId, creatorTenant).isPresent()) {
            branch = requestedBranchId;
        } else if (user.getBranchId() != null
                && branchRepo.findByIdAndTenantId(user.getBranchId(), creatorTenant).isPresent()) {
            branch = user.getBranchId();
        } else if (BranchContext.get() != null
                && branchRepo.findByIdAndTenantId(BranchContext.get(), creatorTenant).isPresent()) {
            branch = BranchContext.get();
        } else {
            branch = branchRepo.findByTenantIdAndIsDefaultTrue(creatorTenant)
                .map(BranchEntity::getId).orElse(null);
        }
        if (branch == null) {
            throw new BusinessRuleViolationException(
                "No branch available to assign this user. Create a branch first.");
        }
        user.setBranchId(branch);
    }

    /**
     * Load a user by id, but only if it is within the caller's tenant/branch scope. Closes the
     * findById cross-tenant gap for UserEntity (which bypasses the Hibernate @Filters): without
     * this, a hospital admin could read/modify another hospital's user by guessing its id.
     */
    private UserEntity findInScopeOrThrow(UUID id) {
        UserEntity user = findOrThrow(id);
        HmsUserDetails principal = currentUser();
        if (principal.isSuperAdmin()) return user; // platform admin sees all

        UUID tenantId = TenantContext.get();
        UUID branchId = BranchContext.get();
        if (tenantId == null || (user.getTenantId() != null && !tenantId.equals(user.getTenantId()))) {
            throw new ResourceNotFoundException("User", id); // 404, don't reveal other tenants
        }
        if (branchId != null && user.getBranchId() != null && !branchId.equals(user.getBranchId())) {
            throw new ResourceNotFoundException("User", id); // branch admin can't reach other branches
        }
        return user;
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
            u.getSalutation(), u.getPhoneNo(), u.getBranchId()
        );
    }
}
