package com.hms.api.department;

import com.hms.api.shared.ApiResponse;
import com.hms.application.department.DepartmentService;
import com.hms.domain.shared.model.Category;
import com.hms.domain.shared.model.Department;
import com.hms.domain.shared.model.StockDepartmentAccess;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.department.DepartmentJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController 
@RequestMapping("/department") 
@RequiredArgsConstructor
public class DepartmentController {
    
    private final DepartmentJpaRepository repo;
    private final DepartmentService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Department>>> getAll(
            @RequestParam(name = "type", required=false) String type,
            @RequestParam(required=false, defaultValue="false") boolean includeInactive) {
        return ResponseEntity.ok(ApiResponse.ok("OK",
                includeInactive ? repo.findAllOrdered() : repo.findAllActive()));
    }

    @GetMapping("/types")
    public ResponseEntity<ApiResponse<String[]>> getTypes() {
        return ResponseEntity.ok(ApiResponse.ok("OK", new String[]{"Clinical", "Diagnostics", "Stock", "Other"}));
    }

    @GetMapping("/getDepartmentByName")
    public ResponseEntity<ApiResponse<List<Department>>> getByName(@RequestParam(name = "name", required=false) String name) {
        return ResponseEntity.ok(ApiResponse.ok("OK", name != null ? repo.searchByName(name) : repo.findAllActive()));
    }

    @GetMapping("/current")
    public ResponseEntity<ApiResponse<List<Department>>> getCurrent() {
        return ResponseEntity.ok(ApiResponse.ok("OK", repo.findAllActive()));
    }

    @PostMapping @PreAuthorize("hasPermission('SETTINGS_DEPARTMENT','')")
    public ResponseEntity<ApiResponse<Department>> create(@RequestBody Department req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Department saved successfully", service.createDepartment(req)));
    }

    @PutMapping @PreAuthorize("hasPermission('SETTINGS_DEPARTMENT','')")
    public ResponseEntity<ApiResponse<Department>> update(@RequestBody Department req) {
        if (req.getId() == null) {
            return (ResponseEntity) ResponseEntity.badRequest().body(ApiResponse.error("id required"));
        }
        return ResponseEntity.ok(ApiResponse.ok("Department updated successfully", service.updateDepartment(req)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Department>> getById(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("OK", repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Department", id))));
    }

    @GetMapping("/getDepartmentByUserId/{id}")
    public ResponseEntity<ApiResponse<List<Department>>> getByUser(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("OK", repo.findAllActive()));
    }

    /**
     * GET /department/getDepartmentsAccess/{id} — department access config for a user/department.
     */
    @GetMapping("/getDepartmentsAccess/{id}")
    public ResponseEntity<ApiResponse<List<StockDepartmentAccess>>> getDepartmentsAccess(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("OK", service.getDepartmentsAccess(id)));
    }

    /**
     * GET /department/getDepartmentsCategory/{id} — categories linked to department.
     */
    @GetMapping("/getDepartmentsCategory/{id}")
    public ResponseEntity<ApiResponse<List<Category>>> getDepartmentsCategory(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("OK", service.getDepartmentsCategory(id)));
    }

    @GetMapping("/page")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<Department>>> getPaginated(
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String value) {
        
        List<Department> all = repo.findAllOrdered();
        
        if (value != null && !value.isBlank()) {
            String lowerValue = value.toLowerCase();
            all = all.stream().filter(e -> {
                try {
                    java.lang.reflect.Method m = e.getClass().getMethod("getName");
                    Object res = m.invoke(e);
                    if (res != null && res.toString().toLowerCase().contains(lowerValue)) return true;
                } catch(Exception ex) {}
                return false;
            }).toList();
        }
        
        int total = all.size();
        int startIndex = Math.min(start, total);
        int endIndex = Math.min(start + limit, total);
        List<Department> pageContent = startIndex <= endIndex ? all.subList(startIndex, endIndex) : new java.util.ArrayList<>();
        
        org.springframework.data.domain.Page<Department> page = new org.springframework.data.domain.PageImpl<>(
            pageContent, 
            org.springframework.data.domain.PageRequest.of(start / Math.max(limit, 1), Math.max(limit, 1)), 
            total
        );
        return ResponseEntity.ok(ApiResponse.ok("OK", page));
    }
}
