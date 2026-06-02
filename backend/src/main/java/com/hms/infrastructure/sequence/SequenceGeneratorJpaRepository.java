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
}
