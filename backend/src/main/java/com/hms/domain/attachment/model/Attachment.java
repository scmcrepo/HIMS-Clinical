package com.hms.domain.attachment.model;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "attachments", indexes = {
    @Index(name = "idx_att_encounter", columnList = "encounter_id"),
    @Index(name = "idx_att_patient",   columnList = "patient_id"),
    @Index(name = "idx_att_type",      columnList = "attachment_type")
})
@Getter @Setter @NoArgsConstructor
public class Attachment extends AuditableEntity {

    @Column(name = "encounter_id") private UUID encounterId;
    @Column(name = "patient_id")   private UUID patientId;
    @Column(name = "provider_id")  private UUID providerId;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "attachment_type", nullable = false)
    private AttachmentType attachmentType;

    @Column(name = "category", length = 40) private String category;

    @Column(name = "file_name", nullable = false, length = 255) private String fileName;
    @Column(name = "file_path", nullable = false, length = 500) private String filePath;
    @Column(name = "content_type", length = 80) private String contentType;
    @Column(name = "meta_data", columnDefinition = "TEXT") private String metaData;
}
