package com.hms.api.agent.v1;

import com.hms.api.shared.ApiResponse;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.CrossTenantAccessException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.observability.CorrelationIdFilter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Locale;

/**
 * Maps exceptions to the agent error contract.
 *
 * <p>Scoped to the agent controllers only, so the existing human-facing error
 * handling is untouched.
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.hms.api.agent.v1")
public class AgentExceptionHandler {

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<ApiResponse<AgentErrorResponse>> onBusinessRule(
            BusinessRuleViolationException ex) {
        AgentErrorCode code = classify(ex.getMessage());
        // The message goes to the log (where it is masked) but not to the agent.
        log.warn("agent.tool.failed code[{}] detail[{}]", code, ex.getMessage());
        HttpStatus status = code == AgentErrorCode.DUPLICATE_BOOKING
            || code == AgentErrorCode.SLOT_FULL ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        return respond(status, code, "The request could not be completed");
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<AgentErrorResponse>> onNotFound(ResourceNotFoundException ex) {
        log.warn("agent.tool.failed code[NOT_FOUND] detail[{}]", ex.getMessage());
        return respond(HttpStatus.NOT_FOUND, AgentErrorCode.NOT_FOUND, "Not found");
    }

    @ExceptionHandler(CrossTenantAccessException.class)
    public ResponseEntity<ApiResponse<AgentErrorResponse>> onCrossTenant(CrossTenantAccessException ex) {
        // A cross-tenant attempt is a security event, not a routine 404.
        log.error("agent.tenant.violation detail[{}]", ex.getMessage());
        return respond(HttpStatus.NOT_FOUND, AgentErrorCode.NOT_FOUND, "Not found");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<AgentErrorResponse>> onDenied(AccessDeniedException ex) {
        log.warn("agent.tool.denied detail[{}]", ex.getMessage());
        return respond(HttpStatus.FORBIDDEN, AgentErrorCode.FORBIDDEN_SCOPE,
                       "The token does not carry the required scope");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<AgentErrorResponse>> onUnexpected(Exception ex) {
        log.error("agent.tool.failed code[INTERNAL_ERROR]", ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, AgentErrorCode.INTERNAL_ERROR,
                       "An internal error occurred");
    }

    private static ResponseEntity<ApiResponse<AgentErrorResponse>> respond(
            HttpStatus status, AgentErrorCode code, String message) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID);
        return ResponseEntity.status(status)
            .body(ApiResponse.ok(message, AgentErrorResponse.of(code, correlationId)));
    }

    /**
     * Best-effort mapping from the existing services' free-text rule violations.
     *
     * <p>Message-text matching is fragile by nature — a reworded exception
     * silently falls through to VALIDATION_FAILED. It is used here because the
     * existing services throw a single exception type; the durable fix is typed
     * exceptions in the scheduling service, which belongs in its own work order
     * rather than as a drive-by change to a live clinical path.
     */
    static AgentErrorCode classify(String message) {
        if (message == null) {
            return AgentErrorCode.VALIDATION_FAILED;
        }
        String m = message.toLowerCase(Locale.ROOT);
        if (m.contains("fully booked") || m.contains("no capacity") || m.contains("slot is full")) {
            return AgentErrorCode.SLOT_FULL;
        }
        if (m.contains("slot") && (m.contains("not found") || m.contains("invalid"))) {
            return AgentErrorCode.SLOT_NOT_FOUND;
        }
        if (m.contains("provider") && m.contains("not found")) {
            return AgentErrorCode.PROVIDER_NOT_FOUND;
        }
        if (m.contains("patient") && m.contains("not found")) {
            return AgentErrorCode.PATIENT_NOT_FOUND;
        }
        if (m.contains("already") && (m.contains("booked") || m.contains("exists"))) {
            return AgentErrorCode.DUPLICATE_BOOKING;
        }
        return AgentErrorCode.VALIDATION_FAILED;
    }
}
