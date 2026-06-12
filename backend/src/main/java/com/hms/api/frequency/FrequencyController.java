package com.hms.api.frequency;
import org.springframework.security.access.prepost.PreAuthorize;

import com.hms.api.shared.ApiResponse;
import com.hms.domain.shared.model.EntityStatus;
import com.hms.domain.shared.model.Frequency;
import com.hms.infrastructure.persistence.frequency.FrequencyJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * FrequencyController — medication dosing frequency master.
 * GET    /frequency           — all active frequencies
 * GET    /frequency/page      — paginated with search
 * POST   /frequency           — create
 * PUT    /frequency           — update
 * DELETE /frequency/{id}      — soft-delete
 */
@RestController
@RequestMapping("/frequency")
@RequiredArgsConstructor
public class FrequencyController {

    private final FrequencyJpaRepository repo;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Frequency>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok("OK", repo.findAllActive()));
    }

    @GetMapping("/page")
    public ResponseEntity<ApiResponse<Page<Frequency>>> getPaginated(
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String value) {
        List<Frequency> all = repo.findAllActive();
        if (value != null && !value.isBlank()) {
            String q = value.toLowerCase();
            all = all.stream()
                .filter(f -> f.getName() != null && f.getName().toLowerCase().contains(q))
                .toList();
        }
        int total = all.size();
        int pageNum = limit > 0 ? start / limit : 0;
        List<Frequency> pageItems = all.stream().skip(start).limit(limit).toList();
        return ResponseEntity.ok(ApiResponse.ok("OK",
            new PageImpl<>(pageItems, PageRequest.of(pageNum, Math.max(limit, 1)), total)));
    }

    @PostMapping
    @PreAuthorize("hasPermission('SETTINGS_FREQUENCY','')")
    public ResponseEntity<ApiResponse<Frequency>> create(@RequestBody Frequency req) {
        if (req.getStatus() == null) req.setStatus(EntityStatus.ACTIVE);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Frequency saved", repo.save(req)));
    }

    @PutMapping
    @PreAuthorize("hasPermission('SETTINGS_FREQUENCY','')")
    public ResponseEntity<ApiResponse<Frequency>> update(@RequestBody Frequency req) {
        return ResponseEntity.ok(ApiResponse.ok("Frequency updated", repo.save(req)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('SETTINGS_FREQUENCY','')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        repo.findById(id).ifPresent(f -> { f.setStatus(EntityStatus.DELETED); repo.save(f); });
        return ResponseEntity.ok(ApiResponse.ok("Frequency deleted", null));
    }
}
