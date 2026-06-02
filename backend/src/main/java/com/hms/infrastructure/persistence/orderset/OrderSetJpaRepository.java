package com.hms.infrastructure.persistence.orderset;

import com.hms.domain.orderset.model.OrderSet;
import com.hms.domain.shared.model.EntityStatus;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface OrderSetJpaRepository extends JpaRepository<OrderSet, UUID> {

    @Query("SELECT o FROM OrderSet o WHERE o.status = com.hms.domain.shared.model.EntityStatus.ACTIVE ORDER BY o.name")
    List<OrderSet> findAllActive();

    @Query("SELECT o FROM OrderSet o WHERE o.status = com.hms.domain.shared.model.EntityStatus.ACTIVE " +
           "AND (:q IS NULL OR LOWER(o.name) LIKE LOWER(CONCAT('%', :q, '%'))) ORDER BY o.name")
    List<OrderSet> searchActive(@Param("q") String q);

    @Query("SELECT o FROM OrderSet o WHERE o.status = com.hms.domain.shared.model.EntityStatus.ACTIVE " +
           "AND o.isFavorite = true AND o.consultantId = :consultantId ORDER BY o.name")
    List<OrderSet> findFavoritesByConsultant(@Param("consultantId") UUID consultantId);

    @Query("SELECT o FROM OrderSet o WHERE o.status = com.hms.domain.shared.model.EntityStatus.ACTIVE " +
           "AND (o.scope = 'GLOBAL' OR o.consultantId = :consultantId OR o.departmentId = :departmentId) " +
           "ORDER BY o.name")
    List<OrderSet> findAccessible(@Param("consultantId") UUID consultantId, @Param("departmentId") UUID departmentId);
}
