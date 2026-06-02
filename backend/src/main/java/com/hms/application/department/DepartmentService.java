package com.hms.application.department;

import com.hms.domain.shared.model.Department;
import com.hms.domain.shared.model.Category;
import com.hms.domain.shared.model.StockDepartmentAccess;
import java.util.List;
import java.util.UUID;

public interface DepartmentService {
    Department createDepartment(Department department);
    Department updateDepartment(Department department);
    List<Category> getDepartmentsCategory(UUID id);
    List<StockDepartmentAccess> getDepartmentsAccess(UUID id);
}
