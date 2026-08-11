package com.hms.application.claims;

import java.util.Locale;
import java.util.Set;

/**
 * Decides which handler an inbound NHCX callback belongs to.
 *
 * <p>Pulled out of {@link NhcxCallbackService} and made dependency-free for a
 * specific reason: routing is the failure mode that hides. A handler that is
 * never reached looks identical from the outside to one that runs and finds
 * nothing to do — the request returns 200, no exception is logged, and the only
 * symptom is data that quietly never appears. Every other part of this campaign
 * has had exactly that bug at least once.
 *
 * <p>Because it takes only strings, the whole routing table can be compiled and
 * executed without a container.
 */
public final class NhcxCallbackRouter {

    private NhcxCallbackRouter() {
    }

    /** What a callback should be dispatched to. */
    public enum Route {
        /** CoverageEligibilityResponse — benefit detail for Screen 2.1. */
        COVERAGE,
        /** Pre-auth decision: approved, rejected, or a query raised. */
        PREAUTH_OUTCOME,
        /** Enhancement decision, distinguished from the original pre-auth. */
        ENHANCEMENT_OUTCOME,
        /** Final claim adjudication. */
        CLAIM_OUTCOME,
        /** PaymentNotice carrying a UTR — Screen 5.3. */
        PAYMENT_NOTICE,
        /** Recognised transport, nothing to dispatch. */
        NONE
    }

    private static final Set<String> ELIGIBILITY_TYPES =
        Set.of("ELIGIBILITY", "COVERAGE_ELIGIBILITY", "DISCOVERY");

    /**
     * Resolve a route.
     *
     * <p><b>Resource type wins over exchange type.</b> A PaymentNotice arrives
     * on the claim exchange — it is the payer telling us what it paid against a
     * claim we filed — so routing on {@code exchangeType} alone would file it as
     * a claim outcome and the UTR would never reach the reconciliation screen.
     * That is the single most expensive mis-route available here, because the
     * money silently stops being tracked.
     *
     * @param exchangeType the stored type on our transaction row
     * @param resourceType FHIR {@code resourceType} from the payload, if present
     * @param hasEnhancementCorrelation whether the correlation id matches an
     *        enhancement rather than the original pre-auth
     */
    public static Route resolve(String exchangeType, String resourceType,
                                boolean hasEnhancementCorrelation) {

        String resource = upper(resourceType);
        String exchange = upper(exchangeType);

        // PaymentNotice is identified by its resource type regardless of which
        // exchange carried it.
        if ("PAYMENTNOTICE".equals(resource) || "PAYMENTRECONCILIATION".equals(resource)) {
            return Route.PAYMENT_NOTICE;
        }

        if (exchange == null) {
            return Route.NONE;
        }

        if (ELIGIBILITY_TYPES.contains(exchange)) {
            return Route.COVERAGE;
        }

        if ("PREAUTH".equals(exchange)) {
            // An enhancement reuses the pre-auth exchange, so only the
            // correlation id distinguishes them. Getting this wrong would
            // overwrite the original approval with the enhancement's answer.
            return hasEnhancementCorrelation ? Route.ENHANCEMENT_OUTCOME : Route.PREAUTH_OUTCOME;
        }

        if ("CLAIM".equals(exchange)) {
            return Route.CLAIM_OUTCOME;
        }

        return Route.NONE;
    }

    /**
     * Whether a pre-auth response is a query rather than a decision.
     *
     * <p>NHCX signals this through the outcome, not a distinct message type. A
     * query treated as a rejection would close a pre-auth the insurer is
     * actively still considering, and the hospital would resubmit instead of
     * answering — restarting the clock on a patient already admitted.
     */
    public static boolean isQuery(String outcome) {
        String o = upper(outcome);
        return "QUEUED".equals(o) || "PENDING".equals(o) || "QUERY".equals(o)
            || "QUERY_RAISED".equals(o);
    }

    /** Whether an outcome is a final decision that should stop the clock. */
    public static boolean isTerminal(String outcome) {
        String o = upper(outcome);
        return "COMPLETE".equals(o) || "ERROR".equals(o) || "REJECTED".equals(o);
    }

    private static String upper(String s) {
        return s == null || s.isBlank() ? null : s.trim().toUpperCase(Locale.ROOT);
    }
}
