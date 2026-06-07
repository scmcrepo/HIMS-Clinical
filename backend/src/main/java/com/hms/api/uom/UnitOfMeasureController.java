package com.hms.api.uom;
import org.springframework.security.access.prepost.PreAuthorize;

import com.hms.api.shared.ApiResponse;
import com.hms.domain.inventory.model.UnitOfMeasure;
import com.hms.infrastructure.persistence.inventory.UnitOfMeasureJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/uom")
@RequiredArgsConstructor
@PreAuthorize("hasPermission('SETTINGS_UOM','')")
public class UnitOfMeasureController {

    private final UnitOfMeasureJpaRepository uomRepo;

    @GetMapping
    public ResponseEntity<ApiResponse<List<UnitOfMeasure>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok("OK", uomRepo.findAllActive()));
    }
}
