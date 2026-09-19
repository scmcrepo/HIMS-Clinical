package com.hms.application.patient;

import com.hms.api.encounter.request.CreateEncounterRequest;
import com.hms.api.patient.request.*;
import com.hms.api.patient.response.PatientResponse;
import com.hms.domain.billing.model.DocumentType;
import com.hms.domain.encounter.model.VisitMode;
import com.hms.domain.patient.model.Patient;
import com.hms.domain.shared.port.out.SequenceNumberPort;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.mapper.PatientMapper;
import com.hms.infrastructure.persistence.patient.PatientJpaRepository;
import com.hms.infrastructure.sequence.NumberSequenceJpaRepository;
import com.hms.infrastructure.sequence.NumberSequenceEntity;
import com.hms.security.encryption.PiiSearchTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;
import com.hms.domain.patient.model.Gender;

/**
 * Patient management service — registration, update, lookup.
 *
 * Encryption notes:
 *   - All PII fields are transparently encrypted by EncryptedStringConverter.
 *   - contactNumberToken is maintained here so phone-based DB lookup remains possible
 *     without decrypting (HMAC token via PiiSearchTokenService).
 *   - Text search is delegated to PatientSearchService (application-layer decryption).
 */
@Service
@RequiredArgsConstructor
public class PatientManagementService {

    private final PatientJpaRepository patientRepo;
    private final PatientMapper patientMapper;
    private final SequenceNumberPort sequencePort;
    private final NumberSequenceJpaRepository numberSequenceRepo;
    private final com.hms.infrastructure.persistence.encounter.ClinicalEncounterJpaRepository encounterRepo;
    private final com.hms.application.encounter.EncounterManagementService encounterService;
    private final PiiSearchTokenService searchTokenService;
    private final PatientSearchService patientSearchService;

    // ── Registration ──────────────────────────────────────────────────────────

    @Transactional
    public PatientResponse registerPatient(RegisterPatientRequest req) {
        validateSalutationAndAge(req.salutation(), req.gender(), req.estimatedDateOfBirth());

        if (req.contactNumber() != null && !req.contactNumber().isBlank()) {
            String token = searchTokenService.phoneToken(req.contactNumber().trim());
            java.util.List<Patient> existingPatients = patientRepo.findByContactNumberToken(token);
            for (Patient p : existingPatients) {
                if (req.firstName().trim().equalsIgnoreCase(p.getFirstName()) && 
                    (req.lastName() == null || req.lastName().isBlank() ? 
                        (p.getLastName() == null || p.getLastName().isBlank()) : 
                        req.lastName().trim().equalsIgnoreCase(p.getLastName()))) {
                    throw new com.hms.exception.BusinessRuleViolationException("A patient with the same name and contact number already exists.");
                }
            }
        }

        Patient patient = patientMapper.fromRegisterRequest(req);

        // Maintain HMAC token for phone-based lookup
        if (req.contactNumber() != null && !req.contactNumber().isBlank()) {
            patient.setContactNumberToken(searchTokenService.phoneToken(req.contactNumber().trim()));
        }

        Patient saved = patientRepo.save(patient);

        String patientNo = sequencePort.generateNext(DocumentType.PATIENT);
        NumberSequenceEntity seq = new NumberSequenceEntity();
        seq.setId(saved.getId());
        seq.setValue(patientNo);
        seq.setTypeId(saved.getId());
        numberSequenceRepo.save(seq);

        if (req.createEncounter()) {
            encounterService.createOutpatientEncounter(new CreateEncounterRequest(
                saved.getId(), req.primaryProviderId(), null, VisitMode.WALK_IN));
        }

        return enrichWithEncounter(patientMapper.toResponse(saved, patientNo));
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Transactional
    public PatientResponse updatePatient(UUID patientId, UpdatePatientRequest req) {
        Patient patient = patientRepo.findById(patientId)
            .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));

        if (req.contactNumber() != null && !req.contactNumber().isBlank()) {
            String token = searchTokenService.phoneToken(req.contactNumber().trim());
            java.util.List<Patient> existingPatients = patientRepo.findByContactNumberToken(token);
            for (Patient p : existingPatients) {
                if (!p.getId().equals(patientId) &&
                    req.firstName().trim().equalsIgnoreCase(p.getFirstName()) && 
                    (req.lastName() == null || req.lastName().isBlank() ? 
                        (p.getLastName() == null || p.getLastName().isBlank()) : 
                        req.lastName().trim().equalsIgnoreCase(p.getLastName()))) {
                    throw new com.hms.exception.BusinessRuleViolationException("A patient with the same name and contact number already exists.");
                }
            }
        }

