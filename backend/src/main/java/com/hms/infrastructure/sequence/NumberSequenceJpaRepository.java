package com.hms.infrastructure.sequence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface NumberSequenceJpaRepository extends JpaRepository<NumberSequenceEntity, UUID> {
    Optional<NumberSequenceEntity> findByTypeId(UUID typeId);
    
    @org.springframework.data.jpa.repository.Query("SELECT n.id FROM NumberSequenceEntity n WHERE n.value LIKE CONCAT('%',:v,'%')")
    java.util.List<java.util.UUID> findIdsByValue(@org.springframework.data.repository.query.Param("v") String value);
}
