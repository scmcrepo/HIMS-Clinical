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
    List<Attachment> findByCategoryOrderByCreatedAtDesc(String category);

    @Query("SELECT a FROM Attachment a WHERE a.category = :category " +
           "AND (:encounterId IS NULL OR a.encounterId = :encounterId) " +
           "AND (:patientId IS NULL OR a.patientId = :patientId) " +
           "ORDER BY a.createdAt DESC")
    List<Attachment> findByCategoryAndScope(@Param("category") String category, 
                                           @Param("encounterId") UUID encounterId, 
                                           @Param("patientId") UUID patientId);
    
    @Query("SELECT a FROM Attachment a WHERE a.encounterId = :eid AND a.attachmentType = :type ORDER BY a.createdAt DESC")
    List<Attachment> findByEncounterIdAndType(@Param("eid") UUID encounterId, @Param("type") AttachmentType type);

    @Query(value = "SELECT * FROM attachments WHERE category = :category " +
                   "AND tenant_id = :tenantId " +
                   "AND branch_id = :branchId " +
                   "ORDER BY created_at DESC LIMIT 1", nativeQuery = true)
    Optional<Attachment> findLatestByCategoryAndScopeNative(@Param("category") String category, 
                                                            @Param("tenantId") UUID tenantId, 
                                                            @Param("branchId") UUID branchId);

    @Query(value = "SELECT * FROM attachments WHERE category = :category " +
                   "AND tenant_id = :tenantId " +
                   "AND branch_id IS NULL " +
                   "ORDER BY created_at DESC LIMIT 1", nativeQuery = true)
    Optional<Attachment> findLatestByCategoryAndTenantOnlyNative(@Param("category") String category, 
                                                                 @Param("tenantId") UUID tenantId);

    @Query(value = "SELECT * FROM attachments WHERE category = :category " +
                   "AND tenant_id = :tenantId " +
                   "ORDER BY created_at DESC LIMIT 1", nativeQuery = true)
    Optional<Attachment> findLatestByCategoryAndTenantAnyBranchNative(@Param("category") String category, 
                                                                      @Param("tenantId") UUID tenantId);

    @Query(value = "SELECT * FROM attachments WHERE category = :category " +
                   "AND tenant_id IS NULL " +
                   "AND branch_id IS NULL " +
                   "ORDER BY created_at DESC LIMIT 1", nativeQuery = true)
    Optional<Attachment> findLatestGlobalLogoNative(@Param("category") String category);

    @Query(value = "SELECT * FROM attachments WHERE category = :category AND tenant_id = :tenantId", nativeQuery = true)
    List<Attachment> findAllByCategoryAndTenantNative(@Param("category") String category, @Param("tenantId") UUID tenantId);
}
