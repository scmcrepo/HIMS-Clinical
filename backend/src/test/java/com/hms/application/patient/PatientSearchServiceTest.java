package com.hms.application.patient;

import com.hms.api.patient.response.PatientResponse;
import com.hms.domain.patient.model.Patient;
import com.hms.infrastructure.mapper.PatientMapper;
import com.hms.infrastructure.persistence.patient.PatientJpaRepository;
import com.hms.infrastructure.sequence.NumberSequenceEntity;
import com.hms.infrastructure.sequence.NumberSequenceJpaRepository;
import com.hms.security.encryption.PiiSearchTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PatientSearchServiceTest {

    @Mock private PatientJpaRepository patientRepo;
    @Mock private PatientMapper patientMapper;
    @Mock private PiiSearchTokenService tokenService;
    @Mock private NumberSequenceJpaRepository numberSequenceRepo;

    @InjectMocks
    private PatientSearchService patientSearchService;

    private Patient patient;

    @BeforeEach
    void setUp() {
        patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setFirstName("TestName");
    }

    @Test
    void search_ShouldReturnAll_WhenQueryEmpty() {
        Page<Patient> page = new PageImpl<>(List.of(patient));
        when(patientRepo.findAllActive(any(Pageable.class))).thenReturn(page);
        
        when(patientMapper.toResponse(any(Patient.class), anyString())).thenReturn(
            org.mockito.Mockito.mock(PatientResponse.class)
        );

        Page<PatientResponse> result = patientSearchService.search("", PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void search_ShouldSearchByPhoneToken_WhenQueryIsDigits() {
        when(tokenService.phoneToken("9876543210")).thenReturn("token123");
        when(patientRepo.findByContactNumberToken("token123")).thenReturn(List.of(patient));
        
        when(patientMapper.toResponse(any(Patient.class), anyString())).thenReturn(
            org.mockito.Mockito.mock(PatientResponse.class)
        );

        Page<PatientResponse> result = patientSearchService.search("9876543210", PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
        verify(patientRepo).findByContactNumberToken("token123");
    }

    @Test
    void search_ShouldSearchByPatientNumber_WhenQueryIsAlphanumeric() {
        Page<Patient> page = new PageImpl<>(List.of(patient));
        when(patientRepo.searchByPatientNumber("P-1234", PageRequest.of(0, 10))).thenReturn(page);
        
        when(patientMapper.toResponse(any(Patient.class), anyString())).thenReturn(
            org.mockito.Mockito.mock(PatientResponse.class)
        );

        Page<PatientResponse> result = patientSearchService.search("P-1234", PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
        verify(patientRepo).searchByPatientNumber("P-1234", PageRequest.of(0, 10));
    }

    @Test
    void search_ShouldSearchByName_WhenQueryIsText() {
        Page<Patient> page = new PageImpl<>(List.of(patient));
        when(patientRepo.findAllActive(any(Pageable.class))).thenReturn(page);
        
        when(patientMapper.toResponse(any(Patient.class), anyString())).thenReturn(
            org.mockito.Mockito.mock(PatientResponse.class)
        );

        Page<PatientResponse> result = patientSearchService.search("Test", PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements()); // Matches in memory
    }
}
