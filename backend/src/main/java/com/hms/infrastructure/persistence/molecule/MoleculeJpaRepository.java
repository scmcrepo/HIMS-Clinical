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

    /** Tenant-scoped lookup — bypasses Hibernate @Filter for reliable bulk-import usage. */
    @Query(value = "SELECT * FROM molecules WHERE tenant_id = :tenantId AND LOWER(name) = LOWER(:name) AND status = 1 LIMIT 1", nativeQuery = true)
    Optional<Molecule> findByTenantIdAndNameIgnoreCase(@Param("tenantId") UUID tenantId, @Param("name") String name);
}

