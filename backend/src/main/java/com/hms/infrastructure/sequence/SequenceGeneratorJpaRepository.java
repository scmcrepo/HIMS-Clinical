package com.hms.infrastructure.sequence;
import com.hms.domain.billing.model.DocumentType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface SequenceGeneratorJpaRepository extends JpaRepository<SequenceGeneratorEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SequenceGeneratorEntity s WHERE s.documentType = :type AND s.activated = true")
    Optional<SequenceGeneratorEntity> findActiveByDocumentTypeForUpdate(@Param("type") DocumentType type);

    @Query("SELECT s FROM SequenceGeneratorEntity s WHERE s.documentType = :type ORDER BY s.createdAt DESC")
    List<SequenceGeneratorEntity> findAllByDocumentType(@Param("type") DocumentType type);

    Optional<SequenceGeneratorEntity> findByPrefixStringIgnoreCase(String prefixString);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SequenceGeneratorEntity s WHERE s.documentType = :type AND s.activated = true " +
           "AND s.tenantId = :tenantId AND " +
           "((:type = com.hms.domain.billing.model.DocumentType.PATIENT AND s.branchId IS NULL) OR " +
           "(:type != com.hms.domain.billing.model.DocumentType.PATIENT AND s.branchId = :branchId))")
    Optional<SequenceGeneratorEntity> findActiveByDocumentTypeTenantAndBranchForUpdate(
        @Param("type") DocumentType type,
        @Param("tenantId") UUID tenantId,
        @Param("branchId") UUID branchId
    );

    @Query("SELECT s FROM SequenceGeneratorEntity s WHERE s.documentType = :type " +
           "AND s.tenantId = :tenantId AND " +
           "((:type = com.hms.domain.billing.model.DocumentType.PATIENT AND s.branchId IS NULL) OR " +
           "(:type != com.hms.domain.billing.model.DocumentType.PATIENT AND s.branchId = :branchId)) " +
           "ORDER BY s.createdAt DESC")
    List<SequenceGeneratorEntity> findAllByDocumentTypeTenantAndBranch(
        @Param("type") DocumentType type,
        @Param("tenantId") UUID tenantId,
        @Param("branchId") UUID branchId
    );

    List<SequenceGeneratorEntity> findAllByTenantId(UUID tenantId);

    @Query("SELECT s FROM SequenceGeneratorEntity s WHERE LOWER(s.prefixString) = LOWER(:prefix) " +
           "AND s.tenantId = :tenantId AND " +
           "((s.documentType = com.hms.domain.billing.model.DocumentType.PATIENT AND :isPatient = true) OR " +
           "(s.documentType != com.hms.domain.billing.model.DocumentType.PATIENT AND s.branchId = :branchId))")
    List<SequenceGeneratorEntity> findConflictingPrefixes(
        @Param("prefix") String prefix,
        @Param("tenantId") UUID tenantId,
        @Param("branchId") UUID branchId,
        @Param("isPatient") boolean isPatient
    );
}
