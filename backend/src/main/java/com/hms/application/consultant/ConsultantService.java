package com.hms.application.consultant;
import com.hms.application.attachment.AttachmentService;
import com.hms.domain.attachment.model.AttachmentType;
import com.hms.domain.consultant.model.*;
import com.hms.domain.shared.model.EntityStatus;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.consultant.ConsultantJpaRepository;
import com.hms.infrastructure.persistence.shared.UserJpaRepository;
import com.hms.infrastructure.persistence.role.RoleJpaRepository;
import com.hms.infrastructure.persistence.department.DepartmentJpaRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.hms.infrastructure.persistence.shared.UserEntity;
import com.hms.infrastructure.persistence.tenant.BranchJpaRepository;
import lombok.RequiredArgsConstructor;
import com.hms.infrastructure.tenant.TenantContext;
import com.hms.infrastructure.tenant.BranchContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;

@Service @RequiredArgsConstructor
public class ConsultantService {
    private final ConsultantJpaRepository repo;
    private final AttachmentService attachmentService;
    private final UserJpaRepository userRepo;
    private final RoleJpaRepository roleRepo;
    private final DepartmentJpaRepository departmentRepo;
    private final BranchJpaRepository branchRepo;
    private final PasswordEncoder passwordEncoder;
    private final com.hms.security.encryption.PiiSearchTokenService tokenService;

