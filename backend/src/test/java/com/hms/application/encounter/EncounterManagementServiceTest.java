package com.hms.application.encounter;

import com.hms.api.encounter.request.CreateEncounterRequest;
import com.hms.api.encounter.response.EncounterResponse;
import com.hms.application.bed.BedManagementService;
import com.hms.application.billing.BillingOperationsService;
import com.hms.application.patient.PatientSearchService;
import com.hms.domain.billing.model.EncounterType;
import com.hms.domain.encounter.model.ClinicalEncounter;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.infrastructure.mapper.EncounterMapper;
import com.hms.infrastructure.persistence.billing.BillJpaRepository;
import com.hms.infrastructure.persistence.consultant.ConsultantJpaRepository;
import com.hms.infrastructure.persistence.encounter.ClinicalEncounterJpaRepository;
import com.hms.infrastructure.persistence.patient.PatientJpaRepository;
import com.hms.infrastructure.sequence.NumberSequenceJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EncounterManagementServiceTest {

    @Mock private ClinicalEncounterJpaRepository encounterRepo;
    @Mock private EncounterMapper encounterMapper;
    @Mock private PatientJpaRepository patientRepo;
    @Mock private ConsultantJpaRepository consultantRepo;
    @Mock private NumberSequenceJpaRepository numberSequenceRepo;
    @Mock private BillJpaRepository billRepo;
    @Mock private BillingOperationsService billingService;
    @Mock private BedManagementService bedService;
    @Mock private ApplicationContext applicationContext;
    @Mock private PatientSearchService patientSearchService;

    @InjectMocks
    private EncounterManagementService encounterService;

    private UUID patientId;
    private UUID providerId;
    private UUID encounterId;

    @BeforeEach
    void setUp() {
        patientId = UUID.randomUUID();
        providerId = UUID.randomUUID();
        encounterId = UUID.randomUUID();
    }

    @Test
    void createOutpatientEncounter_ShouldSaveAndReturnResponse() {
        CreateEncounterRequest req = new CreateEncounterRequest(patientId, providerId, null, null);
        ClinicalEncounter savedEncounter = new ClinicalEncounter();
        savedEncounter.setId(encounterId);
        savedEncounter.setPatientId(patientId);

        when(encounterRepo.save(any(ClinicalEncounter.class))).thenReturn(savedEncounter);
        
        EncounterResponse mockResponse = new EncounterResponse(
                encounterId, patientId, "P-123", "John Doe", providerId, null, EncounterType.OUTPATIENT, null, null, null, null, null, null, false, false, false, null, null, null, null
        );
        when(encounterMapper.toResponse(any(ClinicalEncounter.class), anyString(), anyString())).thenReturn(mockResponse);
        when(patientRepo.findById(patientId)).thenReturn(Optional.empty());

        EncounterResponse result = encounterService.createOutpatientEncounter(req);

        assertNotNull(result);
        verify(encounterRepo).save(any(ClinicalEncounter.class));
    }

    @Test
    void createInpatientEncounter_ShouldThrow_WhenActiveInpatientExists() {
        CreateEncounterRequest req = new CreateEncounterRequest(patientId, providerId, null, null);
        
        ClinicalEncounter existing = new ClinicalEncounter();
        when(encounterRepo.findActiveInpatientByPatientId(patientId)).thenReturn(List.of(existing));

        assertThrows(BusinessRuleViolationException.class, () -> encounterService.createInpatientEncounter(req));
    }

    @Test
    void createInpatientEncounter_ShouldSaveAndReturnResponse() {
        CreateEncounterRequest req = new CreateEncounterRequest(patientId, providerId, null, null);
        ClinicalEncounter savedEncounter = new ClinicalEncounter();
        savedEncounter.setId(encounterId);
        savedEncounter.setPatientId(patientId);

        when(encounterRepo.findActiveInpatientByPatientId(patientId)).thenReturn(List.of());
        when(encounterRepo.save(any(ClinicalEncounter.class))).thenReturn(savedEncounter);
        
        EncounterResponse mockResponse = new EncounterResponse(
                encounterId, patientId, "P-123", "John Doe", providerId, null, EncounterType.INPATIENT, null, null, null, null, null, null, false, false, false, null, null, null, null
        );
        when(encounterMapper.toResponse(any(ClinicalEncounter.class), anyString(), anyString())).thenReturn(mockResponse);

        EncounterResponse result = encounterService.createInpatientEncounter(req);

        assertNotNull(result);
        verify(encounterRepo).save(any(ClinicalEncounter.class));
    }
}
