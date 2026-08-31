package com.hms.application.portal;

import com.hms.application.compliance.ConsentProvenance;
import com.hms.application.compliance.ConsentPurpose;
import com.hms.application.compliance.ConsentService;
import com.hms.api.patient.request.RegisterPatientRequest;
import com.hms.api.patient.response.PatientResponse;
import com.hms.application.patient.PatientManagementService;
import com.hms.domain.patient.model.Gender;
import com.hms.domain.patient.model.Patient;
import com.hms.infrastructure.persistence.patient.PatientJpaRepository;
import com.hms.infrastructure.persistence.portal.PortalPatientLookupRepository;
import com.hms.infrastructure.persistence.tenant.BranchEntity;
import com.hms.infrastructure.persistence.tenant.BranchJpaRepository;
import com.hms.security.encryption.PiiSearchTokenService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Creates a patient record from the app when the verified number matched nothing.
 *
 * <p>Delegates to {@link PatientManagementService#registerPatient} rather than
 * writing a {@code Patient} directly, so portal registrations get the same
 * patient-number sequence, the same duplicate check and the same HMAC token
 * maintenance as a front-desk registration. A parallel write path here would
 * drift from the desk within one release.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PortalRegistrationService {

    private final PatientManagementService patientService;
    private final PatientJpaRepository patientRepo;
    private final PortalPatientLookupRepository lookupRepo;
    private final BranchJpaRepository branchRepo;
    private final PiiSearchTokenService searchTokenService;
    private final PortalTenantScope tenantScope;
    private final PortalProperties properties;
    private final MeterRegistry meterRegistry;
    private final ConsentService consentService;

    public record SelfRegistration(
        UUID tenantId,
        UUID branchId,
        String salutation,
        String firstName,
        String lastName,
        Gender gender,
        LocalDate dateOfBirth,
        String mobile,
        String email,
        String bloodGroup,
        String address,
        String consentVersion) {}

    /**
     * @param verifiedContactToken HMAC token of the OTP-verified number. The
     *                             mobile in the payload must match it, so a
     *                             caller cannot verify their own number and then
     *                             register a patient against someone else's.
     */
    @Transactional
    public PatientResponse register(String verifiedContactToken, SelfRegistration input) {
        String submittedToken = searchTokenService.phoneToken(input.mobile());
        if (submittedToken == null || !submittedToken.equals(verifiedContactToken)) {
            log.warn("event=portal.registration.rejected reason=number_mismatch");
            throw new PortalException(
                PortalErrorCode.VALIDATION_FAILED, "portal.registration.number_mismatch");
        }

        enforceRegistrationCap(verifiedContactToken);

        BranchEntity branch = branchRepo.findById(input.branchId())
            .filter(b -> input.tenantId().equals(b.getTenantId()))
            .filter(b -> b.getStatus() == 1)
            .orElseThrow(() -> new PortalException(
                PortalErrorCode.VALIDATION_FAILED, "portal.registration.invalid_branch"));

        // The caller holds an identity token and therefore has no tenant
        // context. Without entering one explicitly, AuditableEntity's
        // @PrePersist would stamp a null tenant_id and the new patient would be
        // invisible to every tenant-filtered query afterwards.
        PatientResponse response = tenantScope.call(input.tenantId(), branch.getId(), () -> {
            RegisterPatientRequest request = new RegisterPatientRequest(
                input.salutation(),
                input.firstName(),
                input.lastName(),
                input.gender(),
                input.dateOfBirth(),
                // estimatedDateOfBirth is @NotNull on the staff request; the
                // portal always collects a real date of birth, so the estimate
                // is the same value rather than a second question the patient
                // would not understand.
                input.dateOfBirth(),
                input.mobile(),
                input.email(),
                input.bloodGroup(),
                input.address(),
                null,       // primaryProviderId — chosen later, at booking
                null,       // areaId
                null,       // categoryId
                false,      // isClinicalTrial
                false);     // createEncounter — registering is not arriving

            PatientResponse created = patientService.registerPatient(request);

            // Set after creation because RegisterPatientRequest has no such
            // field and widening the staff-facing request record for a portal
            // concern would put a portal flag on every desk registration form.
            markSelfRegistered(created.id());
            return created;
        });

        // WO-023 / S2-03. This used to end here: the consent version was written
        // to the log line below and then dropped. The patient agreed to
        // something the system never stored, so there was no record consent had
        // been given and — worse — nothing for them to withdraw.
        //
        // PATIENT_DIGITAL, not STAFF_ATTESTED: the patient ticked the box
        // themselves in the app. No staff member attested to anything, so
        // capturedBy is legitimately null here, which is why the provenance
        // matters. Under the Fiduciary/Processor split confirmed 2026-08-30 this
        // is a purpose the platform holds as Fiduciary in its own right.
        try {
            consentService.grant(
                response.id(), ConsentPurpose.PORTAL_SELF_ACCESS,
                properties.getConsentVersion(), "en",
                ConsentPurpose.PORTAL_SELF_ACCESS.getNoticeSummary(),
                "PORTAL", null, false, false, null,
                ConsentProvenance.PATIENT_DIGITAL);
        } catch (RuntimeException e) {
            // A registration that succeeded must not be rolled back because the
            // consent write failed — the patient exists and their session is
            // about to start. But an unrecorded consent is a compliance gap, so
            // it is logged at ERROR and metered rather than swallowed.
            log.error("event=portal.registration.consent_failed patient_id={} error_type={}",
                      response.id(), e.getClass().getSimpleName());
            meterRegistry.counter("hms_portal_consent_failures_total").increment();
        }

        log.info("event=portal.registration.created patient_id={} tenant_id={} consent_version={}",
            response.id(), input.tenantId(), properties.getConsentVersion());
        meterRegistry.counter("hms_portal_registrations_total", "outcome", "created").increment();

        return response;
    }

    private void markSelfRegistered(UUID patientId) {
        Optional<Patient> patient = patientRepo.findById(patientId);
        if (patient.isEmpty()) {
            log.warn("event=portal.registration.flag_skipped patient_id={}", patientId);
            return;
        }
        patient.get().setSelfRegistered(true);
        patientRepo.save(patient.get());
    }

    /**
     * Caps registrations per number across all tenants.
     *
     * <p>Counted on the HMAC token via the cross-tenant lookup, because the
     * limit exists to stop one number registering fifty patients — and a
     * per-tenant count would be trivially defeated by registering once at each
     * hospital.
     *
     * <p>A family sharing one number is the legitimate case this must not break,
     * which is why the default is 3 rather than 1.
     */
    private void enforceRegistrationCap(String contactNumberToken) {
        long existing = lookupRepo
            .findIdsByContactNumberTokenAcrossTenants(contactNumberToken).size();

        if (existing >= properties.getMaxSelfRegistrationsPerNumber()) {
            log.warn("event=portal.registration.rejected reason=cap_reached count={}", existing);
            meterRegistry.counter("hms_portal_registrations_total", "outcome", "cap_reached")
                .increment();
            throw new PortalException(
                PortalErrorCode.REGISTRATION_CAP_REACHED, "portal.registration.cap_reached");
        }
    }
}
