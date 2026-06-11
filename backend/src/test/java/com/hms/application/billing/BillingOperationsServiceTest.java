package com.hms.application.billing;

import com.hms.api.billing.request.AddChargeRequest;
import com.hms.api.billing.response.BillResponse;
import com.hms.application.diagnostic.DiagnosticOrderingService;
import com.hms.domain.billing.model.Bill;
import com.hms.domain.billing.model.BillStatus;
import com.hms.domain.billing.model.ChargeLineItem;
import com.hms.domain.billing.model.EncounterType;
import com.hms.domain.catalog.model.ServiceCatalogItem;
import com.hms.domain.charge.model.Charge;
import com.hms.domain.shared.model.Category;
import com.hms.domain.shared.model.ChargeCategoryType;
import com.hms.domain.shared.port.out.SequenceNumberPort;
import com.hms.infrastructure.mapper.BillMapper;
import com.hms.infrastructure.persistence.bed.BedJpaRepository;
import com.hms.infrastructure.persistence.bed.RoomCategoryJpaRepository;
import com.hms.infrastructure.persistence.billing.BillDetailModifiedJpaRepository;
import com.hms.infrastructure.persistence.billing.BillJpaRepository;
import com.hms.infrastructure.persistence.category.CategoryJpaRepository;
import com.hms.infrastructure.persistence.catalog.ServiceCatalogItemJpaRepository;
import com.hms.infrastructure.persistence.charge.ChargeJpaRepository;
import com.hms.infrastructure.persistence.consultant.ConsultantJpaRepository;
import com.hms.infrastructure.persistence.diagnostic.DiagnosticOrderJpaRepository;
import com.hms.infrastructure.persistence.encounter.ClinicalEncounterJpaRepository;
import com.hms.infrastructure.persistence.patient.PatientJpaRepository;
import com.hms.infrastructure.sequence.NumberSequenceJpaRepository;
import com.hms.infrastructure.settings.SettingsRegistryImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
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
    @Mock private ApplicationContext applicationContext;
    @Mock private DiagnosticOrderingService diagnosticOrderingService;
    @Mock private CategoryJpaRepository categoryRepo;

    @InjectMocks
    private BillingOperationsService billingOperationsService;

    @Test
    void testAddChargeLineItem_AutoCreatesDiagnosticsForOutpatientDraftBill() {
        // Arrange
        UUID billId = UUID.randomUUID();
        UUID serviceCatalogItemId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        
        Bill bill = new Bill();
        bill.setId(billId);
        bill.setBillStatus(BillStatus.DRAFT);
        bill.setEncounterType(EncounterType.OUTPATIENT);
        bill.setPatientId(UUID.randomUUID());
        bill.setEncounterId(UUID.randomUUID());

        AddChargeRequest request = new AddChargeRequest(
                serviceCatalogItemId, null, 1000L, 1, null, null
        );

        ServiceCatalogItem serviceCatalogItem = new ServiceCatalogItem();
        serviceCatalogItem.setId(serviceCatalogItemId);
        serviceCatalogItem.setName("Complete Blood Count");

        Charge charge = new Charge();
        charge.setId(serviceCatalogItemId);
        charge.setCategoryId(categoryId);
        charge.setQuantitative(false);

        Category category = new Category();
        category.setId(categoryId);
        category.setName("Lab Tests");
        category.setChargeCategoryType(ChargeCategoryType.DIAGNOSTICS);

        // Set up the autowired applicationContext using ReflectionTestUtils
        ReflectionTestUtils.setField(billingOperationsService, "applicationContext", applicationContext);

        when(billRepo.findByIdForUpdate(billId)).thenReturn(Optional.of(bill));
        when(serviceCatalogRepo.findById(serviceCatalogItemId)).thenReturn(Optional.of(serviceCatalogItem));
        when(chargeRepo.findById(serviceCatalogItemId)).thenReturn(Optional.of(charge));
        when(diagnosticOrderRepo.findByPatientIdAndPaymentStatusIn(any(), any())).thenReturn(Collections.emptyList());
        when(diagnosticOrderRepo.findByEncounterId(any())).thenReturn(Collections.emptyList());
        when(billRepo.save(any(Bill.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        when(applicationContext.getBean(DiagnosticOrderingService.class)).thenReturn(diagnosticOrderingService);
        when(applicationContext.getBean(CategoryJpaRepository.class)).thenReturn(categoryRepo);
        when(categoryRepo.findById(categoryId)).thenReturn(Optional.of(category));
        
        when(billMapper.toResponse(any(), any(), any(), any(), any())).thenReturn(mock(BillResponse.class));

        // Act
        BillResponse response = billingOperationsService.addChargeLineItem(billId, request);

        // Assert
        assertNotNull(response);
        // Verify that diagnostics auto-creation was triggered on the bill
        verify(diagnosticOrderingService).placeOrder(any());
        verify(billRepo, atLeastOnce()).save(any(Bill.class));
    }

    @Test
    void testResolveRate_WithInsurancePayor_ReturnsPayorRate() {
        UUID serviceCatalogItemId = UUID.randomUUID();
        UUID payorId = UUID.randomUUID();

        Bill bill = new Bill();
        bill.setBillType(com.hms.domain.billing.model.BillType.INSURANCE);
        bill.setPayorId(payorId);
        bill.setEncounterType(EncounterType.OUTPATIENT);

        Charge charge = new Charge();
        charge.setId(serviceCatalogItemId);

        com.hms.domain.charge.model.Tariff t1 = new com.hms.domain.charge.model.Tariff();
        t1.setBillType("CASH");
        t1.setRate(10000L);
        charge.addTariff(t1);

        com.hms.domain.charge.model.Tariff t2 = new com.hms.domain.charge.model.Tariff();
        t2.setBillType("CREDIT");
        t2.setRate(11000L);
        charge.addTariff(t2);

        com.hms.domain.charge.model.Tariff t3 = new com.hms.domain.charge.model.Tariff();
        t3.setBillType("INSURANCE");
        t3.setPayorId(payorId);
        t3.setRate(15000L); // Payor specific rate
        charge.addTariff(t3);

        when(chargeRepo.findById(serviceCatalogItemId)).thenReturn(Optional.of(charge));

        long resolvedRate = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                billingOperationsService, "resolveRate", bill, serviceCatalogItemId
        );

        org.junit.jupiter.api.Assertions.assertEquals(15000L, resolvedRate);
    }

    @Test
    void testResolveRate_WithMissingInsurancePayor_FallsBackToCredit() {
        UUID serviceCatalogItemId = UUID.randomUUID();
        UUID payorId = UUID.randomUUID();

        Bill bill = new Bill();
        bill.setBillType(com.hms.domain.billing.model.BillType.INSURANCE);
        bill.setPayorId(payorId);
        bill.setEncounterType(EncounterType.OUTPATIENT);

        Charge charge = new Charge();
        charge.setId(serviceCatalogItemId);

        com.hms.domain.charge.model.Tariff t1 = new com.hms.domain.charge.model.Tariff();
        t1.setBillType("CASH");
        t1.setRate(10000L);
        charge.addTariff(t1);

        com.hms.domain.charge.model.Tariff t2 = new com.hms.domain.charge.model.Tariff();
        t2.setBillType("CREDIT");
        t2.setRate(11000L); // Credit rate
        charge.addTariff(t2);

        // No insurance payor rate matching payorId is configured

        when(chargeRepo.findById(serviceCatalogItemId)).thenReturn(Optional.of(charge));

        long resolvedRate = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                billingOperationsService, "resolveRate", bill, serviceCatalogItemId
        );

        org.junit.jupiter.api.Assertions.assertEquals(11000L, resolvedRate);
    }
}
