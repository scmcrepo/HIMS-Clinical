package com.hms.application.template;

import com.hms.domain.shared.model.Template;
import com.hms.domain.shared.model.CommonTemplate;
import com.hms.domain.casesheet.model.CaseSheetTemplate;
import java.util.List;
import java.util.UUID;

public interface TemplateService {
    List<Template> getTemplatesByType(CommonTemplate type);
    List<Template> getTemplateByTypeAndName(CommonTemplate type, String name);
    List<CaseSheetTemplate> getDepartmentTemplateByDepartmentId(UUID id);
    void removeDepartmentTemplates(UUID id, UUID dptId);
}
