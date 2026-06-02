package com.hms.application.patient;
import com.hms.api.patient.request.*;
import com.hms.api.patient.response.PatientResponse;
import com.hms.domain.billing.model.DocumentType;
import com.hms.domain.patient.model.Patient;
import com.hms.domain.shared.port.out.SequenceNumberPort;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.mapper.PatientMapper;
import com.hms.infrastructure.persistence.patient.PatientJpaRepository;
import com.hms.infrastructure.sequence.NumberSequenceJpaRepository;
import com.hms.infrastructure.sequence.NumberSequenceEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
@Service @RequiredArgsConstructor
public class PatientManagementService {
    private final PatientJpaRepository patientRepo;
    private final PatientMapper patientMapper;
    private final SequenceNumberPort sequencePort;
    private final NumberSequenceJpaRepository numberSequenceRepo;
    private final com.hms.infrastructure.persistence.encounter.ClinicalEncounterJpaRepository encounterRepo;
    private final com.hms.application.encounter.EncounterManagementService encounterService;

    private PatientResponse enrichWithEncounter(PatientResponse resp) {
        var activeEnc = encounterRepo.findActiveInpatientByPatientId(resp.id()).stream().findFirst();
        return new PatientResponse(
            resp.id(), resp.patientNumber(), resp.salutation(), resp.firstName(), resp.lastName(),
            resp.fullName(), resp.gender(), resp.dateOfBirth(), resp.estimatedDateOfBirth(),
            resp.age(), resp.contactNumber(), resp.email(), resp.bloodGroup(), resp.address(), resp.primaryProviderId(),
            resp.areaId(), resp.categoryId(), resp.isClinicalTrial(), resp.status(),
            activeEnc.isPresent(), activeEnc.map(com.hms.domain.encounter.model.ClinicalEncounter::getId).orElse(null)
        );
    }

    private String resolvePatientNumber(UUID id) {
        return numberSequenceRepo.findById(id)
            .map(NumberSequenceEntity::getValue)
            .orElse("NEW");
    }

    @Transactional
    public PatientResponse registerPatient(RegisterPatientRequest req) {
        Patient patient = patientMapper.fromRegisterRequest(req);
        Patient saved = patientRepo.save(patient);

        // Link patient number via OneToOne NumberSequence pattern.
        // NumberSequence.typeId = patient.id (same UUID — the join key).
        // Mirrors: Patient.patientNo @OneToOne @JoinColumn(name="id")
        String patientNo = sequencePort.generateNext(DocumentType.PATIENT);
        NumberSequenceEntity seq = new NumberSequenceEntity();
        seq.setId(saved.getId());          // same UUID as patient PK — this IS the join column
        seq.setValue(patientNo);
        seq.setTypeId(saved.getId());
        numberSequenceRepo.save(seq);
        
        if (req.createEncounter()) {
            var encounterReq = new com.hms.api.encounter.request.CreateEncounterRequest(
                saved.getId(),
                req.primaryProviderId(),
                null, // appointmentId
                com.hms.domain.encounter.model.VisitMode.WALK_IN
            );
            encounterService.createOutpatientEncounter(encounterReq);
        }

        return enrichWithEncounter(patientMapper.toResponse(saved, patientNo));
    }

    @Transactional
    public PatientResponse updatePatient(UUID patientId, UpdatePatientRequest req) {
        Patient patient = patientRepo.findById(patientId)
            .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));
        patientMapper.applyUpdateRequest(req, patient);
        Patient saved = patientRepo.save(patient);
        return enrichWithEncounter(patientMapper.toResponse(saved, resolvePatientNumber(saved.getId())));
    }

    @Transactional(readOnly = true)
    public PatientResponse findById(UUID patientId) {
        Patient patient = patientRepo.findById(patientId)
            .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));
        return enrichWithEncounter(patientMapper.toResponse(patient, resolvePatientNumber(patient.getId())));
    }

    @Transactional(readOnly = true)
    public Page<PatientResponse> searchPatients(String query, Pageable pageable) {
        return patientRepo.searchByNameOrContact(query, pageable)
            .map(p -> enrichWithEncounter(patientMapper.toResponse(p, resolvePatientNumber(p.getId()))));
    }

    @Transactional
    public void toggleClinicalTrial(UUID patientId) {
        Patient patient = patientRepo.findById(patientId)
            .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));
        patient.toggleClinicalTrial();
        patientRepo.save(patient);
    }
}
