package com.hms.infrastructure.persistence.bulkupload;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BulkImportJobJpaRepository extends JpaRepository<BulkImportJobEntity, UUID> {
}
