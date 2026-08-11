package com.hms.api.claims;

import com.hms.api.claims.request.ReconcileRequest;
import com.hms.api.claims.response.ClaimRowResponse;
import com.hms.api.claims.response.DeductionLineResponse;
import com.hms.api.claims.response.PaymentAdviceResponse;
import com.hms.api.shared.ApiResponse;
import com.hms.application.claims.ClaimPaymentService;
import jakarta.validation.Valid;
import com.hms.security.SpringSecurityAuditorAware;
import lombok.RequiredArgsConstructor;
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
 * Claim disbursal tracking and bank reconciliation — Screens 5.2 and 5.3.
 *
 * <p>Guarded by {@code CLAIM_PAYMENTS} rather than {@code NHCX_CLAIMS}.
 * Confirming that money reached the hospital's account is an accounts function,
 * and the person who files claims should not also be the person who certifies
 * that they were paid.
 */
@RestController
@RequestMapping("/insurance/claims")
@RequiredArgsConstructor
@PreAuthorize("hasPermission('CLAIM_PAYMENTS','')")
public class ClaimPaymentController {

    private final ClaimPaymentService service;

    /**
     * Reused rather than reading the SecurityContext directly, so "who acted"
     * is resolved by exactly one rule across the application — the same one
     * that stamps created_by on every audited row.
     */
    private final SpringSecurityAuditorAware auditor;

    /**
     * The control tower — Screen 5.2.
     *
     * <p>One call returns the rows and their advices together. The five metric
     * cards are computed on the client from this payload rather than by a
     * separate aggregate endpoint, so the totals can never disagree with the
     * table printed beneath them.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ClaimRowResponse>>> controlTower() {
        List<ClaimRowResponse> body = service.controlTowerRows().stream()
            .map(t -> ClaimRowResponse.from(t,
                service.advicesFor(t.getId()).stream()
                       .map(PaymentAdviceResponse::from)
                       .toList()))
            .toList();
        return ResponseEntity.ok(ApiResponse.of(body));
    }

    /** Advices awaiting a bank confirmation — the accounts work queue. */
    @GetMapping("/payments/pending")
    public ResponseEntity<ApiResponse<List<PaymentAdviceResponse>>> pending() {
        List<PaymentAdviceResponse> body = service.pendingReconciliation().stream()
            .map(PaymentAdviceResponse::from)
            .toList();
        return ResponseEntity.ok(ApiResponse.of(body));
    }

    /** Every advice recorded against one claim. */
    @GetMapping("/{transactionId}/payments")
    public ResponseEntity<ApiResponse<List<PaymentAdviceResponse>>> advices(
            @PathVariable UUID transactionId) {
        List<PaymentAdviceResponse> body = service.advicesFor(transactionId).stream()
            .map(PaymentAdviceResponse::from)
            .toList();
        return ResponseEntity.ok(ApiResponse.of(body));
    }

    /** Itemised disallowances behind a deduction. */
    @GetMapping("/{transactionId}/deductions")
    public ResponseEntity<ApiResponse<List<DeductionLineResponse>>> deductions(
            @PathVariable UUID transactionId) {
        List<DeductionLineResponse> body = service.deductionsFor(transactionId).stream()
            .map(DeductionLineResponse::from)
            .toList();
        return ResponseEntity.ok(ApiResponse.of(body));
    }

    /**
     * Confirm the bank credit against a payment advice.
     *
     * <p>The acting user is taken from the session, never from the request body:
     * this is the record of who certified the money arrived, and a client-
     * supplied actor would make that record worthless.
     */
    @PostMapping("/payments/{adviceId}/reconcile")
    public ResponseEntity<ApiResponse<PaymentAdviceResponse>> reconcile(
            @PathVariable UUID adviceId,
            @Valid @RequestBody ReconcileRequest req) {

        UUID actor = auditor.getCurrentAuditor().orElse(null);
        var advice = service.reconcile(adviceId, req.bankCreditedPaise(), actor, req.note());
        return ResponseEntity.ok(ApiResponse.ok("Reconciled",
                                                PaymentAdviceResponse.from(advice)));
    }

    /** Challenge a specific disallowed line — Screen 5.2's dispute action. */
    @PostMapping("/deductions/{lineId}/dispute")
    public ResponseEntity<ApiResponse<DeductionLineResponse>> dispute(
            @PathVariable UUID lineId,
            @RequestParam(required = false) String note) {

        return ResponseEntity.ok(ApiResponse.ok("Dispute raised",
            DeductionLineResponse.from(service.disputeLine(lineId, note))));
    }
}
