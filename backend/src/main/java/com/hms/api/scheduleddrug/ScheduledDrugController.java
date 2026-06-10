package com.hms.api.scheduleddrug;

import org.springframework.security.access.prepost.PreAuthorize;
import com.hms.api.shared.ApiResponse;
import com.hms.domain.shared.model.EntityStatus;
import com.hms.domain.shared.model.ScheduledDrug;
import com.hms.infrastructure.persistence.scheduleddrug.ScheduledDrugJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * ScheduledDrugController — scheduled drug master.
 * GET    /scheduled-drug           — all active scheduled drugs
 * GET    /scheduled-drug/page      — paginated with search
 * POST   /scheduled-drug           — create
 * PUT    /scheduled-drug           — update
 * DELETE /scheduled-drug/{id}      — soft-delete
 */
@RestController
@RequestMapping("/scheduled-drug")
@RequiredArgsConstructor
@PreAuthorize("hasPermission('SETTINGS_SCHEDULEDDRUG','')")
public class ScheduledDrugController {

    private final ScheduledDrugJpaRepository repo;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ScheduledDrug>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok("OK", repo.findAllActive()));
    }

    @GetMapping("/page")
    public ResponseEntity<ApiResponse<Page<ScheduledDrug>>> getPaginated(
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String value) {
        List<ScheduledDrug> all = repo.findAllExcludeDeleted();
        if (value != null && !value.isBlank()) {
            String q = value.toLowerCase();
            all = all.stream()
                .filter(sd -> sd.getName() != null && sd.getName().toLowerCase().contains(q))
                .toList();
        }
        int total = all.size();
        int pageNum = limit > 0 ? start / limit : 0;
        List<ScheduledDrug> pageItems = all.stream().skip(start).limit(limit).toList();
        return ResponseEntity.ok(ApiResponse.ok("OK",
            new PageImpl<>(pageItems, PageRequest.of(pageNum, Math.max(limit, 1)), total)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ScheduledDrug>> create(@RequestBody ScheduledDrug req) {
        if (req.getStatus() == null) req.setStatus(EntityStatus.ACTIVE);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Scheduled drug saved", repo.save(req)));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<ScheduledDrug>> update(@RequestBody ScheduledDrug req) {
        return ResponseEntity.ok(ApiResponse.ok("Scheduled drug updated", repo.save(req)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        repo.findById(id).ifPresent(sd -> { sd.setStatus(EntityStatus.DELETED); repo.save(sd); });
        return ResponseEntity.ok(ApiResponse.ok("Scheduled drug deleted", null));
    }
}
