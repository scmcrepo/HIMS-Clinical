package com.hms.infrastructure.persistence.goodsreturn;
import com.hms.domain.procurement.model.GoodsReturn;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;
public interface GoodsReturnJpaRepository extends JpaRepository<GoodsReturn, UUID> {
    @Query("SELECT r FROM GoodsReturn r WHERE r.returnDate = :date ORDER BY r.createdAt DESC")
    List<GoodsReturn> findByDate(@Param("date") LocalDate date);
}
