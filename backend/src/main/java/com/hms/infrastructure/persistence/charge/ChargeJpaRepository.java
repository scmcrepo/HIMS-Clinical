package com.hms.infrastructure.persistence.charge;
import com.hms.domain.charge.model.Charge;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface ChargeJpaRepository extends JpaRepository<Charge, UUID> {
    @Query("SELECT c FROM Charge c LEFT JOIN FETCH c.tariffs WHERE c.id = :id")
    Optional<Charge> findByIdWithTariffs(@Param("id") UUID id);

    @Query("SELECT c FROM Charge c LEFT JOIN FETCH c.tariffs WHERE c.status = 1 ORDER BY c.name ASC")
    List<Charge> findAllActiveWithTariffs();
    @Query("SELECT c FROM Charge c LEFT JOIN FETCH c.tariffs WHERE c.status = 1 AND LOWER(c.name) LIKE LOWER(CONCAT('%',:q,'%'))")
    List<Charge> searchByName(@Param("q") String q);
    @Query("SELECT c FROM Charge c LEFT JOIN FETCH c.tariffs WHERE c.status = 1 AND c.categoryId = :catId")
    List<Charge> findByCategoryId(@Param("catId") UUID categoryId);
    @Query("SELECT c FROM Charge c LEFT JOIN FETCH c.tariffs WHERE c.id IN :ids AND c.status = 1")
    List<Charge> findAllByIdIn(@Param("ids") List<UUID> ids);

    @Query("SELECT c FROM Charge c WHERE LOWER(c.name) = LOWER(:name) AND c.status = 1")
    List<Charge> findByNameIgnoreCase(@Param("name") String name);

    @Query("SELECT c FROM Charge c LEFT JOIN FETCH c.tariffs WHERE c.status IN (0, 1) ORDER BY c.status DESC, c.name ASC")
    List<Charge> findAllNotDeletedOrdered();

    @Query("SELECT c FROM Charge c LEFT JOIN FETCH c.tariffs WHERE c.status IN (0, 1) AND LOWER(c.name) LIKE LOWER(CONCAT('%',:q,'%')) ORDER BY c.status DESC, c.name ASC")
    List<Charge> searchAllNotDeletedOrdered(@Param("q") String q);
}
