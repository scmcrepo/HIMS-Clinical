package com.hms.api.agent.v1;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WO-001 / T-006.
 *
 * <p>The retryable flag is a contract with an LLM agent. Marking a business
 * rejection retryable makes the agent loop on a request that can never succeed;
 * marking a transient outage non-retryable makes it give up on one that would.
 */
class AgentErrorCodeTest {

    @Test
    void businessRejectionsAreNotRetryable() {
        assertFalse(AgentErrorCode.SLOT_FULL.isRetryable(),
            "a full slot stays full; retrying just loops");
        assertFalse(AgentErrorCode.DUPLICATE_BOOKING.isRetryable(),
            "retrying a duplicate is how you get a third booking");
        assertFalse(AgentErrorCode.VALIDATION_FAILED.isRetryable());
        assertFalse(AgentErrorCode.PATIENT_NOT_FOUND.isRetryable());
    }

    @Test
    void authorisationFailuresAreNotRetryable() {
        // A revoked token stays revoked; a missing scope stays missing.
        assertFalse(AgentErrorCode.UNAUTHORIZED.isRetryable());
        assertFalse(AgentErrorCode.FORBIDDEN_SCOPE.isRetryable());
    }

    @Test
    void transientFailuresAreRetryable() {
        assertTrue(AgentErrorCode.RATE_LIMITED.isRetryable());
        assertTrue(AgentErrorCode.UPSTREAM_UNAVAILABLE.isRetryable());
        assertTrue(AgentErrorCode.INTERNAL_ERROR.isRetryable());
    }

    @Test
    void idempotencyProblemsAreNotRetryable() {
        // Retrying without fixing the key just reproduces the same error.
        assertFalse(AgentErrorCode.IDEMPOTENCY_KEY_REQUIRED.isRetryable());
        assertFalse(AgentErrorCode.IDEMPOTENCY_KEY_REUSED.isRetryable());
    }

    @Test
    void classificationMapsTheSchedulingServicesRuleViolations() {
        // Message-text matching is fragile by nature; pinning it here means a
        // reworded exception fails a test instead of silently degrading every
        // error to VALIDATION_FAILED.
        assertTrue(AgentExceptionHandler.classify("Slot is fully booked")
                   == AgentErrorCode.SLOT_FULL);
        assertTrue(AgentExceptionHandler.classify("Patient not found for id x")
                   == AgentErrorCode.PATIENT_NOT_FOUND);
        assertTrue(AgentExceptionHandler.classify("Appointment already booked")
                   == AgentErrorCode.DUPLICATE_BOOKING);
    }

    @Test
    void anUnrecognisedMessageDegradesToValidationFailed() {
        assertTrue(AgentExceptionHandler.classify("something entirely new")
                   == AgentErrorCode.VALIDATION_FAILED);
        assertTrue(AgentExceptionHandler.classify(null)
                   == AgentErrorCode.VALIDATION_FAILED);
    }
}
