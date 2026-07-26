package com.hms.infrastructure.gov;

import lombok.Getter;

/**
 * Failure talking to a government gateway.
 *
 * <p>{@code retryable} exists so the agent layer and the claim state machine can
 * distinguish "the gateway is briefly unavailable" from "this claim was
 * rejected". Retrying a rejection forever is how a claim silently never gets
 * paid.
 */
@Getter
public class GovApiException extends RuntimeException {

    private final String code;
    private final boolean retryable;

    public GovApiException(String code, String message, boolean retryable) {
        super(message);
        this.code = code;
        this.retryable = retryable;
    }
}
