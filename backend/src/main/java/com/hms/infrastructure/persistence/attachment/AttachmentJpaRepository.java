package com.hms.infrastructure.persistence.attachment;
import com.hms.domain.attachment.model.Attachment;
import com.hms.domain.attachment.model.AttachmentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface AttachmentJpaRepository extends JpaRepository<Attachment, UUID> {
    List<Attachment> findByEncounterIdOrderByCreatedAtDesc(UUID encounterId);
    List<Attachment> findByPatientIdOrderByCreatedAtDesc(UUID patientId);
    Optional<Attachment> findFirstByProviderIdAndAttachmentType(UUID providerId, AttachmentType type);
    Optional<Attachment> findFirstByPatientIdAndAttachmentType(UUID patientId, AttachmentType type);
    Optional<Attachment> findFirstByCategoryOrderByCreatedAtDesc(String category);
    @Query("SELECT a FROM Attachment a WHERE a.encounterId = :eid AND a.attachmentType = :type ORDER BY a.createdAt DESC")
    List<Attachment> findByEncounterIdAndType(@Param("eid") UUID encounterId, @Param("type") AttachmentType type);
}
