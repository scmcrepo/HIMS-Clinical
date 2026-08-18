package com.hms.application.portal;

/**
 * The portal's error vocabulary, shared with the mobile client.
 *
 * <p>Note what is missing: there is no {@code MOBILE_NOT_REGISTERED}. Whether a
 * number has records at a hospital is itself disclosable information — someone
 * probing numbers against a de-addiction or fertility clinic learns something
 * real from a "not found" — so the unregistered case is answered with the same
 * shape and the same code as the registered one, and the difference surfaces
 * only after the code is verified.
 */
public enum PortalErrorCode {
    OTP_RATE_LIMITED(429, false),
    OTP_INVALID(401, false),
    OTP_EXPIRED(401, false),
    OTP_ATTEMPTS_EXCEEDED(401, false),
    OTP_DELIVERY_FAILED(503, true),
    IDENTITY_TOKEN_REQUIRED(401, false),
    PATIENT_NOT_IN_CANDIDATE_SET(403, false),
    REGISTRATION_CAP_REACHED(409, false),
    UNAUTHORIZED(401, false),
    NOT_FOUND(404, false),
    VALIDATION_FAILED(400, false);

    private final int httpStatus;
    private final boolean retryable;

    PortalErrorCode(int httpStatus, boolean retryable) {
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public boolean retryable() {
        return retryable;
    }
}
