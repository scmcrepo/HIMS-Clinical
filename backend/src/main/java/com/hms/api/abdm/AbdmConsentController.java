package com.hms.api.abdm;

import com.hms.api.abdm.request.ConsentRequestCmd;
import com.hms.api.abdm.response.ConsentRequestResponse;
import com.hms.api.abdm.response.ExternalRecordResponse;
import com.hms.api.shared.ApiResponse;
import com.hms.application.abdm.AbdmConsentService;
import com.hms.security.SpringSecurityAuditorAware;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ABDM consent and external health records — Screens 3.1 and 3.2.
 *
 * <p>Two permissions, not one. Asking a patient for their history from other
 * hospitals and reading that history are different acts: a clinician may hold
 * {@code ABDM_RECORDS_VIEW} for records already granted without being able to
 * raise new consent requests.
 */
@RestController
@RequestMapping("/abdm")
@RequiredArgsConstructor
public class AbdmConsentController {

    private final AbdmConsentService service;
    private final SpringSecurityAuditorAware auditor;

    /** Raise a consent request — Screen 3.1. */
    @PostMapping("/consent-requests")
    @PreAuthorize("hasPermission('ABDM_CONSENT_REQUEST','')")
    public ResponseEntity<ApiResponse<ConsentRequestResponse>> request(
            @Valid @RequestBody ConsentRequestCmd cmd) {

        var saved = service.requestConsent(cmd.patientId(), cmd.encounterId(), cmd.purposeCode(),
                                           cmd.hiTypes(), cmd.dateRangeFrom(), cmd.dateRangeTo(),
                                           cmd.expiresAt(), auditor.getCurrentAuditor().orElse(null));

        return ResponseEntity.accepted().body(ApiResponse.ok(
            "Consent request sent to the patient", ConsentRequestResponse.from(saved)));
    }

    /** Consent requests for a patient, newest first. */
    @GetMapping("/consent-requests/patient/{patientId}")
    @PreAuthorize("hasPermission('ABDM_CONSENT_REQUEST','')")
    public ResponseEntity<ApiResponse<List<ConsentRequestResponse>>> requests(
            @PathVariable UUID patientId) {

        List<ConsentRequestResponse> body = service.requestsFor(patientId).stream()
            .map(ConsentRequestResponse::from)
            .toList();
        return ResponseEntity.ok(ApiResponse.of(body));
    }

    /** Pull records under a granted artifact. */
    @PostMapping("/artifacts/{artifactRowId}/fetch")
    @PreAuthorize("hasPermission('ABDM_RECORDS_VIEW','')")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> fetch(
            @PathVariable UUID artifactRowId) {

        int count = service.fetchRecords(artifactRowId,
                                         auditor.getCurrentAuditor().orElse(null)).size();
        return ResponseEntity.ok(ApiResponse.ok("Records retrieved", Map.of("count", count)));
    }

    /**
     * The external records index — Screen 3.2.
     *
     * <p>Returns metadata only. Records whose consent has expired or been
     * revoked are absent, because a time-boxed grant that keeps showing records
     * after it ends is not time-boxed.
     */
    @GetMapping("/records/patient/{patientId}")
    @PreAuthorize("hasPermission('ABDM_RECORDS_VIEW','')")
    public ResponseEntity<ApiResponse<List<ExternalRecordResponse>>> records(
            @PathVariable UUID patientId) {

        List<ExternalRecordResponse> body = service.recordsFor(patientId).stream()
            .map(ExternalRecordResponse::from)
            .toList();
        return ResponseEntity.ok(ApiResponse.of(body));
    }

    /** Open one record. Re-checks consent and writes a disclosure audit row. */
    @GetMapping("/records/{recordId}")
    @PreAuthorize("hasPermission('ABDM_RECORDS_VIEW','')")
    public ResponseEntity<ApiResponse<Map<String, String>>> openRecord(
            @PathVariable UUID recordId) {

        var record = service.openRecord(recordId, auditor.getCurrentAuditor().orElse(null));
        return ResponseEntity.ok(ApiResponse.of(Map.of(
            "hiType", record.getHiType(),
            "sourceHipName", record.getSourceHipName() == null ? "" : record.getSourceHipName(),
            "payload", record.getPayload() == null ? "" : record.getPayload())));
    }

    /** Copy a record into the local case sheet. */
    @PostMapping("/records/{recordId}/import")
    @PreAuthorize("hasPermission('ABDM_RECORDS_VIEW','')")
    public ResponseEntity<ApiResponse<ExternalRecordResponse>> importRecord(
            @PathVariable UUID recordId,
            @RequestParam UUID caseSheetId) {

        var updated = service.markImported(recordId, caseSheetId,
                                           auditor.getCurrentAuditor().orElse(null));
        return ResponseEntity.ok(ApiResponse.ok("Imported into the case sheet",
                                                ExternalRecordResponse.from(updated)));
    }
}
