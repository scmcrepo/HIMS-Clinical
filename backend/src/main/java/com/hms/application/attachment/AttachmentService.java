package com.hms.application.attachment;
import com.hms.domain.attachment.model.*;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.attachment.AttachmentJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
@Service @RequiredArgsConstructor @Slf4j
public class AttachmentService {
    private final AttachmentJpaRepository attachmentRepo;
    @Value("${hms.storage.attachment-base-path:/var/hms/attachments}") private String basePath;

    /**
     * Save a file and create an Attachment record.
     * For CONSULTANT and PATIENT_PICTURE types: upsert (one file per entity).
     */
    @Transactional
    public Attachment saveAttachment(MultipartFile file, AttachmentType type,
                                     UUID encounterId, UUID patientId, UUID providerId,
                                     String category) throws IOException {
        if (file == null || file.isEmpty()) throw new BusinessRuleViolationException("File is empty");
        String fileName   = sanitizeFileName(Objects.requireNonNull(file.getOriginalFilename()));
        String subDir     = type.name().toLowerCase();
        Path   targetDir  = Paths.get(basePath, subDir);
        Files.createDirectories(targetDir);
        String storedName = UUID.randomUUID() + "_" + fileName;
        Path   targetPath = targetDir.resolve(storedName);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        // Upsert for CONSULTANT (one photo per provider)
        if (type == AttachmentType.CONSULTANT && providerId != null) {
            return attachmentRepo.findFirstByProviderIdAndAttachmentType(providerId, type)
                .map(existing -> updateFilePath(existing, targetPath.toString(), fileName, file.getContentType()))
                .orElseGet(() -> createRecord(type, encounterId, patientId, providerId, category, fileName, targetPath.toString(), file.getContentType()));
        }
        // Upsert for PATIENT_PICTURE (one photo per patient)
        if (type == AttachmentType.PATIENT_PICTURE && patientId != null) {
            return attachmentRepo.findFirstByPatientIdAndAttachmentType(patientId, type)
                .map(existing -> updateFilePath(existing, targetPath.toString(), fileName, file.getContentType()))
                .orElseGet(() -> createRecord(type, encounterId, patientId, providerId, category, fileName, targetPath.toString(), file.getContentType()));
        }
        return createRecord(type, encounterId, patientId, providerId, category, fileName, targetPath.toString(), file.getContentType());
    }

    @Transactional(readOnly = true)
    public List<Attachment> getByEncounter(UUID encounterId) {
        return attachmentRepo.findByEncounterIdOrderByCreatedAtDesc(encounterId);
    }

    @Transactional(readOnly = true)
    public List<Attachment> getByPatient(UUID patientId) {
        return attachmentRepo.findByPatientIdOrderByCreatedAtDesc(patientId);
    }

    @Transactional(readOnly = true)
    public Resource downloadFile(UUID attachmentId) {
        Attachment attachment = attachmentRepo.findById(attachmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Attachment", attachmentId));
        try {
            Path filePath = Paths.get(attachment.getFilePath());
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new BusinessRuleViolationException("File not found on disk: " + attachment.getFileName());
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new BusinessRuleViolationException("Invalid file path for attachment: " + attachmentId);
        }
    }

    @Transactional
    public void deleteAttachment(UUID attachmentId) {
        Attachment attachment = attachmentRepo.findById(attachmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Attachment", attachmentId));
        try { Files.deleteIfExists(Paths.get(attachment.getFilePath())); } catch (IOException e) {
            log.warn("Could not delete file on disk for attachment {}: {}", attachmentId, e.getMessage());
        }
        attachmentRepo.delete(attachment); // Physical delete — no soft delete for attachments
    }

    @Transactional(readOnly = true)
    public Attachment getById(UUID id) {
        return attachmentRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Attachment", id));
    }

    @Transactional(readOnly = true)
    public Optional<Attachment> getLatestByCategory(String category) {
        return attachmentRepo.findFirstByCategoryOrderByCreatedAtDesc(category);
    }

    private Attachment createRecord(AttachmentType type, UUID encounterId, UUID patientId,
                                    UUID providerId, String category, String fileName,
                                    String filePath, String contentType) {
        Attachment a = new Attachment();
        a.setAttachmentType(type); a.setEncounterId(encounterId); a.setPatientId(patientId);
        a.setProviderId(providerId); a.setCategory(category);
        a.setFileName(fileName); a.setFilePath(filePath); a.setContentType(contentType);
        return attachmentRepo.save(a);
    }

    private Attachment updateFilePath(Attachment existing, String newPath, String fileName, String contentType) {
        existing.setFilePath(newPath); existing.setFileName(fileName); existing.setContentType(contentType);
        return attachmentRepo.save(existing);
    }

    private String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
