package com.hms.application.billing;

import com.hms.api.billing.request.AddChargeRequest;
import com.hms.api.billing.request.CreateBillRequest;
import com.hms.api.billing.response.BillResponse;
import com.hms.api.billing.response.BillSummaryResponse;
import com.hms.domain.billing.model.*;
import com.hms.domain.billing.service.BillingEngine;
import com.hms.domain.patient.model.Patient;
import com.hms.domain.shared.port.out.SequenceNumberPort;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.mapper.BillMapper;
import com.hms.infrastructure.persistence.bed.BedJpaRepository;
import com.hms.infrastructure.persistence.bed.RoomCategoryJpaRepository;
import com.hms.infrastructure.persistence.billing.BillDetailModifiedJpaRepository;
import com.hms.infrastructure.persistence.billing.BillJpaRepository;
import com.hms.infrastructure.persistence.catalog.ServiceCatalogItemJpaRepository;
import com.hms.infrastructure.persistence.charge.ChargeJpaRepository;
import com.hms.infrastructure.persistence.consultant.ConsultantJpaRepository;
import com.hms.infrastructure.persistence.diagnostic.DiagnosticOrderJpaRepository;
import com.hms.infrastructure.persistence.encounter.ClinicalEncounterJpaRepository;
import com.hms.infrastructure.persistence.patient.PatientJpaRepository;
import com.hms.infrastructure.sequence.NumberSequenceEntity;
import com.hms.infrastructure.sequence.NumberSequenceJpaRepository;
import com.hms.infrastructure.settings.SettingsRegistryImpl;
import com.hms.security.encryption.PiiSearchTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillingOperationsServiceTest {

    @Mock private BillingEngineFactory engineFactory;
    @Mock private BillDetailModifiedJpaRepository billDetailModifiedRepo;
    @Mock private BillJpaRepository billRepo;
    @Mock private BillMapper billMapper;
    @Mock private SettingsRegistryImpl settingsRegistry;
    @Mock private PatientJpaRepository patientRepo;
    @Mock private DiagnosticOrderJpaRepository diagnosticOrderRepo;
    @Mock private ServiceCatalogItemJpaRepository serviceCatalogRepo;
    @Mock private ChargeJpaRepository chargeRepo;
    @Mock private NumberSequenceJpaRepository numberSequenceRepo;
    @Mock private ClinicalEncounterJpaRepository encounterRepo;
    @Mock private BedJpaRepository bedRepo;
    @Mock private RoomCategoryJpaRepository roomCategoryRepo;
    @Mock private ConsultantJpaRepository consultantRepo;
    @Mock private SequenceNumberPort sequencePort;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private PiiSearchTokenService tokenService;
    @Mock private ApplicationContext applicationContext;

    @InjectMocks
    private BillingOperationsService billingOperationsService;

    private UUID billId;
    private UUID patientId;
    private UUID encounterId;
    private UUID providerId;
    private Bill bill;
    private Patient patient;
    private NumberSequenceEntity numberSequenceEntity;

    @BeforeEach
    void setUp() {
        billId = UUID.randomUUID();
        patientId = UUID.randomUUID();
        encounterId = UUID.randomUUID();
        providerId = UUID.randomUUID();

        bill = new Bill();
        bill.setId(billId);
        bill.setPatientId(patientId);
        bill.setEncounterId(encounterId);
        bill.setPrimaryProviderId(providerId);
        bill.setBillStatus(BillStatus.DRAFT);
        bill.setEncounterType(EncounterType.OUTPATIENT);
        bill.setBillType(BillType.CASH);
        bill.setChargeLineItems(new ArrayList<>());

        patient = new Patient();
        patient.setId(patientId);
        patient.setFirstName("Jane");
        patient.setLastName("Doe");

        numberSequenceEntity = new NumberSequenceEntity();
        numberSequenceEntity.setValue("PAT-002");
    }

    // -------------------------------------------------------------------------
    // getAllBills
    // -------------------------------------------------------------------------

    @Test
    void getAllBills_ShouldReturnMappedBills() {
        when(billRepo.findAll()).thenReturn(List.of(bill));
        when(patientRepo.findById(patientId)).thenReturn(Optional.of(patient));
        when(numberSequenceRepo.findById(patientId)).thenReturn(Optional.of(numberSequenceEntity));
        
        BillSummaryResponse mockResponse = mock(BillSummaryResponse.class);
        when(billMapper.toSummaryResponse(eq(bill), anyString(), anyString())).thenReturn(mockResponse);

        List<BillSummaryResponse> result = billingOperationsService.getAllBills();

        assertFalse(result.isEmpty());
        verify(billRepo).findAll();
        verify(billMapper).toSummaryResponse(bill, "Jane Doe", "PAT-002");
    }

    // -------------------------------------------------------------------------
    // searchBills
    // -------------------------------------------------------------------------

    @Test
    void searchBills_ShouldReturnEmpty_WhenNoMatchFound() {
        when(numberSequenceRepo.findIdsByValue("unknown")).thenReturn(Collections.emptyList());
        when(patientRepo.findAllActive(any())).thenReturn(new PageImpl<>(Collections.emptyList()));

        Page<BillSummaryResponse> result = billingOperationsService.searchBills("unknown", null, null, PageRequest.of(0, 10));

        assertTrue(result.isEmpty());
    }

    @Test
    void searchBills_ShouldReturnBills_WhenMatchFound() {
        when(numberSequenceRepo.findIdsByValue("PAT-002")).thenReturn(List.of(patientId));
        when(billRepo.searchBills(any(), any(), eq(List.of(patientId)), any()))
            .thenReturn(new PageImpl<>(List.of(bill)));
        
        when(patientRepo.findById(patientId)).thenReturn(Optional.of(patient));
        when(numberSequenceRepo.findById(patientId)).thenReturn(Optional.of(numberSequenceEntity));
        
        BillSummaryResponse mockResponse = mock(BillSummaryResponse.class);
        when(billMapper.toSummaryResponse(eq(bill), anyString(), anyString())).thenReturn(mockResponse);

        Page<BillSummaryResponse> result = billingOperationsService.searchBills("PAT-002", null, null, PageRequest.of(0, 10));

        assertFalse(result.isEmpty());
        verify(billRepo).searchBills(any(), any(), eq(List.of(patientId)), any());
    }

    // -------------------------------------------------------------------------
    // createBill
    // -------------------------------------------------------------------------

    @Test
    void createBill_ShouldResumeExistingOutpatientBill() {
        CreateBillRequest request = new CreateBillRequest(patientId, BillType.CASH, EncounterType.OUTPATIENT, providerId, encounterId, null, null, java.time.Instant.now());
        when(billRepo.findDraftBillsByPatientId(patientId)).thenReturn(List.of(bill));
        
        when(billRepo.findById(billId)).thenReturn(Optional.of(bill));
        when(patientRepo.findById(patientId)).thenReturn(Optional.of(patient));
        BillResponse mockResponse = mock(BillResponse.class);
        when(billMapper.toResponse(any(), any(), any(), any(), any())).thenReturn(mockResponse);

        BillResponse result = billingOperationsService.createBill(request);

        assertNotNull(result);
        verify(engineFactory, never()).createDraft(any(), any(), any(), any());
    }

    @Test
    void createBill_ShouldCreateNewBill() {
        CreateBillRequest request = new CreateBillRequest(patientId, BillType.CASH, EncounterType.INPATIENT, providerId, encounterId, null, null, java.time.Instant.now());
        when(billRepo.findDraftBillsByPatientId(patientId)).thenReturn(Collections.emptyList());
        
        BillingEngine mockEngine = new BillingEngine(bill, sequencePort, eventPublisher);
        when(engineFactory.createDraft(patientId, BillType.CASH, EncounterType.INPATIENT, providerId)).thenReturn(mockEngine);
        when(billRepo.saveAndFlush(any(Bill.class))).thenReturn(bill);
        
        when(patientRepo.findById(patientId)).thenReturn(Optional.of(patient));
        BillResponse mockResponse = mock(BillResponse.class);
        when(billMapper.toResponse(any(), any(), any(), any(), any())).thenReturn(mockResponse);

        BillResponse result = billingOperationsService.createBill(request);

        assertNotNull(result);
        verify(billRepo).saveAndFlush(any(Bill.class));
    }

    // -------------------------------------------------------------------------
    // getBillById
    // -------------------------------------------------------------------------

    @Test
    void getBillById_ShouldReturnBill() {
        when(billRepo.findById(billId)).thenReturn(Optional.of(bill));
        when(patientRepo.findById(patientId)).thenReturn(Optional.of(patient));
        BillResponse mockResponse = mock(BillResponse.class);
        when(billMapper.toResponse(any(), any(), any(), any(), any())).thenReturn(mockResponse);

        BillResponse result = billingOperationsService.getBillById(billId);

        assertNotNull(result);
    }
    
    @Test
    void getBillById_ShouldThrowException_WhenBillNotFound() {
        when(billRepo.findById(billId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> billingOperationsService.getBillById(billId));
    }

    // -------------------------------------------------------------------------
    // removeChargeLineItem
    // -------------------------------------------------------------------------

    @Test
    void removeChargeLineItem_ShouldRemoveCharge() {
        UUID lineItemId = UUID.randomUUID();
        ChargeLineItem cli = new ChargeLineItem();
        cli.setId(lineItemId);
        cli.setItemName("Test Charge");
        cli.setAmount(100);
        cli.setQuantity(1);
        // cli.setLineStatus(ChargeLineStatus.ACTIVE);
        bill.getChargeLineItems().add(cli);
        bill.setBillAmount(100);

        when(billRepo.findByIdForUpdate(billId)).thenReturn(Optional.of(bill));
        when(billRepo.save(any(Bill.class))).thenReturn(bill);
        when(patientRepo.findById(patientId)).thenReturn(Optional.of(patient));
        BillResponse mockResponse = mock(BillResponse.class);
        when(billMapper.toResponse(any(), any(), any(), any(), any())).thenReturn(mockResponse);

        BillResponse result = billingOperationsService.removeChargeLineItem(billId, lineItemId, "Mistake");

        assertNotNull(result);
        assertEquals(ChargeLineStatus.CANCELLED, cli.getLineStatus());
        verify(billRepo).save(bill);
    }
}