    @Transactional
    public Consultant create(Consultant req, MultipartFile photo) throws IOException {
        if (req.getContact() == null || req.getContact().isBlank()) {
            throw new com.hms.exception.BusinessRuleViolationException("Contact number is required");
        }
        String contact = req.getContact().trim();
        String contactToken = tokenService.phoneToken(contact);
        UUID branchId = req.getBranchId() != null ? req.getBranchId() : BranchContext.get();
        if (branchId == null) {
            throw new com.hms.exception.BusinessRuleViolationException("Branch is required for consultant");
        }
        if (contactToken != null && repo.existsByContactNumberTokenAndBranchIdAndStatusNot(contactToken, branchId, EntityStatus.DELETED)) {
            throw new com.hms.exception.BusinessRuleViolationException(
                "Contact number '" + req.getContact() + "' already exists in this branch");
        }
        req.setContact(contact);
        req.setContactNumberToken(contactToken);

        Consultant saved = repo.save(req);

        // Auto-create a User for the newly created consultant
        UserEntity savedUser = autoCreateUserForConsultant(saved);
        saved.setUserId(savedUser.getId());
        saved = repo.save(saved);

        if (photo != null && !photo.isEmpty()) {
            var att = attachmentService.saveAttachment(photo, AttachmentType.CONSULTANT,
                null, null, saved.getId(), null);
            saved.setPhotoAttachmentId(att.getId());
            saved = repo.save(saved);
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Consultant> getAll() { return repo.findAllActive(); }

    @Transactional(readOnly = true)
    public List<Consultant> getAllNonDeleted() { return repo.findAllNonDeleted(); }

    @Transactional(readOnly = true)
    public List<Consultant> searchNonDeletedByName(String name) {
        if (name == null || name.isBlank()) return repo.findAllNonDeleted();
        String lowerName = name.toLowerCase(java.util.Locale.ROOT);
        return repo.findAllNonDeleted().stream()
            .filter(c -> {
                String fn = c.getFirstName();
                String ln = c.getLastName();
                return (fn != null && fn.toLowerCase(java.util.Locale.ROOT).contains(lowerName))
                    || (ln != null && ln.toLowerCase(java.util.Locale.ROOT).contains(lowerName));
            })
            .toList();
    }

    @Transactional(readOnly = true)
    public Consultant getById(UUID id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Consultant", id));
    }

    @Transactional(readOnly = true)
    public List<Consultant> searchByName(String name) {
        if (name == null || name.isBlank()) return repo.findAllActive();
        String lowerName = name.toLowerCase(java.util.Locale.ROOT);
        return repo.findAllActiveForNameSearch().stream()
            .filter(c -> {
                String fn = c.getFirstName();
                String ln = c.getLastName();
                return (fn != null && fn.toLowerCase(java.util.Locale.ROOT).contains(lowerName))
                    || (ln != null && ln.toLowerCase(java.util.Locale.ROOT).contains(lowerName));
            })
            .toList();
    }

    @Transactional(readOnly = true)
    public List<Consultant> getByType(ConsultantType type) {
        return repo.findAllActive().stream().filter(c -> c.getConsultantType() == type).toList();
    }

    @Transactional
    public Consultant update(UUID id, Consultant req, MultipartFile photo) throws IOException {
        if (req.getContact() == null || req.getContact().isBlank()) {
            throw new com.hms.exception.BusinessRuleViolationException("Contact number is required");
        }
        Consultant existing = getById(id);
        String contact = req.getContact().trim();
        String contactToken = tokenService.phoneToken(contact);
        UUID branchId = req.getBranchId() != null ? req.getBranchId() : (existing.getBranchId() != null ? existing.getBranchId() : BranchContext.get());
        if (branchId == null) {
            throw new com.hms.exception.BusinessRuleViolationException("Branch is required for consultant");
        }
        if (contactToken != null && repo.existsByContactNumberTokenAndBranchIdAndStatusNotAndIdNot(contactToken, branchId, EntityStatus.DELETED, id)) {
            throw new com.hms.exception.BusinessRuleViolationException(
                "Contact number '" + req.getContact() + "' already exists in this branch");
        }

        existing.setSalutation(req.getSalutation());
        existing.setFirstName(req.getFirstName());
        existing.setLastName(req.getLastName());
        existing.setConsultantType(req.getConsultantType());
        existing.setSpecialisation(req.getSpecialisation());
        existing.setContact(contact);
        existing.setContactNumberToken(contactToken);
        existing.setEmail(req.getEmail());
        existing.setRegistrationNo(req.getRegistrationNo());
        existing.setQualification(req.getQualification());
        existing.setAddress(req.getAddress());
        existing.setDepartmentId(req.getDepartmentId());
        if (req.getStatus() != null) {
            existing.setStatus(req.getStatus());
        }

        // If a linked user does not exist, auto-create one
        if (existing.getUserId() == null) {
            UserEntity savedUser = autoCreateUserForConsultant(existing);
            existing.setUserId(savedUser.getId());
        } else {
            // If a linked user exists, update it as well
            userRepo.findById(existing.getUserId()).ifPresent(u -> {
                u.setFirstName(existing.getFirstName());
                u.setLastName(existing.getLastName() != null && !existing.getLastName().isBlank() ? existing.getLastName() : ".");
                u.setEmail(existing.getEmail());
                u.setPhoneNo(existing.getContact());
                u.setPhoneNoToken(tokenService.phoneToken(existing.getContact() != null ? existing.getContact().trim() : null));
                u.setSalutation(existing.getSalutation());
                if (existing.getDepartmentId() != null) {
                    departmentRepo.findById(existing.getDepartmentId()).ifPresent(d -> {
                        u.getDepartments().clear();
                        u.getDepartments().add(d);
                    });
                } else {
                    u.getDepartments().clear();
                }
                if (existing.getStatus() != null) {
                    u.setStatus((short) (existing.getStatus() == EntityStatus.ACTIVE ? 1 : 0));
                    u.setAccountLocked(existing.getStatus() != EntityStatus.ACTIVE);
                }
                userRepo.save(u);
            });
        }

        if (photo != null && !photo.isEmpty()) {
            var att = attachmentService.saveAttachment(photo, AttachmentType.CONSULTANT,
                null, null, existing.getId(), null);
            existing.setPhotoAttachmentId(att.getId());
        }
        return repo.save(existing);
    }

    @Transactional
    public void delete(UUID id) {
        Consultant existing = getById(id);
        existing.softDelete(); // Soft delete
        if (existing.getUserId() != null) {
            userRepo.findById(existing.getUserId()).ifPresent(u -> {
                u.setStatus((short) 0);
                u.setAccountLocked(true);
                userRepo.save(u);
            });
        }
        repo.save(existing);
    }

    private UserEntity autoCreateUserForConsultant(Consultant consultant) {
        String baseUsername = (consultant.getFirstName() + "." + (consultant.getLastName() != null ? consultant.getLastName() : ""))
            .toLowerCase()
            .replaceAll("\\s+", "")
            .replaceAll("[^a-z0-9._-]", "");
        if (baseUsername.endsWith(".")) {
            baseUsername = baseUsername.substring(0, baseUsername.length() - 1);
        }
        if (baseUsername.startsWith(".")) {
            baseUsername = baseUsername.substring(1);
        }
        if (baseUsername.isEmpty()) {
            baseUsername = "consultant";
        } else if (baseUsername.length() > 25) {
            baseUsername = baseUsername.substring(0, 25);
        }

        UUID tenantId = consultant.getTenantId() != null ? consultant.getTenantId() : TenantContext.require();
        UUID branchId = consultant.getBranchId() != null ? consultant.getBranchId() : BranchContext.get();

        // Check if a user with this baseUsername already exists in the same tenant
        Optional<UserEntity> existingUserOpt = userRepo.findByUsername(baseUsername);
        if (existingUserOpt.isPresent()) {
            UserEntity existingUser = existingUserOpt.get();
            if (existingUser.getTenantId().equals(tenantId)) {
                // Yes, same tenant! Link this user to the new branch instead of creating a duplicate user
                if (branchId != null) {
                    branchRepo.findById(branchId).ifPresent(b -> {
                        existingUser.getBranches().add(b);
                    });
                }
                
                var doctorRoleOpt = roleRepo.findByNameAndTenantId("DOCTOR", tenantId);
                if (doctorRoleOpt.isPresent()) {
                    existingUser.getRoles().add(doctorRoleOpt.get());
                }

                if (consultant.getDepartmentId() != null) {
                    departmentRepo.findById(consultant.getDepartmentId()).ifPresent(d -> {
                        existingUser.getDepartments().add(d);
                    });
                }

                if (existingUser.getFirstName() == null || existingUser.getFirstName().isBlank()) {
                    existingUser.setFirstName(consultant.getFirstName());
                }
                if (existingUser.getLastName() == null || existingUser.getLastName().isBlank() || existingUser.getLastName().equals(".")) {
                    existingUser.setLastName(consultant.getLastName() != null && !consultant.getLastName().isBlank() ? consultant.getLastName() : ".");
                }
                if (existingUser.getEmail() == null || existingUser.getEmail().isBlank()) {
                    existingUser.setEmail(consultant.getEmail());
                }
                existingUser.setModifiedAt(java.time.Instant.now());

                return userRepo.save(existingUser);
            }
        }

        String username = baseUsername;
        int counter = 1;
        while (userRepo.existsByUsername(username)) {
            String suffix = String.valueOf(counter);
            int maxBaseLen = 25 - suffix.length();
            String truncatedBase = baseUsername.length() > maxBaseLen 
                ? baseUsername.substring(0, maxBaseLen) 
                : baseUsername;
            username = truncatedBase + suffix;
            counter++;
        }

        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("password"));
        user.setFirstName(consultant.getFirstName());
        user.setLastName(consultant.getLastName() != null && !consultant.getLastName().isBlank() ? consultant.getLastName() : ".");
        user.setEmail(consultant.getEmail());
        user.setPhoneNo(consultant.getContact());
        user.setPhoneNoToken(tokenService.phoneToken(consultant.getContact() != null ? consultant.getContact().trim() : null));
        user.setSalutation(consultant.getSalutation());
        user.setStatus((short) (consultant.getStatus() == EntityStatus.ACTIVE ? 1 : 0));
        user.setAccountLocked(consultant.getStatus() != EntityStatus.ACTIVE);
        user.setSpeechLanguage("en-IN");
        user.setTextAutoSuggest(true);
        user.setShowCasesheet(false);
        user.setCreatedAt(java.time.Instant.now());
        user.setModifiedAt(java.time.Instant.now());

        user.setTenantId(tenantId);
        user.setBranchId(branchId);

        // Populate branches join table with their primary branch initially
        if (branchId != null) {
            branchRepo.findById(branchId).ifPresent(b -> {
                user.setBranches(new HashSet<>(Set.of(b)));
            });
        }

        var doctorRoleOpt = roleRepo.findByNameAndTenantId("DOCTOR", tenantId);
        if (doctorRoleOpt.isPresent()) {
            user.setRoles(new HashSet<>(Set.of(doctorRoleOpt.get())));
        }

        if (consultant.getDepartmentId() != null) {
            var deptOpt = departmentRepo.findById(consultant.getDepartmentId());
            if (deptOpt.isPresent()) {
                user.setDepartments(new HashSet<>(Set.of(deptOpt.get())));
            }
        }

        return userRepo.save(user);
    }
}
