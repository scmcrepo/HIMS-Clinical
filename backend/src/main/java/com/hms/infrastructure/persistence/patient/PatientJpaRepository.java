package com.hms.infrastructure.persistence.patient;
import com.hms.domain.patient.model.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;
public interface PatientJpaRepository extends JpaRepository<Patient, UUID> {
    @Query("""
        SELECT p FROM Patient p 
        WHERE p.status = com.hms.domain.shared.model.EntityStatus.ACTIVE AND (
            LOWER(p.firstName) LIKE LOWER(CONCAT('%',:q,'%')) OR
            LOWER(p.lastName)  LIKE LOWER(CONCAT('%',:q,'%')) OR
            p.contactNumber LIKE CONCAT('%',:q,'%') OR
            EXISTS (SELECT 1 FROM NumberSequenceEntity ns WHERE ns.id = p.id AND LOWER(ns.value) LIKE LOWER(CONCAT('%',:q,'%')))
        )
        """)
    Page<Patient> searchByNameOrContact(@Param("q") String query, Pageable pageable);
    
    @Query("""
        SELECT p.id FROM Patient p 
        WHERE p.status = com.hms.domain.shared.model.EntityStatus.ACTIVE AND (
            LOWER(p.firstName) LIKE LOWER(CONCAT('%',:q,'%')) OR 
            LOWER(p.lastName) LIKE LOWER(CONCAT('%',:q,'%')) OR 
            p.contactNumber LIKE CONCAT('%',:q,'%') OR
            EXISTS (SELECT 1 FROM NumberSequenceEntity ns WHERE ns.id = p.id AND LOWER(ns.value) LIKE LOWER(CONCAT('%',:q,'%')))
        )
        """)
    java.util.List<UUID> searchIdsByNameOrContact(@Param("q") String query);

    Optional<Patient> findByContactNumberAndStatus(String contactNumber, byte status);
}
