package com.hms.application.visit;

import com.hms.domain.visit.model.Visit;
import com.hms.domain.visit.model.VisitMode;
import com.hms.domain.visit.model.VisitStatus;
import com.hms.domain.visit.model.VisitType;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.visit.VisitJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VisitServiceTest {

    @Mock private VisitJpaRepository visitRepo;

    @InjectMocks
    private VisitService visitService;

    private UUID patientId;
    private UUID consultantId;
    private UUID visitId;
    private Visit visit;

    @BeforeEach
    void setUp() {
        patientId = UUID.randomUUID();
        consultantId = UUID.randomUUID();
        visitId = UUID.randomUUID();
        
        visit = new Visit();
        visit.setId(visitId);
        visit.setPatientId(patientId);
        visit.setVisitType(VisitType.OP);
    }

    @Test
    void createOpVisit_ShouldCreateWalkInVisit() {
        when(visitRepo.save(any(Visit.class))).thenAnswer(i -> {
            Visit v = i.getArgument(0);
            v.setId(UUID.randomUUID());
            return v;
        });

        Visit result = visitService.createOpVisit(patientId, consultantId, null, null);

        assertNotNull(result);
        assertEquals(VisitType.OP, result.getVisitType());
        assertEquals(VisitMode.WALK_IN, result.getVisitMode());
        assertEquals(VisitStatus.CHECKEDIN, result.getVisitStatus());
        verify(visitRepo).save(any(Visit.class));
    }

    @Test
    void createIpVisit_ShouldCreateIpVisit() {
        UUID bedId = UUID.randomUUID();
        LocalDate admissionDate = LocalDate.now().minusDays(2);
        
        when(visitRepo.save(any(Visit.class))).thenAnswer(i -> {
            Visit v = i.getArgument(0);
            v.setId(UUID.randomUUID());
            return v;
        });

        Visit result = visitService.createIpVisit(patientId, consultantId, admissionDate, bedId);

        assertNotNull(result);
        assertEquals(VisitType.IP, result.getVisitType());
        assertEquals(admissionDate, result.getVisitDate());
        assertTrue(result.isBedStatus());
        assertEquals(bedId, result.getLastBedId());
    }

    @Test
    void stampCasesheetDate_ShouldSetCasesheetStampAndSave() {
        when(visitRepo.findById(visitId)).thenReturn(Optional.of(visit));
        when(visitRepo.save(any(Visit.class))).thenReturn(visit);

        visitService.stampCasesheetDate(visitId);

        verify(visitRepo).save(visit);
    }

    @Test
    void markBillDraft_ShouldSetBillStatusToTrue() {
        when(visitRepo.findById(visitId)).thenReturn(Optional.of(visit));
        when(visitRepo.save(any(Visit.class))).thenReturn(visit);

        visitService.markBillDraft(visitId);

        assertTrue(visit.isBillStatus());
        verify(visitRepo).save(visit);
    }

    @Test
    void clearBillStatus_ShouldSetBillStatusToFalse() {
        visit.setBillStatus(true);
        when(visitRepo.findById(visitId)).thenReturn(Optional.of(visit));
        when(visitRepo.save(any(Visit.class))).thenReturn(visit);

        visitService.clearBillStatus(visitId);

        assertFalse(visit.isBillStatus());
        verify(visitRepo).save(visit);
    }
}
