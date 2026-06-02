package com.hms.infrastructure.persistence.diagtemplate;

import com.hms.domain.diagnostic.model.LabTemplateDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface LabTemplateDetailJpaRepository extends JpaRepository<LabTemplateDetail, UUID> {
}
