package com.hms.api.shared;

import com.hms.application.compliance.ConsentRequiredException;
import com.hms.application.incident.CrossTenantAccessDetector;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.exception.CrossTenantAccessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private final CrossTenantAccessDetector crossTenantDetector;

    public GlobalExceptionHandler(CrossTenantAccessDetector crossTenantDetector) {
        this.crossTenantDetector = crossTenantDetector;
    }

    /**
     * The patient has not consented to what the caller is trying to do.
     *
     * <p>409, not 403: the caller is perfectly authorised, and the thing missing
     * is the patient's agreement. Sending 403 would send the desk to an
     * administrator to fix a permission that is not broken.
     *
     * <p>The body carries the notice text so the client can show it and resubmit
     * with an attestation in one round trip. Notice text is hospital copy and
     * contains no patient data. The patient id is deliberately not echoed.
     */
    @ExceptionHandler(ConsentRequiredException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleConsentRequired(
            ConsentRequiredException ex) {
        log.warn("event=consent.required purpose={}", ex.getPurpose());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "CONSENT_REQUIRED");
        body.put("purpose", ex.getPurpose().name());
        body.put("requiredForCare", ex.getPurpose().isRequiredForCare());
        if (ex.getNoticeVersion() != null) {
            body.put("noticeVersion", ex.getNoticeVersion());
            body.put("noticeLanguage", ex.getNoticeLanguage());
            body.put("noticeText", ex.getNoticeText());
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.of(ex.getMessage(), body));
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessRule(BusinessRuleViolationException ex) {
        log.warn("Business rule violation: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Validation failed: {}", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("Validation failed: " + errors));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageNotReadable(org.springframework.http.converter.HttpMessageNotReadableException ex) {
        log.warn("Malformed JSON request body: {}", ex.getMessage());
        String msg = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("Invalid request body: " + msg));
    }

    /**
     * WO-026: was logging the exception message and calling
     * {@code printStackTrace()}. Spring's access-denied messages can name the
     * resource and the principal, and stderr bypasses the structured pipeline
     * Promtail scrapes.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        String context = ex.getStackTrace().length > 0
            ? ex.getStackTrace()[0].getClassName()
            : "unknown";
        log.warn("event=security.access_denied context={}", context);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Access denied"));
    }

    /**
     * Tenant isolation stopped something.
     *
     * <p>WO-026 changed two things here. The handler used to call
     * {@code ex.printStackTrace()}, which writes to stderr outside the
     * structured logging pipeline — so the one signal worth having never
     * reached Loki and nothing counted it. The guard worked and nobody would
     * have known it had fired.
     *
     * <p>The exception message is also no longer logged. It can quote the
     * identifiers involved, and a cross-tenant event is exactly the wrong place
     * to write identifiers from two tenants into one log line. The stack's top
     * frame is enough to locate the code.
     */
    @ExceptionHandler(CrossTenantAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleCrossTenant(CrossTenantAccessException ex) {
        String context = ex.getStackTrace().length > 0
            ? ex.getStackTrace()[0].getClassName()
            : "unknown";
        log.warn("event=security.cross_tenant.blocked context={}", context);
        crossTenantDetector.recordBlockedAttempt(context);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Access denied"));
    }

    @ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(org.springframework.security.authentication.BadCredentialsException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(org.springframework.security.authentication.DisabledException.class)
    public ResponseEntity<ApiResponse<Void>> handleDisabled(org.springframework.security.authentication.DisabledException ex) {
        log.warn("Authentication disabled: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred"));
    }
}
