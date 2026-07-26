package com.hms.application.fhir;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the three NHCX request bundles: eligibility, pre-authorisation and the
 * final claim.
 *
 * <p>All three share the same skeleton — a MessageHeader plus Patient, Coverage,
 * Organization and the request resource — which is why they live together rather
 * than in three near-identical classes.
 *
 * <p>Every field here must be checked against the current NHCX implementation
 * guide before go-live. The structures follow FHIR R4 base plus the NRCES India
 * profiles; NHCX has tightened cardinalities over time and a bundle that
 * validates against base R4 can still be rejected by the gateway.
 */
@Component
public class ClaimBundleBuilder {

    /** Inputs the caller assembles from HMS data. No PII reaches logs from here. */
    public record ClaimContext(
        String patientId, String abhaNumber, String abhaAddress, String mrn,
        String patientName, String gender, String birthDate, String phone,
        String providerOrgId, String providerHfrId, String providerName,
        String payerOrgId, String payerName,
        String memberId, String policyNumber, String planName,
        String encounterId, String encounterStart, String encounterEnd,
        long totalAmountMinorUnits, String currency,
        List<ClaimItem> items
    ) {
    }

    public record ClaimItem(
        int sequence, String code, String display,
        long unitPriceMinorUnits, int quantity
    ) {
    }

    // ── eligibility ──────────────────────────────────────────────────────────

    public Map<String, Object> coverageEligibilityRequest(ClaimContext ctx, String correlationId) {
        String reqId = UUID.randomUUID().toString();
        List<Map<String, Object>> resources = new ArrayList<>();

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("resourceType", "CoverageEligibilityRequest");
        request.put("id", reqId);
        request.put("status", "active");
        request.put("purpose", List.of("validation", "benefits"));
        request.put("patient", FhirR4.reference("Patient", ctx.patientId()));
        request.put("created", java.time.OffsetDateTime.now().toString());
        request.put("provider", FhirR4.reference("Organization", ctx.providerOrgId()));
        request.put("insurer", FhirR4.reference("Organization", ctx.payerOrgId()));
        request.put("insurance", List.of(Map.of(
            "focal", true,
            "coverage", FhirR4.reference("Coverage", coverageId(ctx)))));

        resources.add(request);
        resources.addAll(supportingResources(ctx));

        return FhirR4.messageBundle(UUID.randomUUID().toString(),
            "coverageeligibilityrequest", ctx.providerHfrId(), ctx.payerOrgId(),
            correlationId, resources);
    }

    // ── pre-auth and claim ───────────────────────────────────────────────────

    /**
     * @param use {@code preauthorization} or {@code claim}. The bundle shape is
     *            identical; only this discriminator and the message event differ,
     *            which is exactly why they share a builder.
     */
    public Map<String, Object> claimRequest(ClaimContext ctx, String use, String correlationId) {
        String claimId = UUID.randomUUID().toString();
        List<Map<String, Object>> resources = new ArrayList<>();

        Map<String, Object> claim = new LinkedHashMap<>();
        claim.put("resourceType", "Claim");
        claim.put("id", claimId);
        claim.put("status", "active");
        claim.put("type", FhirR4.codeableConcept(FhirR4.SYS_CLAIM_TYPE, "institutional",
                                                 "Institutional"));
        claim.put("use", use);
        claim.put("patient", FhirR4.reference("Patient", ctx.patientId()));
        claim.put("created", java.time.OffsetDateTime.now().toString());
        claim.put("insurer", FhirR4.reference("Organization", ctx.payerOrgId()));
        claim.put("provider", FhirR4.reference("Organization", ctx.providerOrgId()));
        claim.put("priority", FhirR4.codeableConcept(FhirR4.SYS_PROCESS_PRIORITY, "normal",
                                                     "Normal"));
        claim.put("insurance", List.of(Map.of(
            "sequence", 1,
            "focal", true,
            "coverage", FhirR4.reference("Coverage", coverageId(ctx)))));
        claim.put("total", FhirR4.money(ctx.totalAmountMinorUnits(), ctx.currency()));

        if (ctx.encounterId() != null) {
            claim.put("item", claimItems(ctx));
            claim.put("supportingInfo", List.of(Map.of(
                "sequence", 1,
                "category", FhirR4.codeableConcept(
                    "http://terminology.hl7.org/CodeSystem/claiminformationcategory",
                    "info", "Information"),
                "valueReference", FhirR4.reference("Encounter", ctx.encounterId()))));
        }

        resources.add(claim);
        resources.addAll(supportingResources(ctx));
        if (ctx.encounterId() != null) {
            resources.add(FhirR4.encounter(ctx.encounterId(), "finished", "IMP",
                                           ctx.patientId(), ctx.encounterStart(),
                                           ctx.encounterEnd()));
        }

        String event = "preauthorization".equals(use) ? "preauth-request" : "claim-request";
        return FhirR4.messageBundle(UUID.randomUUID().toString(), event,
            ctx.providerHfrId(), ctx.payerOrgId(), correlationId, resources);
    }

    // ── shared pieces ────────────────────────────────────────────────────────

    private List<Map<String, Object>> supportingResources(ClaimContext ctx) {
        List<Map<String, Object>> out = new ArrayList<>();
        out.add(FhirR4.patient(ctx.patientId(), ctx.abhaNumber(), ctx.abhaAddress(), ctx.mrn(),
                               ctx.patientName(), ctx.gender(), ctx.birthDate(), ctx.phone()));
        out.add(FhirR4.organization(ctx.providerOrgId(), ctx.providerHfrId(), ctx.providerName()));
        out.add(FhirR4.organization(ctx.payerOrgId(), null, ctx.payerName()));
        out.add(FhirR4.coverage(coverageId(ctx), ctx.patientId(), ctx.payerOrgId(),
                                ctx.memberId(), ctx.policyNumber(), ctx.planName()));
        return out;
    }

    private List<Map<String, Object>> claimItems(ClaimContext ctx) {
        List<Map<String, Object>> items = new ArrayList<>();
        List<ClaimItem> source = ctx.items() == null ? List.of() : ctx.items();
        for (ClaimItem item : source) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sequence", item.sequence());
            m.put("productOrService", FhirR4.codeableConcept(
                "https://nrces.in/ndhm/fhir/r4/CodeSystem/ndhm-procedure",
                item.code(), item.display()));
            m.put("unitPrice", FhirR4.money(item.unitPriceMinorUnits(), ctx.currency()));
            m.put("quantity", Map.of("value", item.quantity()));
            m.put("net", FhirR4.money(item.unitPriceMinorUnits() * (long) item.quantity(),
                                      ctx.currency()));
            items.add(m);
        }
        return items;
    }

    /** Stable per-context so Claim and Coverage entries reference the same id. */
    private String coverageId(ClaimContext ctx) {
        return UUID.nameUUIDFromBytes(
            (ctx.patientId() + "|" + ctx.payerOrgId() + "|"
             + String.valueOf(ctx.memberId())).getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .toString();
    }
}
