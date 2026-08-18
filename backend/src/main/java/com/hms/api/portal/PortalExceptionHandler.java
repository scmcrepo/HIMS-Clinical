package com.hms.api.portal;

import com.hms.api.shared.ApiResponse;
import com.hms.application.portal.PortalException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Maps {@link PortalException} to the envelope the mobile client expects:
 * {@code {message, data:{code, retryable}}}.
 *
 * <p>Scoped to the portal controllers only, and ordered ahead of the global
 * handler, so nothing here changes how the other 176 controllers report errors.
 *
 * <p>The client is sent a code, never the exception message. Portal exception
 * messages carry log detail — challenge ids, mismatch reasons — and some of the
 * codes describe the state of another person's data. The app renders its own
 * localised string from the code, which is also why it can show that string in
 * Tamil without a server change.
 */
@RestControllerAdvice(assignableTypes = {
    PortalAuthController.class,
    PortalDirectoryController.class
})
@Order(0)
@Slf4j
public class PortalExceptionHandler {

    @ExceptionHandler(PortalException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handle(PortalException e) {
        // Message to the log, code to the caller.
        log.warn("event=portal.error code={} detail={}", e.getCode(), e.getMessage());

        return ResponseEntity
            .status(e.getCode().httpStatus())
            .body(ApiResponse.ok(
                userFacingMessage(e.getCode().name()),
                Map.of(
                    "code", e.getCode().name(),
                    "retryable", e.getCode().retryable())));
    }

    /**
     * A neutral fallback string for clients that do not localise. Deliberately
     * vague: the localised text lives in the app's message pack, and anything
     * specific written here would leak the distinction the code is hiding.
     */
    private static String userFacingMessage(String code) {
        return switch (code) {
            case "OTP_RATE_LIMITED" -> "Too many attempts. Please try again shortly.";
            case "OTP_INVALID", "OTP_EXPIRED", "OTP_ATTEMPTS_EXCEEDED" ->
                "That code is not valid. Please request a new one.";
            case "REGISTRATION_CAP_REACHED" ->
                "This number has reached the maximum number of registrations.";
            case "OTP_DELIVERY_FAILED" ->
                "We could not send the code right now. Please try again.";
            default -> "Request could not be completed.";
        };
    }
}
