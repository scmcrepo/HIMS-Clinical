package com.hms.api.policy;

import com.hms.api.policy.request.ConfirmDiscoveryRequest;
import com.hms.api.policy.request.DiscoveryOtpRequest;
import com.hms.api.policy.response.DiscoveredPolicyResponse;
import com.hms.api.policy.response.PolicyCoverageResponse;
import com.hms.api.shared.ApiResponse;
import com.hms.application.policy.PolicyDiscoveryService;
import jakarta.validation.Valid;
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
import java.util.Map;
import java.util.UUID;

/**
 * Policy discovery and coverage verification — Screens 1.2 and 2.1.
 *
 * <p>Both discovery endpoints return a correlation id rather than an answer.
 * NHCX is asynchronous: the registry acknowledges, and the policies or benefit
 * values arrive later on the callback. A client that expects results inline will
 * work against a sandbox and hang in production, so the contract is explicit
 * about it.
 */
@RestController
@RequestMapping("/policy")
@RequiredArgsConstructor
@PreAuthorize("hasPermission('POLICY_DISCOVERY','')")
public class PolicyDiscoveryController {

    private final PolicyDiscoveryService service;

    /** Step 1 — OTP to the patient authorising the lookup. */
    @PostMapping("/discovery/otp")
    public ResponseEntity<ApiResponse<Map<String, String>>> requestOtp(
            @Valid @RequestBody DiscoveryOtpRequest req) {

        String correlationId = service.requestDiscoveryOtp(
            req.patientId(), req.identifier(), req.consent());
        return ResponseEntity.accepted()
            .body(ApiResponse.ok("OTP sent to the patient",
                                 Map.of("correlationId", correlationId)));
    }

    /** Step 2 — confirm the OTP. Results arrive on the NHCX callback. */
    @PostMapping("/discovery/confirm")
    public ResponseEntity<ApiResponse<Map<String, String>>> confirm(
            @Valid @RequestBody ConfirmDiscoveryRequest req) {

        String correlationId =
            service.confirmDiscovery(req.patientId(), req.correlationId(), req.otp());
        return ResponseEntity.accepted()
            .body(ApiResponse.ok("Discovery authorised; awaiting registry response",
                                 Map.of("correlationId", correlationId)));
    }

    /** Policies discovered for a patient, newest first. */
    @GetMapping("/discovery/patient/{patientId}")
    public ResponseEntity<ApiResponse<List<DiscoveredPolicyResponse>>> discovered(
            @PathVariable UUID patientId) {

        List<DiscoveredPolicyResponse> body = service.discoveredFor(patientId).stream()
            .map(DiscoveredPolicyResponse::from)
            .toList();
        return ResponseEntity.ok(ApiResponse.of(body));
    }

    /** Accept a discovered policy against an insurance record — Screen 1.2's link action. */
    @PostMapping("/discovery/{discoveredId}/link")
    public ResponseEntity<ApiResponse<DiscoveredPolicyResponse>> link(
            @PathVariable UUID discoveredId,
            @RequestParam UUID insuranceId) {

        return ResponseEntity.ok(ApiResponse.ok("Policy linked",
            DiscoveredPolicyResponse.from(service.linkToInsurance(discoveredId, insuranceId))));
    }

    /**
     * The latest coverage snapshot for a patient — Screen 2.1.
     *
     * <p>204 when no check has been run yet, rather than 404: "not checked" is an
     * ordinary state and the banner should render an empty prompt, not an error.
     */
    @GetMapping("/coverage/patient/{patientId}/latest")
    public ResponseEntity<ApiResponse<PolicyCoverageResponse>> latestCoverage(
            @PathVariable UUID patientId) {

        var coverage = service.latestCoverageFor(patientId);
        if (coverage == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(ApiResponse.of(PolicyCoverageResponse.from(
            coverage, service.exclusionsFor(coverage.getId()))));
    }

    /** Every check for a patient. The audit trail behind an admission decision. */
    @GetMapping("/coverage/patient/{patientId}")
    public ResponseEntity<ApiResponse<List<PolicyCoverageResponse>>> coverageHistory(
            @PathVariable UUID patientId) {

        List<PolicyCoverageResponse> body = service.coverageHistoryFor(patientId).stream()
            .map(c -> PolicyCoverageResponse.from(c, service.exclusionsFor(c.getId())))
            .toList();
        return ResponseEntity.ok(ApiResponse.of(body));
    }
}
