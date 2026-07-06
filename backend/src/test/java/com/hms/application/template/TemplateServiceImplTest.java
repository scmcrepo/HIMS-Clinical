package com.hms.application.template;

import com.hms.domain.casesheet.model.CaseSheetTemplate;
import com.hms.domain.shared.model.CommonTemplate;
import com.hms.domain.shared.model.DepartmentTemplate;
import com.hms.domain.shared.model.Template;
import com.hms.infrastructure.persistence.department.DepartmentTemplateJpaRepository;
import com.hms.infrastructure.persistence.template.TemplateJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TemplateServiceImplTest {

    @Mock private TemplateJpaRepository templateRepo;
    @Mock private DepartmentTemplateJpaRepository departmentTemplateRepo;

    @InjectMocks
    private TemplateServiceImpl templateService;

    private UUID deptId;

    @BeforeEach
    void setUp() {
        deptId = UUID.randomUUID();
    }

    @Test
    void getTemplatesByType_ShouldReturnList() {
        Template t = new Template();
        when(templateRepo.findByTemplateType(CommonTemplate.CLINICAL)).thenReturn(List.of(t));
        
        List<Template> list = templateService.getTemplatesByType(CommonTemplate.CLINICAL);
        
        assertEquals(1, list.size());
        verify(templateRepo).findByTemplateType(CommonTemplate.CLINICAL);
    }

    @Test
    void getDepartmentTemplateByDepartmentId_ShouldReturnCaseSheetTemplates() {
        DepartmentTemplate dt = new DepartmentTemplate();
        CaseSheetTemplate cst = new CaseSheetTemplate();
        cst.setId(UUID.randomUUID());
        dt.setTemplate(cst);
        
        when(departmentTemplateRepo.findByDepartmentId(deptId)).thenReturn(List.of(dt));
        
        List<CaseSheetTemplate> list = templateService.getDepartmentTemplateByDepartmentId(deptId);
        
        assertEquals(1, list.size());
        assertEquals(cst.getId(), list.get(0).getId());
    }

    @Test
    void removeDepartmentTemplates_ShouldDeleteIfPresent() {
        UUID templateId = UUID.randomUUID();
        DepartmentTemplate dt = new DepartmentTemplate();
        when(departmentTemplateRepo.findByTemplateIdAndDepartmentId(templateId, deptId)).thenReturn(Optional.of(dt));
        
        templateService.removeDepartmentTemplates(templateId, deptId);
        
        verify(departmentTemplateRepo).delete(dt);
    }
}
