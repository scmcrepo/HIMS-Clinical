package com.hms.application.fhir;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Minimal HL7 FHIR R4.0.1 resource builders.
 *
 * <p><b>Why hand-built rather than HAPI FHIR.</b> HAPI is the right long-term
 * choice and its validator is genuinely valuable — a bundle rejected at the NHCX
 * gateway costs far more than one caught locally. It is not used here because
 * this code was written without a compiler available: an error against HAPI's
 * API surfaces as a wall of messages inside an unfamiliar library, whereas an
 * error here is in code you can read and fix. The structures below are plain
 * maps serialised by the Jackson you already have, so there is no new
 * dependency and no resolution risk.
 *
 * <p><b>Upgrade path.</b> Add {@code ca.uhn.hapi.fhir:hapi-fhir-structures-r4}
 * and {@code hapi-fhir-validation}, then keep these builders as the mapping
 * layer and hand the resulting JSON to HAPI's validator before transmission.
 * That gets profile validation without rewriting the mapping.
 *
 * <p><b>Verify against current specs.</b> ABDM and NHCX profiles move. Field
 * names, required cardinalities and the exact system URIs below reflect the R4
 * base plus NRCES India profiles as understood at authoring time, and must be
 * checked against the current ABDM/NHCX implementation guides before go-live.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class FhirR4 {

    // ── System URIs. India-specific ones come from NRCES.
    public static final String SYS_ABHA        = "https://healthid.abdm.gov.in/ns/abha-number";
    public static final String SYS_ABHA_ADDR   = "https://healthid.abdm.gov.in/ns/abha-address";
    public static final String SYS_MRN         = "https://healthid.abdm.gov.in/ns/mrn";
    public static final String SYS_HFR         = "https://facility.abdm.gov.in/ns/facility-id";
    public static final String SYS_HPR         = "https://hpr.abdm.gov.in/ns/hpr-id";
    public static final String SYS_V2_0203     = "http://terminology.hl7.org/CodeSystem/v2-0203";
    public static final String SYS_CLAIM_TYPE  = "http://terminology.hl7.org/CodeSystem/claim-type";
    public static final String SYS_COVERAGE    = "http://terminology.hl7.org/CodeSystem/coverage-class";
    public static final String SYS_PROCESS_PRIORITY =
        "http://terminology.hl7.org/CodeSystem/processpriority";

    private FhirR4() {
    }

    // ── primitives ───────────────────────────────────────────────────────────

    public static Map<String, Object> coding(String system, String code, String display) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("system", system);
        m.put("code", code);
        if (display != null) {
            m.put("display", display);
        }
        return m;
    }

    public static Map<String, Object> codeableConcept(String system, String code, String display) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("coding", List.of(coding(system, code, display)));
        if (display != null) {
            m.put("text", display);
        }
        return m;
    }

    public static Map<String, Object> identifier(String system, String value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("system", system);
        m.put("value", value);
        return m;
    }

    /** A relative reference such as {@code Patient/abc-123}. */
    public static Map<String, Object> reference(String resourceType, String id) {
        return Map.of("reference", resourceType + "/" + id);
    }

    public static Map<String, Object> money(long amountMinorUnits, String currency) {
        Map<String, Object> m = new LinkedHashMap<>();
        // FHIR Money is decimal. Internal ledgers here are integer minor units.
        m.put("value", amountMinorUnits / 100.0);
        m.put("currency", currency == null ? "INR" : currency);
        return m;
    }

    public static Map<String, Object> period(String start, String end) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (start != null) {
            m.put("start", start);
        }
        if (end != null) {
            m.put("end", end);
        }
        return m;
    }

    // ── resources ────────────────────────────────────────────────────────────

    /**
     * Patient.
     *
     * <p>Callers pass already-decrypted values. Nothing here reaches a log; the
     * caller is responsible for keeping the assembled resource out of log lines
     * and out of anything sent to a model.
     */
    public static Map<String, Object> patient(String id, String abhaNumber, String abhaAddress,
                                              String mrn, String name, String gender,
                                              String birthDate, String phone) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("resourceType", "Patient");
        r.put("id", id);

        List<Map<String, Object>> ids = new ArrayList<>();
        if (abhaNumber != null) {
            ids.add(withType(identifier(SYS_ABHA, abhaNumber), "MR", "Medical record number"));
        }
        if (abhaAddress != null) {
            ids.add(identifier(SYS_ABHA_ADDR, abhaAddress));
        }
        if (mrn != null) {
            ids.add(withType(identifier(SYS_MRN, mrn), "MR", "Medical record number"));
        }
        if (!ids.isEmpty()) {
            r.put("identifier", ids);
        }

        if (name != null) {
            r.put("name", List.of(Map.of("text", name)));
        }
        if (gender != null) {
            r.put("gender", gender.toLowerCase(java.util.Locale.ROOT));
        }
        if (birthDate != null) {
            r.put("birthDate", birthDate);
        }
        if (phone != null) {
            r.put("telecom", List.of(Map.of("system", "phone", "value", phone, "use", "mobile")));
        }
        return r;
    }

    private static Map<String, Object> withType(Map<String, Object> identifier,
                                                String code, String display) {
        Map<String, Object> copy = new LinkedHashMap<>(identifier);
        copy.put("type", codeableConcept(SYS_V2_0203, code, display));
        return copy;
    }

    public static Map<String, Object> organization(String id, String hfrId, String name) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("resourceType", "Organization");
        r.put("id", id);
        if (hfrId != null) {
            r.put("identifier", List.of(withType(identifier(SYS_HFR, hfrId), "PRN",
                                                 "Provider number")));
        }
        r.put("name", name);
        return r;
    }

    public static Map<String, Object> practitioner(String id, String hprId, String name) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("resourceType", "Practitioner");
        r.put("id", id);
        if (hprId != null) {
            r.put("identifier", List.of(identifier(SYS_HPR, hprId)));
        }
        if (name != null) {
            r.put("name", List.of(Map.of("text", name)));
        }
        return r;
    }

    public static Map<String, Object> encounter(String id, String status, String classCode,
                                                String patientId, String start, String end) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("resourceType", "Encounter");
        r.put("id", id);
        r.put("status", status == null ? "finished" : status);
        r.put("class", coding("http://terminology.hl7.org/CodeSystem/v3-ActCode",
                              classCode == null ? "AMB" : classCode, null));
        r.put("subject", reference("Patient", patientId));
        Map<String, Object> p = period(start, end);
        if (!p.isEmpty()) {
            r.put("period", p);
        }
        return r;
    }

    public static Map<String, Object> coverage(String id, String patientId, String payerOrgId,
                                               String memberId, String policyNumber,
                                               String planName) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("resourceType", "Coverage");
        r.put("id", id);
        r.put("status", "active");
        if (memberId != null) {
            r.put("subscriberId", memberId);
        }
        r.put("beneficiary", reference("Patient", patientId));
        r.put("payor", List.of(reference("Organization", payerOrgId)));
        if (policyNumber != null || planName != null) {
            Map<String, Object> cls = new LinkedHashMap<>();
            cls.put("type", codeableConcept(SYS_COVERAGE, "plan", "Plan"));
            cls.put("value", policyNumber == null ? planName : policyNumber);
            if (planName != null) {
                cls.put("name", planName);
            }
            r.put("class", List.of(cls));
        }
        return r;
    }

    // ── bundles ──────────────────────────────────────────────────────────────

    /**
     * A message bundle, which is the shape NHCX expects.
     *
     * <p>{@code fullUrl} uses {@code urn:uuid:} form so entries reference each
     * other without depending on a resolvable server base.
     */
    public static Map<String, Object> messageBundle(String bundleId, String eventCode,
                                                    String senderId, String receiverId,
                                                    String correlationId,
                                                    List<Map<String, Object>> resources) {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("resourceType", "MessageHeader");
        header.put("id", UUID.randomUUID().toString());
        header.put("eventCoding", coding("https://nrces.in/ndhm/fhir/r4/CodeSystem/ndhm-message-events",
                                         eventCode, null));
        header.put("source", Map.of("endpoint", senderId));
        header.put("destination", List.of(Map.of("endpoint", receiverId)));
        if (!resources.isEmpty()) {
            header.put("focus", List.of(Map.of("reference",
                resources.get(0).get("resourceType") + "/" + resources.get(0).get("id"))));
        }

        List<Map<String, Object>> entries = new ArrayList<>();
        entries.add(entry(header));
        for (Map<String, Object> res : resources) {
            entries.add(entry(res));
        }

        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("resourceType", "Bundle");
        bundle.put("id", bundleId);
        bundle.put("type", "message");
        bundle.put("timestamp", java.time.Instant.now().toString());
        if (correlationId != null) {
            bundle.put("identifier", identifier("urn:ietf:rfc:3986", "urn:uuid:" + correlationId));
        }
        bundle.put("entry", entries);
        return bundle;
    }

    private static Map<String, Object> entry(Map<String, Object> resource) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("fullUrl", "urn:uuid:" + resource.get("id"));
        e.put("resource", resource);
        return e;
    }
}
