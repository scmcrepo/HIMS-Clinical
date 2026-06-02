package com.hms.infrastructure.persistence.bed;
import com.hms.domain.bed.model.RoomCategory;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface RoomCategoryJpaRepository extends JpaRepository<RoomCategory, UUID> {
    @Query("SELECT r FROM RoomCategory r WHERE r.status = 1 ORDER BY r.name")
    List<RoomCategory> findAllActive();
    @Query("SELECT r FROM RoomCategory r WHERE r.status IN (0, 1) ORDER BY r.status DESC, r.name ASC")
    List<RoomCategory> findAllOrdered();

    Optional<RoomCategory> findByNameIgnoreCase(String name);
}
