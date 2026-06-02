package com.hms.infrastructure.persistence.category;
import com.hms.domain.shared.model.Category;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface CategoryJpaRepository extends JpaRepository<Category, UUID> {
    @Query("SELECT c FROM Category c WHERE c.status != com.hms.domain.shared.model.EntityStatus.DELETED ORDER BY c.name") List<Category> findAllActive();
    @Query("SELECT c FROM Category c WHERE c.status != com.hms.domain.shared.model.EntityStatus.DELETED AND c.categoryType = :type ORDER BY c.name") List<Category> findByType(@Param("type") String type);
    @Query("SELECT c FROM Category c WHERE c.status != com.hms.domain.shared.model.EntityStatus.DELETED AND LOWER(c.name) LIKE LOWER(CONCAT('%',:q,'%'))") List<Category> searchByName(@Param("q") String q);
}
