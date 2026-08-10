package com.hms.api.abha;

import com.hms.api.abha.request.StartAbhaEnrolmentRequest;
import com.hms.api.abha.request.VerifyByDemographicsRequest;
import com.hms.api.abha.request.VerifyAbhaOtpRequest;
import com.hms.api.abha.response.AbhaLinkageResponse;
import com.hms.api.shared.ApiResponse;
import com.hms.application.abha.AbhaService;
import com.hms.security.SpringSecurityAuditorAware;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
import java.util.UUID;

/**
 * ABHA verification and creation — Screen 1.1.
 *
 * <p>Front-desk staff reach this on the normal session-authenticated chain.
 * Agents reach ABHA through the separate agent gateway with the narrower
 * {@code AGENT_ABHA_WRITE} scope, deliberately not through here.
 *
 * <p>Responses carry a masked ABHA number. Nothing on this controller returns
 * an Aadhaar number, and nothing accepts one back from the client after the
 * initial OTP request.
 */
@RestController
@RequestMapping("/abha")
@RequiredArgsConstructor
@PreAuthorize("hasPermission('ABHA_MANAGE','')")
public class AbhaController {

    private final AbhaService service;

    /** Same actor-resolution rule that stamps created_by on every audited row. */
    private final SpringSecurityAuditorAware auditor;

    /** Send an OTP to begin enrolment. */
    @PostMapping("/enrolment")
    public ResponseEntity<ApiResponse<AbhaLinkageResponse>> start(
            @Valid @RequestBody StartAbhaEnrolmentRequest req) {

        AbhaLinkageResponse body = AbhaLinkageResponse.from(
            service.startEnrolment(req.patientId(), req.channel(), req.loginId()));

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("OTP sent", body));
    }

    /** Verify the OTP and attach the ABHA identity to the patient. */
    @PostMapping("/enrolment/{linkageId}/verify")
    public ResponseEntity<ApiResponse<AbhaLinkageResponse>> verify(
            @PathVariable UUID linkageId,
            @Valid @RequestBody VerifyAbhaOtpRequest req) {

        AbhaLinkageResponse body = AbhaLinkageResponse.from(
            service.verifyOtp(linkageId, req.otp(), req.mobile()));

        return ResponseEntity.ok(ApiResponse.ok("ABHA linked", body));
    }

    /**
     * Verify by Aadhaar demographics when no OTP can be delivered.
     *
     * <p>Weaker assurance than the OTP route, so it is a separate endpoint
     * rather than a flag on the existing one — a reviewer reading the access
     * log can see which route was taken without decoding a request body.
     */
    @PostMapping("/enrolment/{linkageId}/verify-demographics")
    public ResponseEntity<ApiResponse<AbhaLinkageResponse>> verifyByDemographics(
            @PathVariable UUID linkageId,
            @Valid @RequestBody VerifyByDemographicsRequest req) {

        AbhaLinkageResponse body = AbhaLinkageResponse.from(
            service.verifyByDemographics(linkageId, req.aadhaar(), req.name(),
                                         req.gender(), req.yearOfBirth()));

        return ResponseEntity.ok(ApiResponse.ok("ABHA linked", body));
    }

    /**
     * Download the patient's ABHA card.
     *
     * <p>Guarded by {@code ABHA_CARD_VIEW}, not {@code ABHA_MANAGE}: this is the
     * one endpoint that reveals the unmasked national health ID, and the clerk
     * who links an identity at registration does not need to see it. Every call
     * is written to the disclosure audit, successes and failures alike.
     *
     * <p>{@code Cache-Control: no-store} because a national health ID should not
     * sit in a browser or proxy cache after the tab closes.
     *
     * <p>Note the method-level {@code @PreAuthorize} <b>replaces</b> the
     * class-level {@code ABHA_MANAGE} check rather than adding to it. That is
     * intended: viewing the card is its own permission, held by clinicians who
     * may have no reason to create or link identities at all. Requiring both
     * would couple two unrelated jobs.
     */
    @GetMapping("/patient/{patientId}/card")
    @PreAuthorize("hasPermission('ABHA_CARD_VIEW','')")
    public ResponseEntity<Resource> downloadCard(
            @PathVariable UUID patientId,
            @RequestParam(required = false) String purpose) {

        byte[] pdf = service.downloadCard(patientId, auditor.getCurrentAuditor().orElse(null),
                                          purpose);

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"abha-card.pdf\"")
            .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate")
            .header(HttpHeaders.PRAGMA, "no-cache")
            .body(new ByteArrayResource(pdf));
    }

    /** Whether an ABHA address is free, for the address-suggestion field. */
    @GetMapping("/address-available")
    public ResponseEntity<ApiResponse<Boolean>> addressAvailable(
            @RequestParam String abhaAddress) {
        return ResponseEntity.ok(
            ApiResponse.of(service.abhaAddressAvailable(abhaAddress)));
    }

    /** Every linkage attempt for a patient, newest first. */
    @GetMapping("/patient/{patientId}")
    public ResponseEntity<ApiResponse<List<AbhaLinkageResponse>>> history(
            @PathVariable UUID patientId) {

        List<AbhaLinkageResponse> body = service.historyFor(patientId).stream()
            .map(AbhaLinkageResponse::from)
            .toList();

        return ResponseEntity.ok(ApiResponse.of(body));
    }

    /**
     * The patient's active ABHA identity, or 204 if there is none.
     *
     * <p>Drives the verified badge on the patient master. 204 rather than 404
     * because "this patient has no ABHA" is an ordinary state, not a missing
     * resource, and the badge component should not treat it as an error.
     */
    @GetMapping("/patient/{patientId}/linked")
    public ResponseEntity<ApiResponse<AbhaLinkageResponse>> linked(
            @PathVariable UUID patientId) {

        var linkage = service.linkedFor(patientId);
        if (linkage == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(ApiResponse.of(AbhaLinkageResponse.from(linkage)));
    }
}
