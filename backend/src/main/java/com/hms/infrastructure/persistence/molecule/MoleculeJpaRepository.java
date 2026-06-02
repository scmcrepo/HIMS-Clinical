package com.hms.infrastructure.persistence.molecule;
import com.hms.domain.inventory.model.Molecule;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface MoleculeJpaRepository extends JpaRepository<Molecule, UUID> {
    @Query("SELECT m FROM Molecule m WHERE m.status = 1 AND LOWER(m.name) LIKE LOWER(CONCAT('%',:q,'%')) ORDER BY m.name")
    List<Molecule> searchByName(@Param("q") String q);
    @Query("SELECT m FROM Molecule m WHERE m.status = 1 AND LOWER(m.name) LIKE LOWER(CONCAT('%',:q,'%')) ORDER BY m.name")
    Page<Molecule> searchByNamePaged(@Param("q") String q, Pageable p);
    @Query("SELECT m FROM Molecule m WHERE m.status = 1 ORDER BY m.name")
    Page<Molecule> findAllActivePaged(Pageable p);
}
