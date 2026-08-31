package com.hms.api.retention;

import com.hms.api.retention.request.RetentionRequests;
import com.hms.api.shared.ApiResponse;
import com.hms.application.retention.RetentionService;
import com.hms.infrastructure.persistence.retention.RetentionPolicyEntity;
import com.hms.infrastructure.persistence.retention.RetentionRunEntity;
import com.hms.infrastructure.persistence.retention.RetentionRunItemEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Retention policy administration — DPDP s. 8(7), WO-025.
 *
 * <p>One permission, {@code RETENTION_MANAGE}, granted narrowly. Changing a
 * retention period changes when patient records are destroyed, which is closer
 * to a legal act than an administrative one.
 *
 * <p>There is deliberately no endpoint that runs a policy live on demand. The
 * only manual trigger is {@link #preview()}, which forces dry-run regardless of
 * how the policies are configured. Destruction happens on a schedule, after
 * someone has read a preview and armed the policy — not because a button was
 * available on a bad afternoon.
 */
@RestController
@RequestMapping("/compliance/retention")
@RequiredArgsConstructor
public class RetentionController {

    private final RetentionService service;

    /** Every policy, with its current enabled and dry-run state. */
    @GetMapping("/policies")
    @PreAuthorize("hasPermission('RETENTION_MANAGE','')")
    public ResponseEntity<ApiResponse<List<RetentionPolicyEntity>>> policies() {
        return ResponseEntity.ok(ApiResponse.of(service.allPolicies()));
    }

    /**
     * Change a policy.
     *
     * <p>Sending {@code dryRun: false} arms it. The service refuses if the policy
     * does not validate against the live schema, because enabling a policy with a
     * misnamed column is how a typo becomes data loss.
     */
    @PutMapping("/policies/{id}")
    @PreAuthorize("hasPermission('RETENTION_MANAGE','')")
    public ResponseEntity<ApiResponse<RetentionPolicyEntity>> update(
            @PathVariable UUID id,
            @Valid @RequestBody RetentionRequests.Update body) {

        RetentionPolicyEntity updated = service.update(
            id, body.retentionDays(), body.enabled(), body.dryRun(),
            body.maxRowsPerRun(), body.justification());

        String message = Boolean.FALSE.equals(body.dryRun())
            ? "Policy armed. It will delete or anonymise matching records on the next run."
            : "Policy updated";
        return ResponseEntity.ok(ApiResponse.ok(message, updated));
    }

    /**
     * Show what the enabled policies would affect, changing nothing.
     *
     * <p>Forces dry-run regardless of each policy's own setting, so this is safe
     * to call at any time — including against policies that are armed. It is the
     * intended way to answer "what happens if I turn this on?" without finding
     * out the hard way.
     */
    @PostMapping("/preview")
    @PreAuthorize("hasPermission('RETENTION_MANAGE','')")
    public ResponseEntity<ApiResponse<RetentionRunEntity>> preview() {
        return ResponseEntity.ok(ApiResponse.ok(
            "Preview complete. No data was changed.", service.execute(Boolean.TRUE)));
    }

    /** Recent runs — the record that a deletion was policy rather than an incident. */
    @GetMapping("/runs")
    @PreAuthorize("hasPermission('RETENTION_MANAGE','')")
    public ResponseEntity<ApiResponse<List<RetentionRunEntity>>> runs() {
        return ResponseEntity.ok(ApiResponse.of(service.recentRuns()));
    }

    @GetMapping("/runs/{runId}")
    @PreAuthorize("hasPermission('RETENTION_MANAGE','')")
    public ResponseEntity<ApiResponse<List<RetentionRunItemEntity>>> runDetail(
            @PathVariable UUID runId) {
        return ResponseEntity.ok(ApiResponse.of(service.runDetail(runId)));
    }
}
