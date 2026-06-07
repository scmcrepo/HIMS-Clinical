package com.hms.api.category;
import org.springframework.security.access.prepost.PreAuthorize;
import com.hms.api.shared.ApiResponse;
import com.hms.domain.shared.model.Category;
import com.hms.domain.shared.model.CategoryType;
import com.hms.infrastructure.persistence.category.CategoryJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/category") @RequiredArgsConstructor
@PreAuthorize("hasPermission('SETTINGS_CATEGORY','')")
public class CategoryController {
    private final CategoryJpaRepository repo;
    @GetMapping
    public ResponseEntity<ApiResponse<List<Category>>> getAll(@RequestParam(name = "type", required=false) String type) {
        return ResponseEntity.ok(ApiResponse.ok("OK", type != null ? repo.findByType(type) : repo.findAllActive()));
    }
    @GetMapping("/types")
    public ResponseEntity<ApiResponse<CategoryType[]>> getTypes() {
        return ResponseEntity.ok(ApiResponse.ok("OK", CategoryType.values()));
    }
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<Category>>> search(@RequestParam(name = "q") String q) {
        return ResponseEntity.ok(ApiResponse.ok("OK", repo.searchByName(q)));
    }
    @PostMapping
    public ResponseEntity<ApiResponse<Category>> create(@RequestBody Category req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Category created successfully", repo.save(req)));
    }
    @PutMapping
    public ResponseEntity<ApiResponse<Category>> update(@RequestBody Category req) {
        if (req.getId() == null) return (ResponseEntity) ResponseEntity.badRequest().body(ApiResponse.error("id required"));
        return ResponseEntity.ok(ApiResponse.ok("Category updated successfully", repo.save(req)));
    }

    @GetMapping("/page")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<Category>>> getPaginated(
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String value) {
        
        List<Category> all = repo.findAllActive();
        
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
        List<Category> pageContent = startIndex <= endIndex ? all.subList(startIndex, endIndex) : new java.util.ArrayList<>();
        
        org.springframework.data.domain.Page<Category> page = new org.springframework.data.domain.PageImpl<>(
            pageContent, 
            org.springframework.data.domain.PageRequest.of(start / Math.max(limit, 1), Math.max(limit, 1)), 
            total
        );
        return ResponseEntity.ok(ApiResponse.ok("OK", page));
    }

    
}