        validateSalutationAndAge(
            req.salutation() != null ? req.salutation() : patient.getSalutation(),
            req.gender() != null ? req.gender() : patient.getGender(),
            req.estimatedDateOfBirth() != null ? req.estimatedDateOfBirth() : patient.getEstimatedDateOfBirth()
        );

        patientMapper.applyUpdateRequest(req, patient);

        // Re-compute token if contact number changed
        if (req.contactNumber() != null) {
            patient.setContactNumberToken(searchTokenService.phoneToken(req.contactNumber()));
        }

        Patient saved = patientRepo.save(patient);
        return enrichWithEncounter(patientMapper.toResponse(saved, resolvePatientNumber(saved.getId())));
    }

    // ── Lookup ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PatientResponse findById(UUID patientId) {
        Patient patient = patientRepo.findById(patientId)
            .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));
        return enrichWithEncounter(patientMapper.toResponse(patient, resolvePatientNumber(patient.getId())));
    }

    /**
     * Delegates to PatientSearchService which handles encrypted-field search
     * (patient number via SQL, phone via HMAC token, name via in-memory decryption).
     */
    @Transactional(readOnly = true)
    public Page<PatientResponse> searchPatients(String query, Pageable pageable) {
        return patientSearchService.search(query, pageable);
    }

    @Transactional
    public void toggleClinicalTrial(UUID patientId) {
        Patient patient = patientRepo.findById(patientId)
            .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));
        patient.toggleClinicalTrial();
        patientRepo.save(patient);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void validateSalutationAndAge(String salutation, Gender gender, LocalDate dob) {
        if (salutation == null || salutation.isBlank() || dob == null) {
            return;
        }
        String sal = salutation.trim();
        int age = java.time.Period.between(dob, LocalDate.now()).getYears();

        if ("Baby".equalsIgnoreCase(sal)) {
            if (age > 5) {
                throw new com.hms.exception.BusinessRuleViolationException(
                    "Salutation 'Baby' is only applicable for children aged 5 years and below (current age: " + age + " years).");
            }
        } else if ("Master".equalsIgnoreCase(sal)) {
            if (gender != null && gender != Gender.MALE) {
                throw new com.hms.exception.BusinessRuleViolationException(
                    "Salutation 'Master' is only applicable for male patients.");
            }
            if (age >= 18) {
                throw new com.hms.exception.BusinessRuleViolationException(
                    "Salutation 'Master' is only applicable for minors under 18 years (current age: " + age + " years, please use 'Mr').");
            }
        } else if ("Mr".equalsIgnoreCase(sal)) {
            if (gender != null && gender != Gender.MALE) {
                throw new com.hms.exception.BusinessRuleViolationException(
                    "Salutation 'Mr' is only applicable for male patients.");
            }
            if (age < 18) {
                throw new com.hms.exception.BusinessRuleViolationException(
                    "Salutation 'Mr' is only applicable for adults aged 18 years and above (current age: " + age + " years, please use 'Baby' or 'Master').");
            }
        } else if ("Mrs".equalsIgnoreCase(sal) || "Ms".equalsIgnoreCase(sal)) {
            if (gender != null && gender != Gender.FEMALE) {
                throw new com.hms.exception.BusinessRuleViolationException(
                    "Salutation '" + sal + "' is only applicable for female patients.");
            }
            if (age < 18) {
                throw new com.hms.exception.BusinessRuleViolationException(
                    "Salutation '" + sal + "' is only applicable for adults aged 18 years and above (current age: " + age + " years, please use 'Baby').");
            }
        } else if ("Dr".equalsIgnoreCase(sal)) {
            if (age < 21) {
                throw new com.hms.exception.BusinessRuleViolationException(
                    "Salutation 'Dr' is only applicable for adults aged 21 years and above (current age: " + age + " years).");
            }
        }
    }

    private PatientResponse enrichWithEncounter(PatientResponse resp) {
        var activeEnc = encounterRepo.findActiveInpatientByPatientId(resp.id()).stream().findFirst();
        return new PatientResponse(
            resp.id(), resp.patientNumber(), resp.salutation(), resp.firstName(), resp.lastName(),
            resp.fullName(), resp.gender(), resp.dateOfBirth(), resp.estimatedDateOfBirth(),
            resp.age(), resp.contactNumber(), resp.email(), resp.bloodGroup(), resp.address(),
            resp.primaryProviderId(), resp.areaId(), resp.categoryId(), resp.isClinicalTrial(),
            resp.status(),
            activeEnc.isPresent(),
            activeEnc.map(com.hms.domain.encounter.model.ClinicalEncounter::getId).orElse(null)
        );
    }

    private String resolvePatientNumber(UUID id) {
        return numberSequenceRepo.findById(id)
            .map(NumberSequenceEntity::getValue)
            .orElse("NEW");
    }
}
