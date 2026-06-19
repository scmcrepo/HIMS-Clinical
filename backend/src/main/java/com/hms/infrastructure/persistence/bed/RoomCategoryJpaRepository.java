package com.hms.infrastructure.persistence.bed;
import com.hms.domain.bed.model.RoomCategory;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface RoomCategoryJpaRepository extends JpaRepository<RoomCategory, UUID> {
    @Query("SELECT r FROM RoomCategory r WHERE r.status = 1 ORDER BY r.name")
    List<RoomCategory> findAllActive();
    @Query("SELECT r FROM RoomCategory r WHERE r.status IN (0, 1) ORDER BY r.status DESC, r.name ASC")
    List<RoomCategory> findAllOrdered();

    Optional<RoomCategory> findByNameIgnoreCase(String name);

    @Query("SELECT r FROM RoomCategory r WHERE r.tenantId = :tenantId AND (:branchId IS NULL AND r.branchId IS NULL OR r.branchId = :branchId) AND LOWER(r.name) = LOWER(:name) AND r.status = 1")
    Optional<RoomCategory> findByTenantIdAndBranchIdAndNameIgnoreCase(
        @Param("tenantId") UUID tenantId,
        @Param("branchId") UUID branchId,
        @Param("name") String name
    );
}
