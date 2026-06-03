package com.hms.application.template;

import com.hms.domain.shared.model.Template;
import com.hms.domain.shared.model.CommonTemplate;
import com.hms.domain.shared.model.DepartmentTemplate;
import com.hms.domain.casesheet.model.CaseSheetTemplate;
import com.hms.infrastructure.persistence.template.TemplateJpaRepository;
import com.hms.infrastructure.persistence.department.DepartmentTemplateJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TemplateServiceImpl implements TemplateService {

    private final TemplateJpaRepository templateRepo;
    private final DepartmentTemplateJpaRepository departmentTemplateRepo;

    @Override
    @Transactional(readOnly = true)
    public List<Template> getTemplatesByType(CommonTemplate type) {
        return templateRepo.findByTemplateType(type);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Template> getTemplateByTypeAndName(CommonTemplate type, String name) {
        return templateRepo.findByNameAndType(name, type);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CaseSheetTemplate> getDepartmentTemplateByDepartmentId(UUID id) {
        return departmentTemplateRepo.findByDepartmentId(id).stream()
                .map(DepartmentTemplate::getTemplate)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void removeDepartmentTemplates(UUID id, UUID dptId) {
        departmentTemplateRepo.findByTemplateIdAndDepartmentId(id, dptId)
                .ifPresent(departmentTemplateRepo::delete);
    }
}
