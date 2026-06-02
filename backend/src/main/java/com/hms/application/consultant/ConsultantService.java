package com.hms.application.consultant;
import com.hms.application.attachment.AttachmentService;
import com.hms.domain.attachment.model.AttachmentType;
import com.hms.domain.consultant.model.*;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.consultant.ConsultantJpaRepository;
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

    @Transactional
    public Consultant create(Consultant req, MultipartFile photo) throws IOException {
        Consultant saved = repo.save(req);
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
        repo.save(existing);
    }
}
