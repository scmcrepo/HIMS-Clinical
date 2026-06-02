package com.hms.infrastructure.persistence.accountunit;
import com.hms.domain.shared.model.AccountUnit;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface AccountUnitJpaRepository extends JpaRepository<AccountUnit, UUID> {
    @Query("SELECT a FROM AccountUnit a WHERE a.status = 1 ORDER BY a.name") List<AccountUnit> findAllActive();
}
