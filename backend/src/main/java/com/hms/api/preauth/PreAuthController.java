package com.hms.api.preauth;

import com.hms.api.preauth.request.EnhancementCmd;
import com.hms.api.preauth.request.QueryResponseCmd;
import com.hms.api.preauth.request.SubmitPreAuthCmd;
import com.hms.api.preauth.response.EnhancementResponse;
import com.hms.api.preauth.response.EstimateLineResponse;
import com.hms.api.preauth.response.PreAuthQueryResponse;
import com.hms.api.shared.ApiResponse;
import com.hms.application.claims.PreAuthService;
import com.hms.security.SpringSecurityAuditorAware;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cashless pre-authorisation — Screens 4.1 to 4.4.
 *
 * <p>Submission endpoints return the correlation id rather than an outcome. The
 * insurer answers on a callback minutes to days later; a client written to
 * expect an inline decision will appear to work in a sandbox and hang in
 * production.
 */
@RestController
@RequestMapping("/preauth")
@RequiredArgsConstructor
@PreAuthorize("hasPermission('PREAUTH_MANAGE','')")
public class PreAuthController {

    private final PreAuthService service;
    private final SpringSecurityAuditorAware auditor;

    /** Raise a pre-auth — Screen 4.1. */
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> submit(
            @Valid @RequestBody SubmitPreAuthCmd cmd) {

        List<PreAuthService.EstimateLineCmd> lines = cmd.lines().stream()
            .map(l -> new PreAuthService.EstimateLineCmd(l.category(), l.description(),
                                                         l.quantity(), l.unitAmountPaise()))
            .toList();

        var txn = service.submitPreAuth(cmd.patientId(), cmd.encounterId(), cmd.insuranceId(),
                                        cmd.payerCode(), cmd.diagnosisCode(), cmd.diagnosisText(),
                                        cmd.plannedProcedure(), cmd.expectedLosDays(),
                                        cmd.roomType(), lines, Map.of());

        return ResponseEntity.accepted().body(ApiResponse.ok(
            "Pre-authorisation submitted",
            Map.of("id", txn.getId(),
                   "correlationId", txn.getCorrelationId(),
                   "estimatedAmount", txn.getEstimatedAmount())));
    }

    /** The itemised estimate behind a pre-auth. */
    @GetMapping("/{transactionId}/estimate")
    public ResponseEntity<ApiResponse<List<EstimateLineResponse>>> estimate(
            @PathVariable UUID transactionId) {
        return ResponseEntity.ok(ApiResponse.of(
            service.estimateFor(transactionId).stream().map(EstimateLineResponse::from).toList()));
    }

    /** The full query thread — Screen 4.2. */
    @GetMapping("/{transactionId}/queries")
    public ResponseEntity<ApiResponse<List<PreAuthQueryResponse>>> queries(
            @PathVariable UUID transactionId) {
        return ResponseEntity.ok(ApiResponse.of(
            service.queriesFor(transactionId).stream().map(PreAuthQueryResponse::from).toList()));
    }

    /** Everything the insurer is still waiting on — the desk's work queue. */
    @GetMapping("/queries/unanswered")
    public ResponseEntity<ApiResponse<List<PreAuthQueryResponse>>> unanswered() {
        return ResponseEntity.ok(ApiResponse.of(
            service.unansweredQueries().stream().map(PreAuthQueryResponse::from).toList()));
    }

    /** Answer a query — Screen 4.3. */
    @PostMapping("/queries/{queryId}/respond")
    public ResponseEntity<ApiResponse<PreAuthQueryResponse>> respond(
            @PathVariable UUID queryId,
            @Valid @RequestBody QueryResponseCmd cmd) {

        var query = service.respondToQuery(queryId, cmd.responseText(), cmd.attachmentIds(),
                                           auditor.getCurrentAuditor().orElse(null), Map.of());
        return ResponseEntity.ok(ApiResponse.ok("Response sent to the insurer",
                                                PreAuthQueryResponse.from(query)));
    }

    /** Request an enhancement — Screen 4.4. */
    @PostMapping("/{transactionId}/enhancements")
    public ResponseEntity<ApiResponse<EnhancementResponse>> enhance(
            @PathVariable UUID transactionId,
            @Valid @RequestBody EnhancementCmd cmd) {

        var enhancement = service.requestEnhancement(transactionId, cmd.revisedEstimatePaise(),
                                                     cmd.justification(), Map.of());
        return ResponseEntity.accepted().body(ApiResponse.ok(
            "Enhancement requested", EnhancementResponse.from(enhancement)));
    }

    @GetMapping("/{transactionId}/enhancements")
    public ResponseEntity<ApiResponse<List<EnhancementResponse>>> enhancements(
            @PathVariable UUID transactionId) {
        return ResponseEntity.ok(ApiResponse.of(
            service.enhancementsFor(transactionId).stream()
                   .map(EnhancementResponse::from).toList()));
    }
}
