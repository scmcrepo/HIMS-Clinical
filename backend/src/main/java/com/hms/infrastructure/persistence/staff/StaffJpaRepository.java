package com.hms.infrastructure.persistence.staff;
import com.hms.domain.shared.model.Staff;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface StaffJpaRepository extends JpaRepository<Staff, UUID> {
    @Query("SELECT s FROM Staff s WHERE s.status = 1 ORDER BY s.name") List<Staff> findAllActive();
    @Query("SELECT s FROM Staff s WHERE s.status = 1 AND s.staffType = :t") List<Staff> findByType(@Param("t") String type);
    @Query("SELECT s FROM Staff s ORDER BY s.status DESC, s.name ASC") List<Staff> findAllOrdered();
    boolean existsByContactTokenAndStatusNot(String contactToken, com.hms.domain.shared.model.EntityStatus status);
    boolean existsByContactTokenAndStatusNotAndIdNot(String contactToken, com.hms.domain.shared.model.EntityStatus status, UUID id);

    @Query("SELECT COUNT(s) > 0 FROM Staff s WHERE s.contactToken = :contactToken AND s.branchId = :branchId AND s.status != :status")
    boolean existsByContactTokenAndBranchIdAndStatusNot(
        @Param("contactToken") String contactToken,
        @Param("branchId") UUID branchId,
        @Param("status") com.hms.domain.shared.model.EntityStatus status);

    @Query("SELECT COUNT(s) > 0 FROM Staff s WHERE s.contactToken = :contactToken AND s.branchId = :branchId AND s.status != :status AND s.id != :id")
    boolean existsByContactTokenAndBranchIdAndStatusNotAndIdNot(
        @Param("contactToken") String contactToken,
        @Param("branchId") UUID branchId,
        @Param("status") com.hms.domain.shared.model.EntityStatus status,
        @Param("id") UUID id);
}
