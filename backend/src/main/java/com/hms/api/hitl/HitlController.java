package com.hms.api.hitl;

import com.hms.api.hitl.request.OperatorDecisionRequest;
import com.hms.api.hitl.response.EscalationResponse;
import com.hms.api.shared.ApiResponse;
import com.hms.application.hitl.HitlService;
import com.hms.infrastructure.persistence.hitl.HitlEscalationEntity;
import com.hms.security.HmsUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The Administrative Copilot's backend.
 *
 * <p>Human-facing, on the session-authenticated chain. Front-desk staff hold
 * {@code HITL_MANAGE}, not just admins — a receptionist must be able to take
 * over a conversation without waiting for a manager, which is the whole point of
 * the queue.
 */
@RestController
@RequestMapping("/hitl")
@RequiredArgsConstructor
@PreAuthorize("hasPermission('HITL_MANAGE','')")
public class HitlController {

    private final HitlService service;

    /**
     * The queue.
     *
     * <p>Summaries only — no transcripts. Rendering every waiting patient's
     * conversation into one list payload multiplies PHI exposure for no benefit,
     * since an operator reads one at a time.
     */
    @GetMapping("/escalations")
    public ResponseEntity<ApiResponse<List<EscalationResponse>>> queue() {
        List<EscalationResponse> items = service.queue().stream()
            .map(EscalationResponse::summary)
            .toList();
        return ResponseEntity.ok(ApiResponse.ok("Escalation queue", items));
    }

    @GetMapping("/escalations/{id}")
    public ResponseEntity<ApiResponse<EscalationResponse>> detail(@PathVariable UUID id) {
        HitlEscalationEntity entity = service.get(id);
        return ResponseEntity.ok(ApiResponse.ok(
            "Escalation", EscalationResponse.detail(entity, service.transcriptOf(entity))));
    }

    @PostMapping("/escalations/{id}/decision")
    public ResponseEntity<ApiResponse<EscalationResponse>> decide(
            @PathVariable UUID id,
            @Valid @RequestBody OperatorDecisionRequest decision,
            @AuthenticationPrincipal HmsUserDetails principal) {

        HitlEscalationEntity resolved = service.resolve(
            id, decision, principal == null ? null : principal.getId());
        return ResponseEntity.ok(ApiResponse.ok(
            "Decision recorded", EscalationResponse.summary(resolved)));
    }

    /** Queue depth, for the dashboard header and for alerting. */
    @GetMapping("/escalations/count")
    public ResponseEntity<ApiResponse<Long>> waitingCount() {
        return ResponseEntity.ok(ApiResponse.ok("Waiting", service.waitingCount()));
    }
}
