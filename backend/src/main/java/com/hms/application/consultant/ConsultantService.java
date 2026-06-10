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
import lombok.RequiredArgsConstructor;
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
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Consultant create(Consultant req, MultipartFile photo) throws IOException {
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
    public List<Consultant> searchNonDeletedByName(String name) { return repo.searchNonDeletedByName(name); }

    @Transactional(readOnly = true)
    public Consultant getById(UUID id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Consultant", id));
    }

    @Transactional(readOnly = true)
    public List<Consultant> searchByName(String name) { return repo.searchByName(name); }

    @Transactional(readOnly = true)
    public List<Consultant> getByType(ConsultantType type) {
        return repo.findAllActive().stream().filter(c -> c.getConsultantType() == type).toList();
    }

    @Transactional
    public Consultant update(UUID id, Consultant req, MultipartFile photo) throws IOException {
        Consultant existing = getById(id);
        existing.setSalutation(req.getSalutation());
        existing.setFirstName(req.getFirstName());
        existing.setLastName(req.getLastName());
        existing.setConsultantType(req.getConsultantType());
        existing.setSpecialisation(req.getSpecialisation());
        existing.setContact(req.getContact());
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
                u.setSalutation(existing.getSalutation());
                if (existing.getDepartmentId() != null) {
                    departmentRepo.findById(existing.getDepartmentId()).ifPresent(d -> {
                        u.setDepartments(Set.of(d));
                    });
                } else {
                    u.setDepartments(Set.of());
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
        }
        String username = baseUsername;
        int counter = 1;
        while (userRepo.existsByUsername(username)) {
            username = baseUsername + counter;
            counter++;
        }

        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("password"));
        user.setFirstName(consultant.getFirstName());
        user.setLastName(consultant.getLastName() != null && !consultant.getLastName().isBlank() ? consultant.getLastName() : ".");
        user.setEmail(consultant.getEmail());
        user.setPhoneNo(consultant.getContact());
        user.setSalutation(consultant.getSalutation());
        user.setStatus((short) (consultant.getStatus() == EntityStatus.ACTIVE ? 1 : 0));
        user.setAccountLocked(consultant.getStatus() != EntityStatus.ACTIVE);
        user.setSpeechLanguage("en-IN");
        user.setTextAutoSuggest(true);
        user.setShowCasesheet(false);
        user.setCreatedAt(java.time.Instant.now());
        user.setModifiedAt(java.time.Instant.now());

        var doctorRoleOpt = roleRepo.findByName("DOCTOR");
        if (doctorRoleOpt.isPresent()) {
            user.setRoles(Set.of(doctorRoleOpt.get()));
        }

        if (consultant.getDepartmentId() != null) {
            var deptOpt = departmentRepo.findById(consultant.getDepartmentId());
            if (deptOpt.isPresent()) {
                user.setDepartments(Set.of(deptOpt.get()));
            }
        }

        return userRepo.save(user);
    }
}
