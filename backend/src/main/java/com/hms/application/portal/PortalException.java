package com.hms.application.portal;

/**
 * A portal failure that maps to a client error code.
 *
 * <p>The message is for the log only. The client receives the {@link
 * PortalErrorCode} and renders its own localised string — backend messages can
 * carry identifiers, and a phone screen is a poor place to leak one.
 */
public class PortalException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient PortalErrorCode code;

    public PortalException(PortalErrorCode code, String logMessage) {
        super(logMessage);
        this.code = code;
    }

    public PortalErrorCode getCode() {
        return code;
    }
}
