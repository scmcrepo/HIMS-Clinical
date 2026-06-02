package com.hms.application.department;

import com.hms.domain.shared.model.*;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.department.DepartmentJpaRepository;
import com.hms.infrastructure.persistence.department.DepartmentCategoriesJpaRepository;
import com.hms.infrastructure.persistence.department.DepartmentTemplateJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentJpaRepository departmentRepo;
    private final DepartmentCategoriesJpaRepository departmentCategoriesRepo;
    private final DepartmentTemplateJpaRepository departmentTemplateRepo;

    @Override
    @Transactional
    public Department createDepartment(Department department) {
        Set<DepartmentTemplate> departmentTemplates = department.getDepartmentTemplates();
        Set<DepartmentCategories> departmentCategories = department.getDepartmentCategories();
        
        // Temporarily clear to save department first without transient dependencies
        department.setDepartmentCategories(new HashSet<>());
        department.setDepartmentTemplates(new HashSet<>());
        
        Department savedDept = departmentRepo.save(department);
        
        if (savedDept.getType() == DepartmentType.Clinical && departmentTemplates != null) {
            for (DepartmentTemplate dt : departmentTemplates) {
                dt.setDepartment(savedDept);
                departmentTemplateRepo.save(dt);
            }
        } else if (savedDept.getType() == DepartmentType.Stock && departmentCategories != null) {
            for (DepartmentCategories dc : departmentCategories) {
                dc.setDepartment(savedDept);
                departmentCategoriesRepo.save(dc);
            }
        }
        
        return savedDept;
    }

    @Override
    @Transactional
    public Department updateDepartment(Department department) {
        Department existing = departmentRepo.findById(department.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Department", department.getId()));
        
        if (existing.getType() != department.getType()) {
            throw new BusinessRuleViolationException(existing.getType() + " department can't be change into " + department.getType());
        }

        existing.setName(department.getName());
        existing.setDisplayOrder(department.getDisplayOrder());
        existing.setStatus(department.getStatus());
        existing.setStockAccess(department.getStockAccess());
        
        // Handle StockDepartmentAccesses
        existing.getStockDepartmentAccesses().clear();
        if (department.getStockDepartmentAccesses() != null) {
            existing.getStockDepartmentAccesses().addAll(department.getStockDepartmentAccesses());
        }

        if (department.getType() == DepartmentType.Clinical) {
            // Save clinical updates
            departmentRepo.save(existing);
            
            // Check existing templates
            List<DepartmentTemplate> currentTemplates = departmentTemplateRepo.findByDepartmentId(existing.getId());
            Set<UUID> currentTemplateIds = currentTemplates.stream()
                    .map(dt -> dt.getTemplate().getId())
                    .collect(Collectors.toSet());

            if (department.getDepartmentTemplates() != null) {
                for (DepartmentTemplate dt : department.getDepartmentTemplates()) {
                    if (dt.getTemplate() != null && !currentTemplateIds.contains(dt.getTemplate().getId())) {
                        dt.setDepartment(existing);
                        departmentTemplateRepo.save(dt);
                    }
                }
            }
        } else if (department.getType() == DepartmentType.Stock) {
            // Delete all and save new
            departmentRepo.save(existing);
            departmentCategoriesRepo.deleteByDepartmentId(existing.getId());
            
            if (department.getDepartmentCategories() != null) {
                for (DepartmentCategories dc : department.getDepartmentCategories()) {
                    dc.setDepartment(existing);
                    departmentCategoriesRepo.save(dc);
                }
            }
        } else {
            departmentRepo.save(existing);
        }

        return existing;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> getDepartmentsCategory(UUID id) {
        return departmentCategoriesRepo.findByDepartmentId(id).stream()
                .map(DepartmentCategories::getCategory)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockDepartmentAccess> getDepartmentsAccess(UUID id) {
        return departmentRepo.findById(id)
                .map(d -> new ArrayList<>(d.getStockDepartmentAccesses()))
                .orElse(new ArrayList<>());
    }
}
