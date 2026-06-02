package com.hms.infrastructure.persistence.bed;

import com.hms.domain.bed.model.Bed;
import com.hms.domain.bed.model.BedStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BedJpaRepository extends JpaRepository<Bed, UUID> {

    Optional<Bed> findByName(String name);


    /** Pessimistic write lock — prevents concurrent allocation of the same bed. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Bed b WHERE b.id = :id AND b.status = 1")
    Optional<Bed> findActiveByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT b FROM Bed b WHERE b.bedStatus = :status AND b.status = 1 ORDER BY b.name")
    List<Bed> findByBedStatus(@Param("status") BedStatus status);

    @Query("SELECT b FROM Bed b WHERE b.roomCategoryId = :catId AND b.status = 1 ORDER BY b.name")
    List<Bed> findByRoomCategoryId(@Param("catId") UUID roomCategoryId);

    @Query("SELECT b FROM Bed b WHERE b.bedStatus = com.hms.domain.bed.model.BedStatus.AVAILABLE AND b.roomCategoryId = :catId AND b.status = 1")
    List<Bed> findAvailableByCategory(@Param("catId") UUID roomCategoryId);
}
