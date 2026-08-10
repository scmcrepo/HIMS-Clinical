package com.hms.application.policy;

import com.fasterxml.jackson.databind.JsonNode;
import com.hms.infrastructure.persistence.policy.PolicyCoverageEntity;
import com.hms.infrastructure.persistence.policy.PolicyExclusionEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns a FHIR R4 {@code CoverageEligibilityResponse} into the values Screen 2.1
 * displays.
 *
 * <p>This class is small and heavily tested for a reason: it is the only place
 * where a payer's numbers become the hospital's numbers. Everything downstream —
 * the co-pay the patient is asked to sign for, the room they are admitted to,
 * the pre-auth estimate — is derived from what happens here.
 *
 * <h2>Money handling</h2>
 * FHIR sends amounts as decimal rupees. Storage is in paise as {@code long}.
 * The conversion goes through {@link BigDecimal} with {@link RoundingMode#HALF_UP}
 * rather than {@code (long)(value * 100)}, because binary floating point cannot
 * represent 0.07 exactly and the naive cast silently truncates. Across a few
 * thousand claims that is a reconciliation failure nobody can trace back.
 *
 * <h2>Missing versus zero</h2>
 * A payer omitting the room-rent cap means "no cap stated"; a payer sending zero
 * means "nothing is covered". Conflating them would let the desk admit a patient
 * to a room the policy does not cover, so absent values stay {@code null} and are
 * never defaulted to zero.
 */
@Slf4j
@Component
public class CoverageResponseParser {

    /** FHIR benefit codes this parser recognises. Unknown codes are kept as exclusions. */
    static final String BENEFIT_SUM_INSURED = "benefit";
    static final String BENEFIT_UTILISED = "utilized";

    /**
     * Populate a coverage snapshot from a payer response.
     *
     * <p>The entity is mutated rather than returned fresh so the caller keeps
     * ownership of tenant, patient and correlation fields it already set.
     */
    public void applyTo(PolicyCoverageEntity coverage, JsonNode response) {
        if (response == null || response.isMissingNode()) {
            coverage.setPolicyStatus("UNKNOWN");
            return;
        }

        coverage.setPolicyStatus(policyStatus(response));

        JsonNode insurance = firstInsurance(response);
        if (insurance == null) {
            return;
        }

        for (JsonNode item : insurance.path("item")) {
            String category = code(item.path("category"));
            String productOrService = code(item.path("productOrService"));

            for (JsonNode benefit : item.path("benefit")) {
                String type = code(benefit.path("type"));
                Long allowed = paise(benefit.path("allowedMoney").path("value"));
                Long used = paise(benefit.path("usedMoney").path("value"));

                applyBenefit(coverage, type, category, productOrService, allowed, used);
            }

            if (isRoomCategory(category, productOrService)) {
                String display = display(item.path("productOrService"));
                if (display != null) {
                    coverage.setRoomCategory(display);
                }
            }
        }

        applyCoPay(coverage, insurance);
        applyPedWaiting(coverage, insurance);
        deriveBalance(coverage);
    }

    /** Exclusions, restrictions and sub-limits, one row each. */
    public List<PolicyExclusionEntity> exclusionsFrom(JsonNode response) {
        List<PolicyExclusionEntity> out = new ArrayList<>();
        if (response == null) {
            return out;
        }

        JsonNode insurance = firstInsurance(response);
        if (insurance == null) {
            return out;
        }

        for (JsonNode item : insurance.path("item")) {
            if (item.path("excluded").asBoolean(false)) {
                PolicyExclusionEntity e = new PolicyExclusionEntity();
                e.setKind("EXCLUSION");
                e.setCode(code(item.path("productOrService")));
                e.setDescription(firstNonBlank(
                    display(item.path("productOrService")),
                    item.path("name").asText(null),
                    "Excluded benefit"));
                out.add(e);
            }
        }

        for (JsonNode error : response.path("error")) {
            PolicyExclusionEntity e = new PolicyExclusionEntity();
            e.setKind("RESTRICTION");
            e.setCode(code(error.path("code")));
            e.setDescription(firstNonBlank(display(error.path("code")), "Payer restriction"));
            out.add(e);
        }

        return out;
    }

    // ── benefit mapping ──────────────────────────────────────────────────────

    private void applyBenefit(PolicyCoverageEntity coverage, String type, String category,
                              String productOrService, Long allowed, Long used) {

        if (isIcu(category, productOrService)) {
            if (allowed != null) coverage.setIcuCapPaise(allowed);
            return;
        }
        if (isRoomCategory(category, productOrService)) {
            if (allowed != null) coverage.setRoomRentCapPaise(allowed);
            return;
        }
        if (isDeductible(type, category)) {
            if (allowed != null) coverage.setDeductiblePaise(allowed);
            return;
        }

        // Otherwise this is the headline sum insured / utilisation pair.
        if (BENEFIT_SUM_INSURED.equalsIgnoreCase(type) && allowed != null) {
            coverage.setSumInsuredPaise(allowed);
        }
        if (used != null) {
            coverage.setUtilisedPaise(used);
        }
        if (BENEFIT_UTILISED.equalsIgnoreCase(type) && allowed != null) {
            coverage.setUtilisedPaise(allowed);
        }
    }

    /**
     * Balance is derived, never trusted from the payer when both inputs exist.
     *
     * <p>Payers do send a balance, and it is sometimes stale relative to the
     * sum-insured and utilisation figures in the same response. Deriving keeps
     * the three numbers on screen internally consistent, which matters because
     * the desk reads them together.
     */
    private void deriveBalance(PolicyCoverageEntity coverage) {
        Long sum = coverage.getSumInsuredPaise();
        Long used = coverage.getUtilisedPaise();
        if (sum == null) {
            return;
        }
        long utilised = used == null ? 0L : used;
        long balance = sum - utilised;
        if (balance < 0) {
            // Over-utilisation against the stated sum insured is a payer data
            // problem. Clamp for display, but say so — it must not read as a
            // negative entitlement on an admission form.
            log.warn("nhcx.coverage.balance.negative correlationId[{}] clamped",
                     coverage.getCorrelationId());
            balance = 0L;
        }
        coverage.setBalancePaise(balance);
    }

    private void applyCoPay(PolicyCoverageEntity coverage, JsonNode insurance) {
        for (JsonNode item : insurance.path("item")) {
            for (JsonNode benefit : item.path("benefit")) {
                JsonNode pct = benefit.path("allowedUnsignedInt");
                String type = code(benefit.path("type"));
                if ("copay".equalsIgnoreCase(type) || "co-pay".equalsIgnoreCase(type)) {
                    Integer bp = basisPoints(benefit.path("allowedMoney").path("value").isMissingNode()
                                             ? pct
                                             : benefit.path("allowedMoney").path("value"));
                    if (bp != null) {
                        coverage.setCoPayBasisPoints(bp);
                        return;
                    }
                }
            }
        }
    }

    private void applyPedWaiting(PolicyCoverageEntity coverage, JsonNode insurance) {
        for (JsonNode item : insurance.path("item")) {
            String category = code(item.path("category"));
            if (category != null && category.toLowerCase(Locale.ROOT).contains("ped")) {
                JsonNode term = item.path("term");
                if (term.hasNonNull("value")) {
                    coverage.setPedWaitingMonths(term.path("value").asInt());
                }
                coverage.setPedWaitingSatisfied(!item.path("excluded").asBoolean(false));
                return;
            }
        }
    }

    // ── conversions ──────────────────────────────────────────────────────────

    /**
     * Rupees to paise, exactly.
     *
     * <p>{@code (long)(1.15 * 100)} is 114. This is why the conversion is not
     * done with doubles.
     */
    static Long paise(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        try {
            BigDecimal rupees = new BigDecimal(value.asText());
            return rupees.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            log.warn("nhcx.coverage.amount.unparseable type[{}]", e.getClass().getSimpleName());
            return null;
        }
    }

    /** A percentage to basis points: 7.5 becomes 750. */
    static Integer basisPoints(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        try {
            BigDecimal percent = new BigDecimal(value.asText());
            if (percent.compareTo(BigDecimal.ZERO) < 0
                || percent.compareTo(BigDecimal.valueOf(100)) > 0) {
                log.warn("nhcx.coverage.copay.outOfRange");
                return null;
            }
            return percent.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }

    private static String policyStatus(JsonNode response) {
        String outcome = response.path("outcome").asText("");
        boolean inforce = response.path("insurance").isArray()
                          && response.path("insurance").size() > 0
                          && response.path("insurance").get(0).path("inforce").asBoolean(false);

        if ("error".equalsIgnoreCase(outcome)) {
            return "UNKNOWN";
        }
        if (inforce) {
            return "ACTIVE";
        }
        // Not in force, but the payer answered — distinguish expiry from a
        // suspension where the payer said so explicitly.
        String disposition = response.path("disposition").asText("").toLowerCase(Locale.ROOT);
        if (disposition.contains("suspend")) return "SUSPENDED";
        if (disposition.contains("lapse")) return "LAPSED";
        if (disposition.contains("expire")) return "EXPIRED";
        return "UNKNOWN";
    }

    private static JsonNode firstInsurance(JsonNode response) {
        JsonNode arr = response.path("insurance");
        return arr.isArray() && arr.size() > 0 ? arr.get(0) : null;
    }

    private static boolean isIcu(String category, String productOrService) {
        return contains(category, "icu") || contains(productOrService, "icu")
            || contains(category, "intensive") || contains(productOrService, "intensive");
    }

    private static boolean isRoomCategory(String category, String productOrService) {
        return contains(category, "room") || contains(productOrService, "room")
            || contains(category, "bed") || contains(productOrService, "accommodation");
    }

    private static boolean isDeductible(String type, String category) {
        return contains(type, "deductible") || contains(category, "deductible");
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String code(JsonNode codeableConcept) {
        JsonNode coding = codeableConcept.path("coding");
        if (coding.isArray() && coding.size() > 0) {
            return coding.get(0).path("code").asText(null);
        }
        return codeableConcept.path("text").asText(null);
    }

    private static String display(JsonNode codeableConcept) {
        JsonNode coding = codeableConcept.path("coding");
        if (coding.isArray() && coding.size() > 0) {
            String d = coding.get(0).path("display").asText(null);
            if (d != null && !d.isBlank()) return d;
        }
        String text = codeableConcept.path("text").asText(null);
        return text == null || text.isBlank() ? null : text;
    }

    private static String firstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isBlank()) return c;
        }
        return "Unspecified";
    }
}
