package com.hms.application.policy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.infrastructure.persistence.policy.PolicyCoverageEntity;
import com.hms.infrastructure.persistence.policy.PolicyExclusionEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WO-013 / PD-002.
 *
 * <p>This is the class that converts a payer's numbers into the hospital's, so
 * the tests are weighted towards the two failure modes that would not show up
 * until reconciliation: floating-point money loss, and treating an omitted
 * benefit as a zero entitlement.
 */
class CoverageResponseParserTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final CoverageResponseParser parser = new CoverageResponseParser();

    private JsonNode json(String s) throws Exception {
        return mapper.readTree(s);
    }

    private PolicyCoverageEntity blank() {
        PolicyCoverageEntity c = new PolicyCoverageEntity();
        c.setCorrelationId("corr-1");
        return c;
    }

    // ── money conversion ─────────────────────────────────────────────────────

    @Test
    void convertsRupeesToPaiseWithoutFloatingPointLoss() throws Exception {
        // (long)(1.15 * 100) is 114 in binary floating point. It must be 115.
        assertEquals(115L, CoverageResponseParser.paise(json("1.15")));
        assertEquals(7L, CoverageResponseParser.paise(json("0.07")));
        assertEquals(100000000L, CoverageResponseParser.paise(json("1000000")));
    }

    @Test
    void roundsHalfUpAtSubPaisePrecision() throws Exception {
        assertEquals(116L, CoverageResponseParser.paise(json("1.155")));
        assertEquals(115L, CoverageResponseParser.paise(json("1.154")));
    }

    @Test
    void unparseableAmountBecomesNullNotZero() throws Exception {
        // Zero would read on the admission form as "no cover", which is a
        // materially different and dangerous claim.
        assertNull(CoverageResponseParser.paise(json("\"not-a-number\"")));
    }

    @Test
    void missingAmountIsNull() throws Exception {
        assertNull(CoverageResponseParser.paise(json("{}").path("absent")));
        assertNull(CoverageResponseParser.paise(json("null")));
    }

    @Test
    void convertsPercentageToBasisPoints() throws Exception {
        assertEquals(1000, CoverageResponseParser.basisPoints(json("10")));
        // 7.5% is exactly the value an integer-percent column could not hold.
        assertEquals(750, CoverageResponseParser.basisPoints(json("7.5")));
        assertEquals(0, CoverageResponseParser.basisPoints(json("0")));
    }

    @Test
    void rejectsCoPayOutsideZeroToHundred() throws Exception {
        assertNull(CoverageResponseParser.basisPoints(json("150")));
        assertNull(CoverageResponseParser.basisPoints(json("-5")));
    }

    // ── policy status ────────────────────────────────────────────────────────

    @Test
    void inforcePolicyIsActive() throws Exception {
        PolicyCoverageEntity c = blank();
        parser.applyTo(c, json("{\"outcome\":\"complete\",\"insurance\":[{\"inforce\":true}]}"));
        assertEquals("ACTIVE", c.getPolicyStatus());
    }

    @Test
    void distinguishesSuspendedFromExpired() throws Exception {
        PolicyCoverageEntity susp = blank();
        parser.applyTo(susp, json("{\"outcome\":\"complete\",\"disposition\":\"Policy suspended\","
                                  + "\"insurance\":[{\"inforce\":false}]}"));
        assertEquals("SUSPENDED", susp.getPolicyStatus());

        PolicyCoverageEntity exp = blank();
        parser.applyTo(exp, json("{\"outcome\":\"complete\",\"disposition\":\"Cover expired\","
                                 + "\"insurance\":[{\"inforce\":false}]}"));
        assertEquals("EXPIRED", exp.getPolicyStatus());
    }

    @Test
    void payerErrorIsUnknownNotInactive() throws Exception {
        // "We could not answer" must not be shown as "you have no cover".
        PolicyCoverageEntity c = blank();
        parser.applyTo(c, json("{\"outcome\":\"error\",\"insurance\":[{\"inforce\":true}]}"));
        assertEquals("UNKNOWN", c.getPolicyStatus());
    }

    @Test
    void nullResponseLeavesStatusUnknown() {
        PolicyCoverageEntity c = blank();
        parser.applyTo(c, null);
        assertEquals("UNKNOWN", c.getPolicyStatus());
        assertNull(c.getSumInsuredPaise());
    }

    // ── benefits ─────────────────────────────────────────────────────────────

    @Test
    void extractsSumInsuredAndUtilisationAndDerivesBalance() throws Exception {
        PolicyCoverageEntity c = blank();
        parser.applyTo(c, json("""
            {"outcome":"complete","insurance":[{"inforce":true,"item":[
              {"category":{"coding":[{"code":"overall"}]},
               "benefit":[{"type":{"coding":[{"code":"benefit"}]},
                           "allowedMoney":{"value":1000000},
                           "usedMoney":{"value":150000}}]}
            ]}]}""".stripIndent()));

        assertEquals(100000000L, c.getSumInsuredPaise());
        assertEquals(15000000L, c.getUtilisedPaise());
        assertEquals(85000000L, c.getBalancePaise());
    }

    @Test
    void balanceIsDerivedNotTakenFromAStalePayerField() throws Exception {
        PolicyCoverageEntity c = blank();
        parser.applyTo(c, json("""
            {"outcome":"complete","insurance":[{"inforce":true,"item":[
              {"category":{"coding":[{"code":"overall"}]},
               "benefit":[{"type":{"coding":[{"code":"benefit"}]},
                           "allowedMoney":{"value":500000},
                           "usedMoney":{"value":200000}}]}
            ]}]}""".stripIndent()));
        assertEquals(30000000L, c.getBalancePaise());
    }

    @Test
    void overUtilisationClampsToZeroRatherThanNegativeEntitlement() throws Exception {
        PolicyCoverageEntity c = blank();
        parser.applyTo(c, json("""
            {"outcome":"complete","insurance":[{"inforce":true,"item":[
              {"category":{"coding":[{"code":"overall"}]},
               "benefit":[{"type":{"coding":[{"code":"benefit"}]},
                           "allowedMoney":{"value":100000},
                           "usedMoney":{"value":150000}}]}
            ]}]}""".stripIndent()));
        assertEquals(0L, c.getBalancePaise());
    }

    @Test
    void separatesIcuCapFromRoomRentCap() throws Exception {
        PolicyCoverageEntity c = blank();
        parser.applyTo(c, json("""
            {"outcome":"complete","insurance":[{"inforce":true,"item":[
              {"category":{"coding":[{"code":"room-rent"}]},
               "productOrService":{"coding":[{"code":"room","display":"Single Private AC"}]},
               "benefit":[{"type":{"coding":[{"code":"benefit"}]},
                           "allowedMoney":{"value":5000}}]},
              {"category":{"coding":[{"code":"icu-daily"}]},
               "benefit":[{"type":{"coding":[{"code":"benefit"}]},
                           "allowedMoney":{"value":10000}}]}
            ]}]}""".stripIndent()));

        assertEquals(500000L, c.getRoomRentCapPaise());
        assertEquals(1000000L, c.getIcuCapPaise());
        assertEquals("Single Private AC", c.getRoomCategory());
    }

    @Test
    void omittedRoomCapStaysNullRatherThanZero() throws Exception {
        // "No cap stated" and "nothing covered" must not collapse together.
        PolicyCoverageEntity c = blank();
        parser.applyTo(c, json("""
            {"outcome":"complete","insurance":[{"inforce":true,"item":[
              {"category":{"coding":[{"code":"overall"}]},
               "benefit":[{"type":{"coding":[{"code":"benefit"}]},
                           "allowedMoney":{"value":1000000}}]}
            ]}]}""".stripIndent()));

        assertNull(c.getRoomRentCapPaise());
        assertNull(c.getIcuCapPaise());
        assertNull(c.getCoPayBasisPoints());
    }

    @Test
    void extractsCoPayAsBasisPoints() throws Exception {
        PolicyCoverageEntity c = blank();
        parser.applyTo(c, json("""
            {"outcome":"complete","insurance":[{"inforce":true,"item":[
              {"category":{"coding":[{"code":"overall"}]},
               "benefit":[{"type":{"coding":[{"code":"copay"}]},
                           "allowedMoney":{"value":10}}]}
            ]}]}""".stripIndent()));
        assertEquals(1000, c.getCoPayBasisPoints());
    }

    @Test
    void extractsPedWaitingPeriod() throws Exception {
        PolicyCoverageEntity c = blank();
        parser.applyTo(c, json("""
            {"outcome":"complete","insurance":[{"inforce":true,"item":[
              {"category":{"coding":[{"code":"ped-waiting"}]},
               "term":{"value":24},"excluded":true}
            ]}]}""".stripIndent()));

        assertEquals(24, c.getPedWaitingMonths());
        assertNotNull(c.getPedWaitingSatisfied());
        assertFalse(c.getPedWaitingSatisfied());
    }

    // ── exclusions ───────────────────────────────────────────────────────────

    @Test
    void collectsExcludedItemsAsExclusions() throws Exception {
        List<PolicyExclusionEntity> ex = parser.exclusionsFrom(json("""
            {"outcome":"complete","insurance":[{"inforce":true,"item":[
              {"excluded":true,
               "productOrService":{"coding":[{"code":"COSM","display":"Cosmetic procedures"}]}},
              {"excluded":false,
               "productOrService":{"coding":[{"code":"SURG","display":"Surgery"}]}}
            ]}]}""".stripIndent()));

        assertEquals(1, ex.size());
        assertEquals("COSM", ex.get(0).getCode());
        assertEquals("Cosmetic procedures", ex.get(0).getDescription());
        assertEquals("EXCLUSION", ex.get(0).getKind());
    }

    @Test
    void payerErrorsBecomeRestrictions() throws Exception {
        List<PolicyExclusionEntity> ex = parser.exclusionsFrom(json("""
            {"outcome":"complete","insurance":[{"inforce":true,"item":[]}],
             "error":[{"code":{"coding":[{"code":"E-04","display":"Network hospital only"}]}}]}
            """.stripIndent()));

        assertEquals(1, ex.size());
        assertEquals("RESTRICTION", ex.get(0).getKind());
        assertEquals("Network hospital only", ex.get(0).getDescription());
    }

    @Test
    void handlesEmptyAndMalformedResponsesWithoutThrowing() throws Exception {
        assertTrue(parser.exclusionsFrom(null).isEmpty());
        assertTrue(parser.exclusionsFrom(json("{}")).isEmpty());
        assertTrue(parser.exclusionsFrom(json("{\"insurance\":[]}")).isEmpty());

        PolicyCoverageEntity c = blank();
        parser.applyTo(c, json("{\"insurance\":[]}"));
        assertEquals("UNKNOWN", c.getPolicyStatus());
    }
}
