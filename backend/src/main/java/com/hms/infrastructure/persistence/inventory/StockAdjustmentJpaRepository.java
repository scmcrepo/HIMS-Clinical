package com.hms.infrastructure.persistence.inventory;

import com.hms.domain.inventory.model.StockAdjustment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;

public interface StockAdjustmentJpaRepository extends JpaRepository<StockAdjustment, UUID> {
    List<StockAdjustment> findAllByOrderByCreatedAtDesc();
    Page<StockAdjustment> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<StockAdjustment> findBySequenceNumberContainingIgnoreCaseOrderByCreatedAtDesc(String sequenceNumber, Pageable pageable);
}
