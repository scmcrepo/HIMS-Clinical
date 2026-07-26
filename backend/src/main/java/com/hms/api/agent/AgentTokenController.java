package com.hms.api.agent;

import com.hms.api.agent.request.IssueAgentTokenRequest;
import com.hms.api.agent.response.AgentTokenResponse;
import com.hms.api.shared.ApiResponse;
import com.hms.application.agent.AgentTokenService;
import com.hms.infrastructure.persistence.agent.AgentApiTokenEntity;
import com.hms.security.HmsUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Administrative management of agent credentials.
 *
 * <p>Lives on the normal session-authenticated chain, not the agent chain: this
 * is a human administrator's screen. An agent must never be able to reach it, or
 * it could mint itself a wider credential — which is why {@code AGENT_TOKEN_MANAGE}
 * is not in the issuable scope set.
 */
@RestController
@RequestMapping("/agent/tokens")
@RequiredArgsConstructor
@PreAuthorize("hasPermission('AGENT_TOKEN_MANAGE','')")
public class AgentTokenController {

    private final AgentTokenService service;

    @PostMapping
    public ResponseEntity<ApiResponse<AgentTokenResponse>> issue(
            @Valid @RequestBody IssueAgentTokenRequest req) {

        AgentTokenService.IssuedToken issued = service.issue(
            req.name(), req.scopes(), req.branchId(),
            req.validityDays() == null ? null : Duration.ofDays(req.validityDays()));

        // The plaintext appears in this response and nowhere else, ever.
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
            "Token created. Copy it now — it cannot be retrieved again.",
            AgentTokenResponse.withPlaintext(issued.entity(), issued.plaintext())));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AgentTokenResponse>>> list() {
        List<AgentTokenResponse> tokens = service.list().stream()
            .map(AgentTokenResponse::from)
            .toList();
        return ResponseEntity.ok(ApiResponse.ok("Agent tokens", tokens));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> revoke(
            @PathVariable UUID id,
            @AuthenticationPrincipal HmsUserDetails principal) {
        service.revoke(id, principal == null ? null : principal.getId());
        return ResponseEntity.ok(ApiResponse.ok("Token revoked"));
    }

    /** Keeps the entity import meaningful for readers of {@link AgentTokenResponse}. */
    @SuppressWarnings("unused")
    private static AgentApiTokenEntity typeAnchor() {
        return null;
    }
}
