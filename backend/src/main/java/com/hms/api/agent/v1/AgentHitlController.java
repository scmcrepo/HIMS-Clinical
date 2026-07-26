package com.hms.api.agent.v1;

import com.hms.api.hitl.request.RaiseEscalationRequest;
import com.hms.api.hitl.response.EscalationResponse;
import com.hms.api.shared.ApiResponse;
import com.hms.application.agent.AgentToolAuditService;
import com.hms.application.hitl.HitlService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Where the agent asks for a human.
 *
 * <p>On the agent chain, guarded by {@code AGENT_HITL_RAISE} — deliberately a
 * different scope from {@code HITL_MANAGE}. An agent must be able to ask for
 * help and must never be able to resolve its own request for help.
 *
 * <p>Tenant and branch come from the token, not the request body: otherwise a
 * caller could file an escalation into another hospital's queue simply by
 * asserting a tenant id.
 */
@RestController
@RequestMapping("/agent/v1/hitl")
@RequiredArgsConstructor
public class AgentHitlController {

    private final HitlService hitlService;
    private final AgentToolAuditService audit;

    @PostMapping("/escalations")
    @PreAuthorize("hasPermission('AGENT_HITL_RAISE','')")
    public ResponseEntity<ApiResponse<EscalationResponse>> raise(
            @Valid @RequestBody RaiseEscalationRequest body,
            HttpServletRequest request) {

        Object tokenAttr = request.getAttribute("agentTokenId");
        UUID tokenId = tokenAttr instanceof UUID uuid ? uuid : null;

        EscalationResponse response = audit.record(
            "raise_escalation", tokenId,
            () -> EscalationResponse.summary(hitlService.raise(body)));

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Escalation raised", response));
    }
}
