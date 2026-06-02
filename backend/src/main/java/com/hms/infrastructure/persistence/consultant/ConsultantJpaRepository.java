package com.hms.infrastructure.persistence.consultant;
import com.hms.domain.consultant.model.Consultant;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface ConsultantJpaRepository extends JpaRepository<Consultant, UUID> {
    @Query("SELECT c FROM Consultant c WHERE c.status = 1 ORDER BY c.firstName, c.lastName")
    List<Consultant> findAllActive();
    @Query("SELECT c FROM Consultant c WHERE c.status = 1 AND (LOWER(c.firstName) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(c.lastName) LIKE LOWER(CONCAT('%',:q,'%')))")
    List<Consultant> searchByName(@Param("q") String q);
    
    @Query("SELECT c FROM Consultant c WHERE c.status != com.hms.domain.shared.model.EntityStatus.DELETED ORDER BY c.status DESC, c.firstName, c.lastName")
    List<Consultant> findAllNonDeleted();
    
    @Query("SELECT c FROM Consultant c WHERE c.status != com.hms.domain.shared.model.EntityStatus.DELETED AND (LOWER(c.firstName) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(c.lastName) LIKE LOWER(CONCAT('%',:q,'%')))")
    List<Consultant> searchNonDeletedByName(@Param("q") String q);
}
