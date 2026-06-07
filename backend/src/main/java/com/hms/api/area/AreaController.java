package com.hms.api.area;
import org.springframework.security.access.prepost.PreAuthorize;
import com.hms.api.shared.ApiResponse;
import com.hms.infrastructure.persistence.area.AreaEntity;
import com.hms.infrastructure.persistence.area.AreaJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/areas") @RequiredArgsConstructor
@PreAuthorize("hasPermission('SETTINGS_AREA','')")
public class AreaController {
    private final AreaJpaRepository repo;
    @GetMapping
    public ResponseEntity<ApiResponse<List<AreaEntity>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok("OK", repo.findAllActive()));
    }
    @PostMapping
    public ResponseEntity<ApiResponse<AreaEntity>> create(@RequestBody AreaEntity req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Area saved successfully", repo.save(req)));
    }
}
