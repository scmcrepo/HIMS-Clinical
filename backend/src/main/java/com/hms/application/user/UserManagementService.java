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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.hms.domain.consultant.model.Consultant;
import com.hms.domain.consultant.model.ConsultantType;
import com.hms.infrastructure.persistence.consultant.ConsultantJpaRepository;

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
    private final com.hms.security.encryption.PiiSearchTokenService tokenService;

    @Transactional
    public UserResponse createUser(CreateUserRequest req) {
        String cleanUsername = req.username().toLowerCase().trim();
        UUID tenantId = TenantContext.get();
        UUID originalBranchId = com.hms.infrastructure.tenant.BranchContext.get();

        try {
            // Temporarily clear branch context so that TenantFilterAspect disables the branchFilter
            com.hms.infrastructure.tenant.BranchContext.clear();

            Optional<UserEntity> existingUserOpt = userRepo.findByUsername(cleanUsername);
            if (existingUserOpt.isPresent()) {
                UserEntity existingUser = existingUserOpt.get();
                if (tenantId != null && existingUser.getTenantId().equals(tenantId)) {
                    UUID targetBranchId = req.branchId();
                    if (targetBranchId == null) {
                        HmsUserDetails principal = currentUser();
                        if (principal != null && principal.getBranchId() != null) {
                            targetBranchId = principal.getBranchId();
                        } else if (req.branchIds() != null && !req.branchIds().isEmpty()) {
                            targetBranchId = req.branchIds().iterator().next();
                        } else if (originalBranchId != null) {
                            targetBranchId = originalBranchId;
                        } else {
                            targetBranchId = branchRepo.findByTenantIdAndIsDefaultTrue(tenantId)
                                .map(BranchEntity::getId).orElse(null);
                        }
                    }
                    if (targetBranchId != null) {
                        UUID finalBranchId = targetBranchId;
                        boolean alreadyInBranch = existingUser.getBranchId() != null && existingUser.getBranchId().equals(finalBranchId)
                            || existingUser.getBranches().stream().anyMatch(b -> b.getId().equals(finalBranchId));
                        if (!alreadyInBranch) {
                            branchRepo.findById(finalBranchId).ifPresent(b -> {
                                existingUser.getBranches().add(b);
                            });
                            if (req.roleIds() != null && !req.roleIds().isEmpty()) {
                                existingUser.getRoles().addAll(resolveRoles(req.roleIds()));
                            }
                            if (req.departmentIds() != null && !req.departmentIds().isEmpty()) {
                                existingUser.getDepartments().addAll(departmentRepo.findAllById(req.departmentIds()));
                            }
                            UserEntity saved = userRepo.save(existingUser);
                            rebuildPermissionCache(saved);
                            if (originalBranchId != null) {
                                com.hms.infrastructure.tenant.BranchContext.set(originalBranchId);
                            }
                            return toResponse(saved);
                        }
                    }
                }
                throw new BusinessRuleViolationException(
                    "Username '" + req.username() + "' already exists");
            }

            String phoneToken = (req.phoneNo() != null && !req.phoneNo().isBlank()) ? tokenService.phoneToken(req.phoneNo().trim()) : null;
            if (phoneToken != null && tenantId != null) {
                Set<UUID> targetBranchIds = new HashSet<>();
                if (req.branchId() != null) {
                    targetBranchIds.add(req.branchId());
                }
                if (req.branchIds() != null) {
                    targetBranchIds.addAll(req.branchIds());
                }
                if (targetBranchIds.isEmpty()) {
                    HmsUserDetails principal = currentUser();
                    if (principal != null && principal.getBranchId() != null) {
                        targetBranchIds.add(principal.getBranchId());
                    } else if (originalBranchId != null) {
                        targetBranchIds.add(originalBranchId);
                    } else {
                        branchRepo.findByTenantIdAndIsDefaultTrue(tenantId)
                            .map(BranchEntity::getId).ifPresent(targetBranchIds::add);
                    }
                }
                for (UUID bId : targetBranchIds) {
                    if (userRepo.existsByPhoneNoTokenAndTenantIdAndBranchId(phoneToken, tenantId, bId)) {
                        throw new BusinessRuleViolationException(
                            "Contact number '" + req.phoneNo() + "' already exists in branch " +
                            branchRepo.findById(bId).map(BranchEntity::getName).orElse(bId.toString())
                        );
                    }
                    if (consultantRepo.existsByContactNumberTokenAndBranchIdAndStatusNot(phoneToken, bId, EntityStatus.DELETED)) {
                        throw new BusinessRuleViolationException(
                            "A consultant with contact number '" + req.phoneNo() + "' already exists in branch " +
                            branchRepo.findById(bId).map(BranchEntity::getName).orElse(bId.toString())
                        );
                    }
                }
            }

            String emailToken = (req.email() != null && !req.email().isBlank()) ? tokenService.token(req.email().trim()) : null;
            if (emailToken != null && userRepo.existsByEmailToken(emailToken)) {
                throw new BusinessRuleViolationException("Email '" + req.email() + "' is already registered to another user");
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
            user.setPhoneNoToken(phoneToken);
            user.setEmailToken(emailToken);

            // Assign branches
            if (req.branchIds() != null && !req.branchIds().isEmpty()) {
                user.setBranches(new HashSet<>(branchRepo.findAllById(req.branchIds())));
            }

            // Assign roles
            Set<RoleEntity> primaryRoles = resolveRoles(req.roleIds());
            Set<RoleEntity> allRoles = new HashSet<>(primaryRoles);
            
            // Auto-mirror roles to all other authorized branches for the user
            for (RoleEntity role : primaryRoles) {
                if (role.getBranchId() != null) {
                    for (BranchEntity branch : user.getBranches()) {
                        if (!branch.getId().equals(role.getBranchId())) {
                            roleRepo.findByNameAndTenantIdAndBranchId(role.getName(), com.hms.infrastructure.tenant.TenantContext.get(), branch.getId())
                                .ifPresent(allRoles::add);
                        }
                    }
                    if (user.getBranchId() != null && !user.getBranchId().equals(role.getBranchId())) {
                        roleRepo.findByNameAndTenantIdAndBranchId(role.getName(), com.hms.infrastructure.tenant.TenantContext.get(), user.getBranchId())
                            .ifPresent(allRoles::add);
                    }
                }
            }
            
            java.util.Collection<RoleEntity> distinctRoles = allRoles.stream()
                .collect(java.util.stream.Collectors.toMap(RoleEntity::getId, r -> r, (r1, r2) -> r1))
                .values();
            user.setRoles(new HashSet<>(distinctRoles));

            // Assign departments
            if (req.departmentIds() != null && !req.departmentIds().isEmpty()) {
                user.setDepartments(new HashSet<>(departmentRepo.findAllById(req.departmentIds())));
            }

            // branches moved up
            // Audit finding 17.1: stamp tenant + branch from the creator's context so the user inherits
            // the hierarchy and is never saved tenant/branch-less. UserEntity is not an AuditableEntity,
            // so this is NOT done automatically by the Hibernate listener — it must be explicit here.
            stampScope(user, allRoles, req.branchId(), originalBranchId);

            UserEntity saved = userRepo.save(user);
            syncConsultantForUser(saved, originalBranchId);

            // Feedback 2.2: the server permission cache must rebuild immediately when accounts/roles
            // change, so a newly created user can act on their roles without waiting for TTL expiry.
            rebuildPermissionCache(saved);
            
            if (originalBranchId != null) {
                com.hms.infrastructure.tenant.BranchContext.set(originalBranchId);
            }
            return toResponse(saved);
        } finally {
            if (originalBranchId != null) {
                com.hms.infrastructure.tenant.BranchContext.set(originalBranchId);
            }
        }
    }

    @Transactional
    public UserResponse updateUser(UUID userId, UpdateUserRequest req) {
        UUID originalBranchId = com.hms.infrastructure.tenant.BranchContext.get();
        try {
            // Temporarily clear branch context so that TenantFilterAspect disables the branchFilter
            com.hms.infrastructure.tenant.BranchContext.clear();

            UserEntity user = findOrThrow(userId);

            // Manual security check:
            HmsUserDetails principal = currentUser();
            boolean isHospitalAdmin = principal.isSuperAdmin() || principal.isHospitalAdmin();
            UUID editorBranchId = principal.getBranchId();
            UUID existingBranchId = user.getBranchId();

            if (!principal.isSuperAdmin()) {
                UUID tenantId = TenantContext.get();
                if (tenantId == null || (user.getTenantId() != null && !tenantId.equals(user.getTenantId()))) {
                    throw new ResourceNotFoundException("User", userId);
                }
                if (originalBranchId != null) {
                    boolean hasBranch = (user.getBranchId() != null && originalBranchId.equals(user.getBranchId()))
                        || (user.getBranches() != null && user.getBranches().stream().anyMatch(b -> originalBranchId.equals(b.getId())));
                    if (!hasBranch) {
                        throw new ResourceNotFoundException("User", userId);
                    }
                }
            }

            if (req.firstName()     != null) user.setFirstName(req.firstName());
            if (req.lastName()      != null) user.setLastName(req.lastName());
            if (req.email()         != null) {
                String emailToken = !req.email().isBlank() ? tokenService.token(req.email().trim()) : null;
                if (emailToken != null && userRepo.existsByEmailTokenAndIdNot(emailToken, userId)) {
                    throw new BusinessRuleViolationException("Email '" + req.email() + "' is already registered to another user");
                }
                user.setEmail(req.email());
                user.setEmailToken(emailToken);
            }
            if (req.branchIds() != null) {
                Set<BranchEntity> targetBranches;
                if (isHospitalAdmin) {
                    targetBranches = new HashSet<>(branchRepo.findAllById(req.branchIds()));
                } else {
                    targetBranches = new HashSet<>();
                    if (user.getBranches() != null) {
                        for (BranchEntity b : user.getBranches()) {
                            if (editorBranchId != null && !b.getId().equals(editorBranchId)) {
                                targetBranches.add(b);
                            }
                        }
                    }
                    if (editorBranchId != null && req.branchIds().contains(editorBranchId)) {
                        branchRepo.findById(editorBranchId).ifPresent(targetBranches::add);
                    }
                }
                user.getBranches().retainAll(targetBranches);
                user.getBranches().addAll(targetBranches);
            }
            
            if (req.roleIds() != null) {
                Set<RoleEntity> primaryRoles = resolveRoles(req.roleIds());
                Set<RoleEntity> allRoles = new HashSet<>(primaryRoles);
                
                // Auto-mirror roles to all other authorized branches for the user
                for (RoleEntity role : primaryRoles) {
                    if (role.getBranchId() != null) {
                        for (BranchEntity branch : user.getBranches()) {
                            if (!branch.getId().equals(role.getBranchId())) {
                                roleRepo.findByNameAndTenantIdAndBranchId(role.getName(), user.getTenantId(), branch.getId())
                                    .ifPresent(allRoles::add);
                            }
                        }
                        // Also mirror to the primary branchId if not in branches list
                        if (user.getBranchId() != null && !user.getBranchId().equals(role.getBranchId())) {
                            roleRepo.findByNameAndTenantIdAndBranchId(role.getName(), user.getTenantId(), user.getBranchId())
                                .ifPresent(allRoles::add);
                        }
                    }
                }
                
                Set<RoleEntity> distinctRoles = new HashSet<>(
                    allRoles.stream()
                        .collect(java.util.stream.Collectors.toMap(RoleEntity::getId, r -> r, (r1, r2) -> r1))
                        .values()
                );

                if (!isHospitalAdmin) {
                    Set<RoleEntity> targetRoles = new HashSet<>(distinctRoles);
                    if (user.getRoles() != null) {
                        for (RoleEntity r : user.getRoles()) {
                            if (editorBranchId != null && r.getBranchId() != null && !r.getBranchId().equals(editorBranchId)) {
                                targetRoles.add(r);
                            }
                        }
                    }
                    distinctRoles = targetRoles;
                }

                user.getRoles().retainAll(distinctRoles);
                user.getRoles().addAll(distinctRoles);
            }
            if (req.speechLanguage()!= null) user.setSpeechLanguage(req.speechLanguage());
            if (req.salutation()    != null) user.setSalutation(req.salutation());
            if (req.phoneNo() != null || req.branchIds() != null || req.branchId() != null) {
                String contactNo = req.phoneNo() != null ? req.phoneNo().trim() : user.getPhoneNo();
                String phoneToken = (contactNo != null && !contactNo.isBlank()) ? tokenService.phoneToken(contactNo) : null;
                if (phoneToken != null && user.getTenantId() != null) {
                    Set<UUID> targetBranchIds = new HashSet<>();
                    if (req.branchId() != null) {
                        targetBranchIds.add(req.branchId());
                    }
                    if (req.branchIds() != null) {
                        targetBranchIds.addAll(req.branchIds());
                    }
                    if (targetBranchIds.isEmpty()) {
                        if (user.getBranchId() != null) {
                            targetBranchIds.add(user.getBranchId());
                        }
                        if (user.getBranches() != null) {
                            for (BranchEntity b : user.getBranches()) {
                                targetBranchIds.add(b.getId());
                            }
                        }
                    }
                    for (UUID bId : targetBranchIds) {
                        if (userRepo.existsByPhoneNoTokenAndTenantIdAndBranchIdAndIdNot(phoneToken, user.getTenantId(), bId, userId)) {
                            throw new BusinessRuleViolationException(
                                "Contact number '" + contactNo + "' already exists in branch " +
                                branchRepo.findById(bId).map(BranchEntity::getName).orElse(bId.toString())
                            );
                        }
                        if (consultantRepo.existsByContactNumberTokenAndBranchIdAndStatusNot(phoneToken, bId, EntityStatus.DELETED)) {
                            boolean existsOther = !consultantRepo.findByUserIdAndBranchId(userId, bId).isPresent();
                            if (existsOther) {
                                throw new BusinessRuleViolationException(
                                    "A consultant with contact number '" + contactNo + "' already exists in branch " +
                                    branchRepo.findById(bId).map(BranchEntity::getName).orElse(bId.toString())
                                );
                            }
                        }
                    }
                }
                if (req.phoneNo() != null) {
                    user.setPhoneNo(req.phoneNo());
                    user.setPhoneNoToken(phoneToken);
                }
            }
            user.setShowCasesheet(req.showCasesheet());
            user.setTextAutoSuggest(req.textAutoSuggest());
            user.setModifiedAt(Instant.now());

            if (req.departmentIds() != null) {
                Set<Department> targetDepartments;
                if (isHospitalAdmin) {
                    targetDepartments = new HashSet<>(departmentRepo.findAllById(req.departmentIds()));
                } else {
                    targetDepartments = new HashSet<>();
                    if (user.getDepartments() != null) {
                        for (Department d : user.getDepartments()) {
                            if (editorBranchId != null && d.getBranchId() != null && !d.getBranchId().equals(editorBranchId)) {
                                targetDepartments.add(d);
                            }
                        }
                    }
                    for (Department d : departmentRepo.findAllById(req.departmentIds())) {
                        if (editorBranchId == null || d.getBranchId() == null || d.getBranchId().equals(editorBranchId)) {
                            targetDepartments.add(d);
                        }
                    }
                }
                user.getDepartments().retainAll(targetDepartments);
                user.getDepartments().addAll(targetDepartments);
            }

            if (req.roleIds() != null || req.branchId() != null || req.branchIds() != null) {
                stampScope(user, user.getRoles(), req.branchId(), originalBranchId);
                if (!isHospitalAdmin && existingBranchId != null && user.getBranches().stream().anyMatch(b -> b.getId().equals(existingBranchId))) {
                    user.setBranchId(existingBranchId);
                }
            }

            // Status change also controls account lock — mirrors legacy behaviour
            if (req.status() != null) {
                user.setStatus(req.status() == EntityStatus.ACTIVE ? (short) 1 : (short) 0);
                user.setAccountLocked(req.status() != EntityStatus.ACTIVE);
                // Reset failed login attempts when admin reactivates the account,
            // giving the user 5 fresh login attempts.
            if (req.status() == EntityStatus.ACTIVE) {
                user.setFailedLoginAttempts(0);
            }
        }

            UserEntity saved = userRepo.save(user);
            syncConsultantForUser(saved, originalBranchId);

            // Feedback 2.2: rebuild the permission cache so role/account changes take effect at once.
            rebuildPermissionCache(saved);
            
            if (originalBranchId != null) {
                com.hms.infrastructure.tenant.BranchContext.set(originalBranchId);
            }
            return toResponse(saved);
        } finally {
            if (originalBranchId != null) {
                com.hms.infrastructure.tenant.BranchContext.set(originalBranchId);
            }
        }
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
        userRepo.updatePassword(user.getId(), passwordEncoder.encode(req.newPassword()), Instant.now());
    }

    @Transactional
    public void adminResetPassword(UUID userId, String newPassword) {
        UserEntity user = findInScopeOrThrow(userId);
        userRepo.updatePassword(user.getId(), passwordEncoder.encode(newPassword), Instant.now());
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
        List<RoleEntity> roles = roleRepo.findAllById(roleIds);
        
        HmsUserDetails principal = currentUser();
        boolean isHospitalAdmin = principal.isSuperAdmin() || principal.isHospitalAdmin();
        
        for (RoleEntity r : roles) {
            if (r.getBranchId() == null && !isHospitalAdmin) {
                throw new BusinessRuleViolationException("You do not have permission to assign the tenant-wide role: " + r.getName());
            }
        }
        return new HashSet<>(roles);
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
    private void stampScope(UserEntity user, Set<RoleEntity> roles, UUID requestedBranchId, UUID creatorBranch) {
        UUID creatorTenant = TenantContext.get();

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

        UUID branch = null;
        // If the creator is pinned to a specific branch, force the target user into that branch.
        HmsUserDetails principal = currentUser();
        if (principal.getBranchId() != null) {
            branch = principal.getBranchId();
        } else if (requestedBranchId != null
                && branchRepo.findByIdAndTenantId(requestedBranchId, creatorTenant).isPresent()) {
            branch = requestedBranchId;
        } else if (user.getBranches() != null && !user.getBranches().isEmpty()) {
            branch = user.getBranches().iterator().next().getId();
        } else if (user.getBranchId() != null
                && branchRepo.findByIdAndTenantId(user.getBranchId(), creatorTenant).isPresent()) {
            branch = user.getBranchId();
        } else if (creatorBranch != null
                && branchRepo.findByIdAndTenantId(creatorBranch, creatorTenant).isPresent()) {
            branch = creatorBranch;
        } else {
            branch = branchRepo.findByTenantIdAndIsDefaultTrue(creatorTenant)
                .map(BranchEntity::getId).orElse(null);
        }
        if (branch == null) {
            throw new BusinessRuleViolationException(
                "No branch available to assign this user. Create a branch first.");
        }
        user.setBranchId(branch);
        if (user.getBranches() == null) {
            user.setBranches(new HashSet<>());
        }
        final UUID bId = branch;
        if (user.getBranches().stream().noneMatch(b -> b.getId().equals(bId))) {
            branchRepo.findById(bId).ifPresent(b -> user.getBranches().add(b));
        }
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
        if (branchId != null) {
            boolean hasBranch = (user.getBranchId() != null && branchId.equals(user.getBranchId()))
                || (user.getBranches() != null && user.getBranches().stream().anyMatch(b -> branchId.equals(b.getId())));
            if (!hasBranch) {
                throw new ResourceNotFoundException("User", id); // branch admin can't reach other branches
            }
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

        UUID activeBranchId = BranchContext.get();
        UUID consultantId = null;
        if (activeBranchId != null) {
            consultantId = consultantRepo.findByUserIdAndBranchId(u.getId(), activeBranchId)
                .map(com.hms.domain.consultant.model.Consultant::getId)
                .orElse(null);
        } else {
            List<com.hms.domain.consultant.model.Consultant> consultants = consultantRepo.findByUserId(u.getId());
            if (!consultants.isEmpty()) {
                consultantId = consultants.get(0).getId();
            }
        }

        Set<UUID> branchIds = u.getBranches().stream()
            .map(com.hms.infrastructure.persistence.tenant.BranchEntity::getId)
            .collect(Collectors.toSet());

        return new UserResponse(
            u.getId(), u.getUsername(), u.getFirstName(), u.getLastName(),
            u.getFirstName() + " " + u.getLastName(),
            u.getEmail(),
            u.getStatus() == 1 ? EntityStatus.ACTIVE : EntityStatus.INACTIVE,
            u.isAccountLocked(), roleSummaries,
            departmentIds, new HashSet<>(), consultantId,
            u.isShowCasesheet(), u.getSpeechLanguage(), u.isTextAutoSuggest(),
            u.getSalutation(), u.getPhoneNo(), u.getBranchId(), branchIds
        );
    }

    /**
     * Auto-syncs a Consultant entity when a user gets the DOCTOR role.
     * Ensures they show up as available doctors in dropdowns and schedules.
     */
    private void syncConsultantForUser(UserEntity user, UUID fallbackBranchId) {
        boolean hasDoctorRole = user.getRoles().stream()
            .anyMatch(r -> "DOCTOR".equalsIgnoreCase(r.getName()));

        if (hasDoctorRole) {
            Set<UUID> userBranchIds = new HashSet<>();
            if (user.getBranchId() != null) {
                userBranchIds.add(user.getBranchId());
            }
            if (user.getBranches() != null) {
                for (BranchEntity b : user.getBranches()) {
                    userBranchIds.add(b.getId());
                }
            }
            if (userBranchIds.isEmpty() && fallbackBranchId != null) {
                userBranchIds.add(fallbackBranchId);
            }

            boolean isHospitalAdmin = false;
            org.springframework.security.core.Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof HmsUserDetails currDetails) {
                if (currDetails.isHospitalAdmin() || currDetails.isSuperAdmin()) {
                    isHospitalAdmin = true;
                }
            }

            Set<UUID> branchesToSync = new HashSet<>();
            if (isHospitalAdmin) {
                branchesToSync.addAll(userBranchIds);
            } else {
                UUID activeBranch = BranchContext.get();
                if (activeBranch != null) {
                    if (userBranchIds.contains(activeBranch)) {
                        branchesToSync.add(activeBranch);
                    }
                }
            }

            for (UUID branchId : branchesToSync) {
                int updated = consultantRepo.updateProfileForBranch(
                    user.getId(), branchId,
                    user.getFirstName(),
                    user.getLastName() != null && !user.getLastName().isBlank() ? user.getLastName() : ".",
                    user.getSalutation(),
                    user.getEmail(),
                    user.getPhoneNo(),
                    user.getPhoneNoToken()
                );

                if (updated == 0) {
                    Consultant c = new Consultant();
                    c.setUserId(user.getId());
                    c.setBranchId(branchId);
                    c.setStatus(EntityStatus.ACTIVE);
                    c.setSalutation(user.getSalutation());
                    c.setFirstName(user.getFirstName());
                    c.setLastName(user.getLastName() != null && !user.getLastName().isBlank() ? user.getLastName() : ".");
                    c.setEmail(user.getEmail());
                    c.setContact(user.getPhoneNo());
                    c.setContactNumberToken(user.getPhoneNoToken());
                    c.setConsultantType(ConsultantType.PERMANENT);
                    
                    if (user.getDepartments() != null && !user.getDepartments().isEmpty()) {
                        c.setDepartmentId(user.getDepartments().iterator().next().getId());
                    }

                    c.setTenantId(user.getTenantId());
                    consultantRepo.save(c);
                }
            }

            if (isHospitalAdmin && !userBranchIds.isEmpty()) {
                consultantRepo.deleteConsultantsForBranchesNotIn(user.getId(), userBranchIds);
            }

            consultantRepo.updateProfileDetails(
                user.getId(),
                user.getFirstName(),
                user.getLastName() != null && !user.getLastName().isBlank() ? user.getLastName() : ".",
                user.getSalutation(),
                user.getEmail(),
                user.getPhoneNo(),
                user.getPhoneNoToken()
            );
        } else {
            boolean isHospitalAdmin = false;
            org.springframework.security.core.Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof HmsUserDetails currDetails) {
                if (currDetails.isHospitalAdmin() || currDetails.isSuperAdmin()) {
                    isHospitalAdmin = true;
                }
            }

            if (isHospitalAdmin) {
                consultantRepo.deleteAllConsultantsForUser(user.getId());
            } else {
                UUID activeBranch = BranchContext.get();
                if (activeBranch != null) {
                    consultantRepo.deleteConsultantForUserInBranch(user.getId(), activeBranch);
                }
            }
        }
    }
}
