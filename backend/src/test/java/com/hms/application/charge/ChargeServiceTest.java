package com.hms.application.charge;

import com.hms.domain.charge.model.Charge;
import com.hms.domain.charge.model.Tariff;
import com.hms.infrastructure.persistence.charge.ChargeJpaRepository;
import com.hms.infrastructure.persistence.charge.TariffJpaRepository;
import com.hms.infrastructure.persistence.catalog.ServiceCatalogItemJpaRepository;
import com.hms.infrastructure.persistence.catalog.ServiceCategoryJpaRepository;
import com.hms.infrastructure.persistence.category.CategoryJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChargeServiceTest {

    @Mock private ChargeJpaRepository chargeRepo;
    @Mock private TariffJpaRepository tariffRepo;
    @Mock private ServiceCatalogItemJpaRepository serviceCatalogItemRepo;
    @Mock private ServiceCategoryJpaRepository serviceCategoryRepo;
    @Mock private CategoryJpaRepository categoryRepo;

    @InjectMocks
    private ChargeService chargeService;

    private UUID chargeId;
    private Charge existingCharge;

    @BeforeEach
    void setUp() {
        chargeId = UUID.randomUUID();
        existingCharge = new Charge();
        existingCharge.setId(chargeId);
        existingCharge.setName("Test Charge");

        Tariff cashTariff = new Tariff();
        cashTariff.setBillType("CASH");
        cashTariff.setRate(10000L); // 100 Rs
        existingCharge.addTariff(cashTariff);

        Tariff creditTariff = new Tariff();
        creditTariff.setBillType("CREDIT");
        creditTariff.setRate(11000L); // 110 Rs
        existingCharge.addTariff(creditTariff);
    }

    @Test
    void testUpdateCharge_NoTariffChange_DoesNotTriggerVersioning() {
        Charge req = new Charge();
        req.setId(chargeId);
        req.setName("Test Charge Modified Name");
        
        Tariff t1 = new Tariff();
        t1.setBillType("CASH");
        t1.setRate(10000L);
        req.addTariff(t1);

        Tariff t2 = new Tariff();
        t2.setBillType("CREDIT");
        t2.setRate(11000L);
        req.addTariff(t2);

        when(chargeRepo.findById(chargeId)).thenReturn(Optional.of(existingCharge));
        when(tariffRepo.countBillUsage(chargeId)).thenReturn(1L); // Bills use it
        when(chargeRepo.save(any(Charge.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Charge result = chargeService.updateCharge(chargeId, req);

        assertNotNull(result);
        assertEquals("Test Charge Modified Name", result.getName());
        verify(chargeRepo, times(1)).save(existingCharge); // updated in place
    }

    @Test
    void testUpdateCharge_TariffAdded_TriggersVersioningIfBillsUseIt() {
        Charge req = new Charge();
        req.setId(chargeId);
        req.setName("Test Charge");

        Tariff t1 = new Tariff();
        t1.setBillType("CASH");
        t1.setRate(10000L);
        req.addTariff(t1);

        Tariff t2 = new Tariff();
        t2.setBillType("CREDIT");
        t2.setRate(11000L);
        req.addTariff(t2);

        Tariff newTariff = new Tariff();
        newTariff.setBillType("INSURANCE");
        newTariff.setPayorId(UUID.randomUUID());
        newTariff.setRate(12000L);
        req.addTariff(newTariff);

        when(chargeRepo.findById(chargeId)).thenReturn(Optional.of(existingCharge));
        when(tariffRepo.countBillUsage(chargeId)).thenReturn(1L); // Bills use it
        when(chargeRepo.save(any(Charge.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Charge result = chargeService.updateCharge(chargeId, req);

        assertNotNull(result);
        assertNotEquals(existingCharge, result); // it created a new Charge object due to versioning
        assertEquals("Test Charge", result.getName());
        assertNotNull(existingCharge.getEndDate()); // existing got retired
        verify(chargeRepo, times(1)).save(existingCharge); // saved retired charge
        verify(chargeRepo, times(1)).save(argThat(c -> c != existingCharge)); // saved new charge
    }

    @Test
    void testUpdateCharge_TariffRemoved_TriggersVersioningIfBillsUseIt() {
        Charge req = new Charge();
        req.setId(chargeId);
        req.setName("Test Charge");

        Tariff t1 = new Tariff();
        t1.setBillType("CASH");
        t1.setRate(10000L);
        req.addTariff(t1); // CREDIT tariff is removed

        when(chargeRepo.findById(chargeId)).thenReturn(Optional.of(existingCharge));
        when(tariffRepo.countBillUsage(chargeId)).thenReturn(1L); // Bills use it
        when(chargeRepo.save(any(Charge.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Charge result = chargeService.updateCharge(chargeId, req);

        assertNotNull(result);
        assertNotEquals(existingCharge, result);
        assertNotNull(existingCharge.getEndDate());
        verify(chargeRepo, times(1)).save(existingCharge);
        verify(chargeRepo, times(1)).save(argThat(c -> c != existingCharge));
    }

    @Test
    void testUpdateCharge_TariffRateModified_TriggersVersioningIfBillsUseIt() {
        Charge req = new Charge();
        req.setId(chargeId);
        req.setName("Test Charge");

        Tariff t1 = new Tariff();
        t1.setBillType("CASH");
        t1.setRate(10500L); // Rate modified from 10000L
        req.addTariff(t1);

        Tariff t2 = new Tariff();
        t2.setBillType("CREDIT");
        t2.setRate(11000L);
        req.addTariff(t2);

        when(chargeRepo.findById(chargeId)).thenReturn(Optional.of(existingCharge));
        when(tariffRepo.countBillUsage(chargeId)).thenReturn(1L); // Bills use it
        when(chargeRepo.save(any(Charge.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Charge result = chargeService.updateCharge(chargeId, req);

        assertNotNull(result);
        assertNotEquals(existingCharge, result);
        assertNotNull(existingCharge.getEndDate());
        verify(chargeRepo, times(1)).save(existingCharge);
        verify(chargeRepo, times(1)).save(argThat(c -> c != existingCharge));
    }
    @Test
    void testUpdateCharge_SyncsMultipleInsuranceTariffsToSinglePricingTier() {
        Charge req = new Charge();
        req.setId(chargeId);
        req.setName("Test Charge");
        UUID categoryId = UUID.randomUUID();
        req.setCategoryId(categoryId);

        Tariff t1 = new Tariff();
        t1.setBillType("CASH");
        t1.setRate(10000L);
        req.addTariff(t1);

        // Multiple INSURANCE tariffs
        Tariff t2 = new Tariff();
        t2.setBillType("INSURANCE");
        t2.setPayorId(UUID.randomUUID());
        t2.setRate(12000L);
        req.addTariff(t2);

        Tariff t3 = new Tariff();
        t3.setBillType("INSURANCE");
        t3.setPayorId(UUID.randomUUID());
        t3.setRate(13000L);
        req.addTariff(t3);

        com.hms.domain.shared.model.Category uiCategory = new com.hms.domain.shared.model.Category();
        uiCategory.setId(categoryId);
        uiCategory.setName("Lab");
        uiCategory.setChargeCategoryType(com.hms.domain.shared.model.ChargeCategoryType.DIAGNOSTICS);

        com.hms.domain.catalog.model.ServiceCategory serviceCategory = new com.hms.domain.catalog.model.ServiceCategory();
        serviceCategory.setId(UUID.randomUUID());
        serviceCategory.setName("Lab");

        com.hms.domain.catalog.model.ServiceCatalogItem serviceCatalogItem = new com.hms.domain.catalog.model.ServiceCatalogItem();
        serviceCatalogItem.setId(chargeId);

        when(chargeRepo.findById(chargeId)).thenReturn(Optional.of(existingCharge));
        when(tariffRepo.countBillUsage(chargeId)).thenReturn(0L); // No bills use it, safe update in place
        when(categoryRepo.findById(categoryId)).thenReturn(Optional.of(uiCategory));
        when(serviceCategoryRepo.findByName("Lab")).thenReturn(Optional.of(serviceCategory));
        when(serviceCatalogItemRepo.findById(chargeId)).thenReturn(Optional.of(serviceCatalogItem));
        when(chargeRepo.save(any(Charge.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Charge result = chargeService.updateCharge(chargeId, req);

        assertNotNull(result);
        // Verify only 2 pricing tiers were created/updated: one CASH and one INSURANCE (no duplicates)
        assertEquals(2, serviceCatalogItem.getPricingTiers().size());
        assertTrue(serviceCatalogItem.getPricingTiers().stream().anyMatch(pt -> pt.getBillType() == com.hms.domain.billing.model.BillType.CASH));
        assertTrue(serviceCatalogItem.getPricingTiers().stream().anyMatch(pt -> pt.getBillType() == com.hms.domain.billing.model.BillType.INSURANCE));
    }
}
