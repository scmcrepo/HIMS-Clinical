package com.hms.api.compliance;

import com.hms.api.compliance.request.RightsRequests;
import com.hms.api.compliance.response.RightsResponses;
import com.hms.api.shared.ApiResponse;
import com.hms.application.compliance.DataPrincipalRightsService;
import com.hms.infrastructure.persistence.compliance.ErasureRequestEntity;
import com.hms.infrastructure.persistence.compliance.ErasureTargetEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Data principal rights: erasure and correction.
 *
 * <p>The first endpoint in {@code api/compliance}, which was an empty directory
 * while V179 seeded {@code ERASURE_MANAGE} and {@code TenantService} provisioned
 * it — a permission guarding nothing.
 *
 * <p>Three permissions, because three different things are happening.
 * {@code ERASURE_REQUEST} takes the request and is held by reception, since a
 * patient asking to be forgotten should not have to find an administrator.
 * {@code ERASURE_MANAGE} verifies identity, runs the sweep and refuses requests,
 * and is deliberately narrower. Reading the queue needs
 * {@code ERASURE_REQUEST} — you cannot process what you cannot see.
 *
 * <p>Deliberately not here: a bulk erasure endpoint. Erasure is irreversible for
 * the DELETE targets, and an API that erases a list is one malformed request
 * away from erasing a hospital.
 */
@RestController
@RequestMapping("/compliance/rights")
@RequiredArgsConstructor
public class DataPrincipalRightsController {

    private final DataPrincipalRightsService service;

    /**
     * Raise an erasure or correction request.
     *
     * <p>Idempotent per patient and type: asking twice returns the existing open
     * request rather than opening a second, so a patient who taps twice does not
     * start two sweeps racing over the same rows.
     */
    @PostMapping
    @PreAuthorize("hasPermission('ERASURE_REQUEST','')")
    public ResponseEntity<ApiResponse<RightsResponses.RightsRequest>> raise(
            @Valid @RequestBody RightsRequests.Raise body) {

        ErasureRequestEntity request = service.raise(
            body.patientId(), body.requestType(), body.requestedVia(),
            body.requestedByPatient(), body.correctionPayload());

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
            "Request recorded. It must be verified before it can be actioned.",
            RightsResponses.RightsRequest.from(request)));
    }

    /**
     * Record that the requester was proved to be the patient.
     *
     * <p>Separate from raising the request, and separately permissioned, because
     * this is the step that unlocks irreversible deletion. Merging the two would
     * mean whoever can take a phone call can erase a patient.
     */
    @PostMapping("/{id}/verify")
    @PreAuthorize("hasPermission('ERASURE_MANAGE','')")
    public ResponseEntity<ApiResponse<RightsResponses.RightsRequest>> verify(
            @PathVariable UUID id,
            @Valid @RequestBody RightsRequests.Verify body) {

        ErasureRequestEntity request = service.verifyRequester(id, body.method());
        return ResponseEntity.ok(ApiResponse.ok(
            "Requester verified", RightsResponses.RightsRequest.from(request)));
    }

    /**
     * Run the sweep.
     *
     * <p>Returns a per-store receipt rather than a bare acknowledgement: the
     * patient is entitled to know what was erased, what was anonymised, and what
     * was kept under a retention obligation. A refusal to erase is only lawful
     * if the patient is told it happened and why.
     */
    @PostMapping("/{id}/execute")
    @PreAuthorize("hasPermission('ERASURE_MANAGE','')")
    public ResponseEntity<ApiResponse<RightsResponses.ErasureReceipt>> execute(
            @PathVariable UUID id) {

        List<ErasureTargetEntity> targets = service.execute(id);
        ErasureRequestEntity request = service.get(id);

        return ResponseEntity.ok(ApiResponse.ok(
            "Erasure processed", RightsResponses.ErasureReceipt.of(request, targets)));
    }

    /** Refuse a request. The reason is mandatory and is shown to the patient. */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasPermission('ERASURE_MANAGE','')")
    public ResponseEntity<ApiResponse<RightsResponses.RightsRequest>> reject(
            @PathVariable UUID id,
            @Valid @RequestBody RightsRequests.Reject body) {

        ErasureRequestEntity request = service.reject(id, body.reason());
        return ResponseEntity.ok(ApiResponse.ok(
            "Request refused", RightsResponses.RightsRequest.from(request)));
    }

    /** The receipt for a completed request, re-readable at any time. */
    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('ERASURE_REQUEST','')")
    public ResponseEntity<ApiResponse<RightsResponses.ErasureReceipt>> get(
            @PathVariable UUID id) {

        ErasureRequestEntity request = service.get(id);
        return ResponseEntity.ok(ApiResponse.of(
            RightsResponses.ErasureReceipt.of(request, service.targetsFor(id))));
    }

    /**
     * The work queue.
     *
     * <p>{@code overdue} on each row is computed against the statutory deadline,
     * so a request running past its clock is visible in the list rather than only
     * in a log nobody reads.
     */
    @GetMapping
    @PreAuthorize("hasPermission('ERASURE_REQUEST','')")
    public ResponseEntity<ApiResponse<List<RightsResponses.RightsRequest>>> queue(
            @RequestParam(required = false) String state) {

        List<RightsResponses.RightsRequest> rows = service.queue(state).stream()
            .map(RightsResponses.RightsRequest::from)
            .toList();
        return ResponseEntity.ok(ApiResponse.of(rows));
    }

    /** Every request this patient has ever raised. */
    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasPermission('ERASURE_REQUEST','')")
    public ResponseEntity<ApiResponse<List<RightsResponses.RightsRequest>>> history(
            @PathVariable UUID patientId) {

        List<RightsResponses.RightsRequest> rows = service.historyFor(patientId).stream()
            .map(RightsResponses.RightsRequest::from)
            .toList();
        return ResponseEntity.ok(ApiResponse.of(rows));
    }
}
