package com.hms.application.claims;

import com.hms.application.claims.NhcxCallbackRouter.Route;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WO-016 / CB-001.
 *
 * <p>Routing is the failure mode that hides. A handler that is never reached
 * looks identical from outside to one that runs and finds nothing to do: 200
 * back to the gateway, nothing in the logs, and data that simply never appears.
 * Every module in this campaign has had that bug at least once.
 */
class NhcxCallbackRouterTest {

    @Test
    void paymentNoticeRoutesOnResourceTypeNotExchangeType() {
        // The expensive one. A PaymentNotice arrives on the CLAIM exchange, so
        // routing by exchange type files a disbursal as a claim outcome and the
        // UTR never reaches reconciliation — money stops being tracked silently.
        assertEquals(Route.PAYMENT_NOTICE,
            NhcxCallbackRouter.resolve("CLAIM", "PaymentNotice", false));
        assertEquals(Route.PAYMENT_NOTICE,
            NhcxCallbackRouter.resolve("PREAUTH", "PaymentNotice", false));
        assertEquals(Route.PAYMENT_NOTICE,
            NhcxCallbackRouter.resolve(null, "PaymentReconciliation", false));
    }

    @Test
    void routingIsCaseInsensitiveOnBothInputs() {
        assertEquals(Route.PAYMENT_NOTICE,
            NhcxCallbackRouter.resolve("claim", "paymentnotice", false));
        assertEquals(Route.COVERAGE, NhcxCallbackRouter.resolve("eligibility", null, false));
    }

    @Test
    void eligibilityAliasesAllRouteToCoverage() {
        assertEquals(Route.COVERAGE, NhcxCallbackRouter.resolve("ELIGIBILITY", null, false));
        assertEquals(Route.COVERAGE,
            NhcxCallbackRouter.resolve("COVERAGE_ELIGIBILITY", null, false));
        assertEquals(Route.COVERAGE, NhcxCallbackRouter.resolve("DISCOVERY", null, false));
    }

    @Test
    void enhancementIsDistinguishedFromTheOriginalPreAuthByCorrelationOnly() {
        // Both use the PREAUTH exchange. Getting this wrong overwrites the
        // original approval with the enhancement's answer.
        assertEquals(Route.PREAUTH_OUTCOME,
            NhcxCallbackRouter.resolve("PREAUTH", "ClaimResponse", false));
        assertEquals(Route.ENHANCEMENT_OUTCOME,
            NhcxCallbackRouter.resolve("PREAUTH", "ClaimResponse", true));
    }

    @Test
    void unknownAndBlankInputsResolveToNoneRatherThanGuessing() {
        assertEquals(Route.NONE, NhcxCallbackRouter.resolve(null, null, false));
        assertEquals(Route.NONE, NhcxCallbackRouter.resolve("SOMETHING_NEW", null, false));
        assertEquals(Route.NONE, NhcxCallbackRouter.resolve("  ", "  ", false));
    }

    @Test
    void aQueryIsNotARejection() {
        // Closing a pre-auth the insurer is still considering makes the hospital
        // resubmit instead of answering, restarting the clock on an admitted
        // patient.
        assertTrue(NhcxCallbackRouter.isQuery("queued"));
        assertTrue(NhcxCallbackRouter.isQuery("PENDING"));
        assertTrue(NhcxCallbackRouter.isQuery("QUERY_RAISED"));
        assertFalse(NhcxCallbackRouter.isQuery("complete"));
        assertFalse(NhcxCallbackRouter.isQuery("error"));
        assertFalse(NhcxCallbackRouter.isQuery(null));
    }

    @Test
    void anOutcomeIsNeverBothAQueryAndTerminal() {
        // Both true would close a pre-auth and keep it open at once.
        for (String outcome : new String[]{"queued", "pending", "query", "query_raised",
                                           "complete", "error", "rejected", null, "weird"}) {
            assertFalse(NhcxCallbackRouter.isQuery(outcome)
                        && NhcxCallbackRouter.isTerminal(outcome),
                        "contradictory classification for: " + outcome);
        }
    }

    @Test
    void everyRouteIsReachable() {
        // Guards against a Route constant that nothing can ever produce, which
        // would mean a handler wired to it never runs.
        Set<Route> reached = new HashSet<>();
        reached.add(NhcxCallbackRouter.resolve("CLAIM", "PaymentNotice", false));
        reached.add(NhcxCallbackRouter.resolve("ELIGIBILITY", null, false));
        reached.add(NhcxCallbackRouter.resolve("PREAUTH", null, false));
        reached.add(NhcxCallbackRouter.resolve("PREAUTH", null, true));
        reached.add(NhcxCallbackRouter.resolve("CLAIM", null, false));
        reached.add(NhcxCallbackRouter.resolve(null, null, false));

        assertEquals(Route.values().length, reached.size(),
                     "every Route constant must be produceable by resolve()");
    }
}
