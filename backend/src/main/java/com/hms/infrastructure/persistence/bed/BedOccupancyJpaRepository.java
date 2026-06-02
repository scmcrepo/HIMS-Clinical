package com.hms.infrastructure.persistence.bed;

import com.hms.domain.bed.model.BedOccupancy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BedOccupancyJpaRepository extends JpaRepository<BedOccupancy, UUID> {

    @Query("SELECT o FROM BedOccupancy o WHERE o.encounterId = :eid AND o.status = 1")
    Optional<BedOccupancy> findActiveByEncounterId(@Param("eid") UUID encounterId);

    @Query("SELECT o FROM BedOccupancy o WHERE o.bedId = :bedId AND o.status = 1")
    Optional<BedOccupancy> findActiveByBedId(@Param("bedId") UUID bedId);

    @Query("SELECT o FROM BedOccupancy o WHERE o.encounterId = :eid ORDER BY o.fromDatetime ASC")
    List<BedOccupancy> findAllByEncounterId(@Param("eid") UUID encounterId);

    @Query("SELECT o FROM BedOccupancy o ORDER BY o.fromDatetime ASC")
    org.springframework.data.domain.Page<BedOccupancy> findOldest(
        org.springframework.data.domain.Pageable pageable);

    default java.util.Optional<BedOccupancy> findOldestAllocation() {
        var page = findOldest(org.springframework.data.domain.PageRequest.of(0, 1));
        return page.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(page.getContent().get(0));
    }
}
