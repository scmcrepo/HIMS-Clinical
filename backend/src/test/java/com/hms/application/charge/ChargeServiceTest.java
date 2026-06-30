package com.hms.application.charge;

import com.hms.domain.charge.model.Charge;
import com.hms.domain.charge.model.Tariff;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.infrastructure.persistence.charge.ChargeJpaRepository;
import com.hms.infrastructure.persistence.charge.TariffJpaRepository;
import com.hms.infrastructure.tenant.TenantContext;
import com.hms.infrastructure.tenant.BranchContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChargeServiceTest {

    @Mock private ChargeJpaRepository chargeRepo;
    @Mock private TariffJpaRepository tariffRepo;
    @Mock private com.hms.infrastructure.persistence.catalog.ServiceCatalogItemJpaRepository serviceCatalogItemRepo;
    @Mock private com.hms.infrastructure.persistence.catalog.ServiceCategoryJpaRepository serviceCategoryRepo;
    @Mock private com.hms.infrastructure.persistence.category.CategoryJpaRepository categoryRepo;
    @Mock private com.hms.infrastructure.persistence.diagtemplate.DiagnosticTemplateJpaRepository diagTemplateRepo;

    @InjectMocks
    private ChargeService chargeService;

    private UUID tenantId;
    private UUID branchId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        branchId = UUID.randomUUID();
        TenantContext.set(tenantId);
        BranchContext.set(branchId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        BranchContext.clear();
    }

    @Test
    void testCreateCharge_Success_NoCollision() {
        Charge req = new Charge();
        req.setName("Unique Charge Name");

        when(chargeRepo.findByTenantIdAndBranchIdAndNameIgnoreCase(eq(tenantId), eq(branchId), eq("Unique Charge Name")))
                .thenReturn(Collections.emptyList());
        when(chargeRepo.save(any(Charge.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Charge saved = chargeService.createCharge(req);

        assertNotNull(saved);
        assertEquals("Unique Charge Name", saved.getName());
        verify(chargeRepo).save(req);
    }

    @Test
    void testCreateCharge_Failure_DuplicateNameInSameBranch() {
        Charge req = new Charge();
        req.setName("Duplicate Charge Name");

        Charge existing = new Charge();
        existing.setId(UUID.randomUUID());
        existing.setName("Duplicate Charge Name");

        when(chargeRepo.findByTenantIdAndBranchIdAndNameIgnoreCase(eq(tenantId), eq(branchId), eq("Duplicate Charge Name")))
                .thenReturn(List.of(existing));

        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> chargeService.createCharge(req)
        );

        assertTrue(exception.getMessage().contains("already exists in this branch"));
        verify(chargeRepo, never()).save(any());
    }

    @Test
    void testUpdateCharge_RenameSuccess_NoCollision() {
        UUID chargeId = UUID.randomUUID();
        Charge existing = new Charge();
        existing.setId(chargeId);
        existing.setName("Old Name");
        existing.setTenantId(tenantId);
        existing.setBranchId(branchId);

        Charge req = new Charge();
        req.setName("New Brand New Name");

        when(chargeRepo.findById(chargeId)).thenReturn(Optional.of(existing));
        when(chargeRepo.findByTenantIdAndBranchIdAndNameIgnoreCase(eq(tenantId), eq(branchId), eq("New Brand New Name")))
                .thenReturn(Collections.emptyList());
        when(chargeRepo.save(any(Charge.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Charge updated = chargeService.updateCharge(chargeId, req);

        assertNotNull(updated);
        assertEquals("New Brand New Name", updated.getName());
    }

    @Test
    void testUpdateCharge_RenameFailure_NameAlreadyTaken() {
        UUID chargeId = UUID.randomUUID();
        Charge existing = new Charge();
        existing.setId(chargeId);
        existing.setName("Old Name");
        existing.setTenantId(tenantId);
        existing.setBranchId(branchId);

        Charge req = new Charge();
        req.setName("Duplicate Name");

        Charge otherActiveCharge = new Charge();
        otherActiveCharge.setId(UUID.randomUUID());
        otherActiveCharge.setName("Duplicate Name");

        when(chargeRepo.findById(chargeId)).thenReturn(Optional.of(existing));
        when(chargeRepo.findByTenantIdAndBranchIdAndNameIgnoreCase(eq(tenantId), eq(branchId), eq("Duplicate Name")))
                .thenReturn(List.of(otherActiveCharge));

        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> chargeService.updateCharge(chargeId, req)
        );

        assertTrue(exception.getMessage().contains("already exists in this branch"));
        verify(chargeRepo, never()).save(any());
    }
}
