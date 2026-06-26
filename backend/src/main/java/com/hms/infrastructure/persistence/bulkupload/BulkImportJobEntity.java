package com.hms.infrastructure.persistence.bulkupload;

import com.hms.domain.shared.model.AuditableEntity;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.util.List;

@Entity
@Table(name = "bulk_import_jobs")
@Getter
@Setter
@NoArgsConstructor
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@org.hibernate.annotations.Filter(name = "branchFilter", condition = "branch_id = :branchId")
public class BulkImportJobEntity extends AuditableEntity {

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "job_status", nullable = false)
    private String jobStatus;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "created_count", nullable = false)
    private int createdCount;

    @Column(name = "skipped_count", nullable = false)
    private int skippedCount;

    @Column(name = "error_count", nullable = false)
    private int errorCount;

    @Type(JsonBinaryType.class)
    @Column(name = "errors", columnDefinition = "jsonb")
    private List<String> errors;

}
