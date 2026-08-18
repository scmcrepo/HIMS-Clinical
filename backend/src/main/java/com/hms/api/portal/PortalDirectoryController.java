package com.hms.api.portal;

import com.hms.api.patient.response.PatientResponse;
import com.hms.api.portal.request.PortalRequests;
import com.hms.api.portal.response.PortalResponses;
import com.hms.api.shared.ApiResponse;
import com.hms.application.portal.PortalDirectoryService;
import com.hms.application.portal.PortalRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The pre-tenant surface: what a verified-but-unregistered patient can see and
 * do (WO-017 / PT-006).
 *
 * <p>A separate class from {@link PortalAuthController} because the paths differ
 * at the first segment — {@code /portal/hospitals} and
 * {@code /portal/patients/register} rather than {@code /portal/auth/...} —
 * and a single controller cannot carry two base paths without the kind of
 * relative-path trickery that breaks silently.
 *
 * <p>Everything here runs on the identity scope: the caller has proved they hold
 * a mobile number and nothing more. No clinical data is reachable from this
 * controller, by construction rather than by check.
 */
@RestController
@RequestMapping("/portal")
@RequiredArgsConstructor
public class PortalDirectoryController {

    private final PortalDirectoryService directoryService;
    private final PortalRegistrationService registrationService;

    /**
     * Hospitals on the platform, for a patient with no existing records.
     *
     * <p>Cross-tenant by nature and safe to be: it returns only what a hospital
     * already publishes about itself — name, address, public phone number. No
     * patient data passes through here.
     */
    @GetMapping("/hospitals")
    @PreAuthorize("hasAuthority('PORTAL_IDENTITY')")
    public ResponseEntity<ApiResponse<List<PortalResponses.HospitalCandidate>>> hospitals() {
        return ResponseEntity.ok(ApiResponse.ok(
            "Hospitals", directoryService.listActiveHospitals()));
    }

    @GetMapping("/hospitals/{tenantId}/branches")
    @PreAuthorize("hasAuthority('PORTAL_IDENTITY')")
    public ResponseEntity<ApiResponse<List<PortalResponses.BranchSummary>>> branches(
            @PathVariable UUID tenantId) {
        return ResponseEntity.ok(ApiResponse.ok(
            "Branches", directoryService.listActiveBranches(tenantId)));
    }

    @PostMapping("/patients/register")
    @PreAuthorize("hasAuthority('PORTAL_IDENTITY')")
    public ResponseEntity<ApiResponse<PatientResponse>> register(
            @Valid @RequestBody PortalRequests.SelfRegister body) {

        PatientResponse created = registrationService.register(
            PortalAuthController.verifiedContactToken(),
            new PortalRegistrationService.SelfRegistration(
                body.tenantId(), body.branchId(), body.salutation(),
                body.firstName(), body.lastName(), body.gender(), body.dateOfBirth(),
                body.mobile(), body.email(), body.bloodGroup(), body.address(),
                body.consentVersion()));

        return ResponseEntity.ok(ApiResponse.ok("Registered", created));
    }
}
