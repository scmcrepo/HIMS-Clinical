package com.hms.application.charge;

import com.hms.domain.charge.model.Charge;
import com.hms.domain.charge.model.ChargeType;
import com.hms.domain.charge.model.Tariff;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.catalog.ServiceCatalogItemJpaRepository;
import com.hms.infrastructure.persistence.catalog.ServiceCategoryJpaRepository;
import com.hms.infrastructure.persistence.category.CategoryJpaRepository;
import com.hms.infrastructure.persistence.charge.ChargeJpaRepository;
import com.hms.infrastructure.persistence.charge.TariffJpaRepository;
import com.hms.infrastructure.tenant.BranchContext;
import com.hms.infrastructure.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

    private MockedStatic<TenantContext> mockedTenantContext;
    private MockedStatic<BranchContext> mockedBranchContext;
    
    private UUID chargeId;
    private UUID tenantId;
    private UUID branchId;
    private UUID categoryId;
    private Charge charge;

    @BeforeEach
    void setUp() {
        chargeId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        branchId = UUID.randomUUID();
        categoryId = UUID.randomUUID();

        mockedTenantContext = mockStatic(TenantContext.class);
        mockedTenantContext.when(TenantContext::require).thenReturn(tenantId);
        
        mockedBranchContext = mockStatic(BranchContext.class);
        mockedBranchContext.when(BranchContext::get).thenReturn(branchId);

        charge = new Charge();
        charge.setId(chargeId);
        charge.setTenantId(tenantId);
        charge.setBranchId(branchId);
        charge.setCategoryId(categoryId);
        charge.setName("Consultation Fee");
        // charge.setChargeType(ChargeType.OP);
        
        Tariff tariff = new Tariff();
        tariff.setBillType("CASH");
        tariff.setRate(500L);
        charge.addTariff(tariff);
    }

    @AfterEach
    void tearDown() {
        mockedTenantContext.close();
        mockedBranchContext.close();
    }

    @Test
    void createCharge_ShouldCreateSuccessfully() {
        when(chargeRepo.findByTenantIdAndBranchIdAndNameIgnoreCase(tenantId, branchId, charge.getName()))
                .thenReturn(Collections.emptyList());
        when(chargeRepo.save(any(Charge.class))).thenReturn(charge);

        Charge result = chargeService.createCharge(charge);

        assertNotNull(result);
        verify(chargeRepo).save(charge);
    }

    @Test
    void createCharge_ShouldThrowException_WhenDuplicateName() {
        when(chargeRepo.findByTenantIdAndBranchIdAndNameIgnoreCase(tenantId, branchId, charge.getName()))
                .thenReturn(List.of(charge));

        assertThrows(BusinessRuleViolationException.class, () -> chargeService.createCharge(charge));
    }
    
    @Test
    void createCharge_ShouldDelegateToUpdate_WhenIdExists() {
        charge.setId(chargeId);
        when(chargeRepo.existsById(chargeId)).thenReturn(true);
        when(chargeRepo.findById(chargeId)).thenReturn(Optional.of(charge));
        when(chargeRepo.save(any(Charge.class))).thenReturn(charge);
        
        Charge result = chargeService.createCharge(charge);
        
        assertNotNull(result);
        verify(chargeRepo, atLeastOnce()).save(charge);
    }

    @Test
    void updateCharge_ShouldUpdateInPlace_WhenRatesNotChanged() {
        Charge req = new Charge();
        req.setName("Consultation Fee - Updated");
        req.setCategoryId(categoryId);
        Tariff newTariff = new Tariff();
        newTariff.setBillType("CASH");
        newTariff.setRate(500L); // same rate
        req.addTariff(newTariff);

        when(chargeRepo.findById(chargeId)).thenReturn(Optional.of(charge));
        when(chargeRepo.findByTenantIdAndBranchIdAndNameIgnoreCase(any(), any(), anyString()))
                .thenReturn(Collections.emptyList());
        when(tariffRepo.countBillUsage(chargeId)).thenReturn(5L); // bill is using it, but rate didn't change
        when(chargeRepo.save(any(Charge.class))).thenReturn(charge);

        Charge result = chargeService.updateCharge(chargeId, req);

        assertNotNull(result);
        assertEquals("Consultation Fee - Updated", result.getName());
        verify(chargeRepo).save(charge);
    }

    @Test
    void updateCharge_ShouldCreateNewVersion_WhenRatesChangedAndInUse() {
        Charge req = new Charge();
        req.setName("Consultation Fee");
        req.setCategoryId(categoryId);
        Tariff newTariff = new Tariff();
        newTariff.setBillType("CASH");
        newTariff.setRate(600L); // changed rate
        req.addTariff(newTariff);

        when(chargeRepo.findById(chargeId)).thenReturn(Optional.of(charge));
        when(tariffRepo.countBillUsage(chargeId)).thenReturn(5L); // bill is using it
        when(chargeRepo.save(any(Charge.class))).thenReturn(charge);

        Charge result = chargeService.updateCharge(chargeId, req);

        assertNotNull(result);
        assertNotNull(charge.getEndDate()); // old charge was retired
        verify(chargeRepo, times(2)).save(any(Charge.class)); // save retired + save new
    }

    @Test
    void updateCharge_ShouldUpdateInPlace_WhenRatesChangedButNotInUse() {
        Charge req = new Charge();
        req.setName("Consultation Fee");
        req.setCategoryId(categoryId);
        Tariff newTariff = new Tariff();
        newTariff.setBillType("CASH");
        newTariff.setRate(600L); // changed rate
        req.addTariff(newTariff);

        when(chargeRepo.findById(chargeId)).thenReturn(Optional.of(charge));
        when(tariffRepo.countBillUsage(chargeId)).thenReturn(0L); // NO bills using it
        when(chargeRepo.save(any(Charge.class))).thenReturn(charge);

        Charge result = chargeService.updateCharge(chargeId, req);

        assertNotNull(result);
        assertNull(charge.getEndDate()); // NOT retired
        assertEquals(600L, charge.getTariffs().iterator().next().getRate());
        verify(chargeRepo).save(charge);
    }

    @Test
    void deleteCharge_ShouldRetire() {
        when(chargeRepo.findById(chargeId)).thenReturn(Optional.of(charge));
        
        chargeService.deleteCharge(chargeId);
        
        assertNotNull(charge.getEndDate());
        verify(chargeRepo).save(charge);
    }

    @Test
    void getById_ShouldReturnCharge() {
        when(chargeRepo.findByIdWithTariffs(chargeId)).thenReturn(Optional.of(charge));
        
        Charge result = chargeService.getById(chargeId);
        
        assertNotNull(result);
    }
    
    @Test
    void search_ShouldReturnList() {
        when(chargeRepo.searchByName("Fee")).thenReturn(List.of(charge));
        
        List<Charge> list = chargeService.search("Fee");
        
        assertFalse(list.isEmpty());
    }

    @Test
    void validateDelete_ShouldReturnMessage_WhenInUse() {
        when(tariffRepo.countBillUsage(chargeId)).thenReturn(5L);
        
        String msg = chargeService.validateDelete(chargeId);
        
        assertNotNull(msg);
    }

    @Test
    void validateDelete_ShouldReturnNull_WhenNotInUse() {
        when(tariffRepo.countBillUsage(chargeId)).thenReturn(0L);
        
        String msg = chargeService.validateDelete(chargeId);
        
        assertNull(msg);
    }
}
