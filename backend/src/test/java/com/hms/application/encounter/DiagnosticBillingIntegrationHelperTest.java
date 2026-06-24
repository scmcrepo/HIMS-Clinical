package com.hms.application.encounter;

import com.hms.api.billing.response.BillResponse;
import com.hms.api.diagnostic.request.PlaceOrderRequest;
import com.hms.api.opip.request.AddDiagnosticOrderRequest;
import com.hms.application.billing.BillingOperationsService;
import com.hms.application.diagnostic.DiagnosticOrderingService;
import com.hms.domain.billing.model.EncounterType;
import com.hms.domain.catalog.model.ServiceCatalogItem;
import com.hms.domain.diagnostic.model.DiagnosticTemplate;
import com.hms.infrastructure.persistence.catalog.ServiceCatalogItemJpaRepository;
import com.hms.infrastructure.persistence.diagtemplate.DiagnosticTemplateJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiagnosticBillingIntegrationHelperTest {

    @Mock private DiagnosticOrderingService diagnosticOrderingService;
    @Mock private BillingOperationsService billingService;
    @Mock private DiagnosticTemplateJpaRepository templateRepo;
    @Mock private ServiceCatalogItemJpaRepository serviceCatalogItemRepo;

    @InjectMocks
    private DiagnosticBillingIntegrationHelper helper;

    @Test
    void testPlaceDiagnosticOrderAndBill_DirectLookupSuccess() {
        UUID patientId = UUID.randomUUID();
        UUID encounterId = UUID.randomUUID();
        UUID requestedById = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID chargeId = UUID.randomUUID();
        UUID billId = UUID.randomUUID();

        // Mock ensureDraftBill
        BillResponse billResp = mock(BillResponse.class);
        when(billResp.id()).thenReturn(billId);
        when(billingService.ensureDraftBill(eq(patientId), eq(encounterId), eq(EncounterType.OUTPATIENT), eq(requestedById)))
                .thenReturn(billResp);

        // Mock DiagnosticTemplate lookup
        DiagnosticTemplate template = new DiagnosticTemplate();
        template.setId(templateId);
        template.setChargeId(chargeId);
        template.setName("Complete Blood Count");
        when(templateRepo.findById(templateId)).thenReturn(Optional.of(template));

        // Mock ServiceCatalogItem existence check
        when(serviceCatalogItemRepo.existsById(chargeId)).thenReturn(true);

        // Input requests
        AddDiagnosticOrderRequest.DiagnosticOrderLineRequest item = new AddDiagnosticOrderRequest.DiagnosticOrderLineRequest(
                templateId.toString(), "Complete Blood Count", "LAB"
        );

        // Execute
        helper.placeDiagnosticOrderAndBill(List.of(item), patientId, encounterId, EncounterType.OUTPATIENT, requestedById);

        // Verify placeOrder was called with the direct chargeId
        ArgumentCaptor<PlaceOrderRequest> captor = ArgumentCaptor.forClass(PlaceOrderRequest.class);
        verify(diagnosticOrderingService).placeOrder(captor.capture());
        PlaceOrderRequest request = captor.getValue();
        assertEquals(encounterId, request.encounterId());
        assertEquals(patientId, request.patientId());
        assertEquals(billId, request.billId());
        assertEquals(1, request.lines().size());
        assertEquals(chargeId, request.lines().get(0).serviceCatalogItemId());
    }

    @Test
    void testPlaceDiagnosticOrderAndBill_FallbackNameBasedLookupSuccess() {
        UUID patientId = UUID.randomUUID();
        UUID encounterId = UUID.randomUUID();
        UUID requestedById = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID chargeId = UUID.randomUUID();
        UUID resolvedCatalogItemId = UUID.randomUUID();
        UUID billId = UUID.randomUUID();

        // Mock ensureDraftBill
        BillResponse billResp = mock(BillResponse.class);
        when(billResp.id()).thenReturn(billId);
        when(billingService.ensureDraftBill(eq(patientId), eq(encounterId), eq(EncounterType.OUTPATIENT), eq(requestedById)))
                .thenReturn(billResp);

        // Mock DiagnosticTemplate lookup (chargeId is set but doesn't exist in service catalog, simulating drift)
        DiagnosticTemplate template = new DiagnosticTemplate();
        template.setId(templateId);
        template.setChargeId(chargeId);
        template.setName("TSH");
        when(templateRepo.findById(templateId)).thenReturn(Optional.of(template));

        // existsById for chargeId returns false
        when(serviceCatalogItemRepo.existsById(chargeId)).thenReturn(false);

        // Fallback name-based lookup mock
        ServiceCatalogItem catalogItem = new ServiceCatalogItem();
        catalogItem.setId(resolvedCatalogItemId);
        catalogItem.setName("TSH");
        when(serviceCatalogItemRepo.findActiveByNameIgnoreCase("TSH")).thenReturn(List.of(catalogItem));

        // Input requests
        AddDiagnosticOrderRequest.DiagnosticOrderLineRequest item = new AddDiagnosticOrderRequest.DiagnosticOrderLineRequest(
                templateId.toString(), "TSH", "LAB"
        );

        // Execute
        helper.placeDiagnosticOrderAndBill(List.of(item), patientId, encounterId, EncounterType.OUTPATIENT, requestedById);

        // Verify placeOrder was called with the fallback name-based resolvedCatalogItemId
        ArgumentCaptor<PlaceOrderRequest> captor = ArgumentCaptor.forClass(PlaceOrderRequest.class);
        verify(diagnosticOrderingService).placeOrder(captor.capture());
        PlaceOrderRequest request = captor.getValue();
        assertEquals(1, request.lines().size());
        assertEquals(resolvedCatalogItemId, request.lines().get(0).serviceCatalogItemId());
    }

    @Test
    void testPlaceDiagnosticOrderAndBill_RawIdFallbackSuccess() {
        UUID patientId = UUID.randomUUID();
        UUID encounterId = UUID.randomUUID();
        UUID requestedById = UUID.randomUUID();
        UUID catalogItemId = UUID.randomUUID();
        UUID resolvedCatalogItemId = UUID.randomUUID();
        UUID billId = UUID.randomUUID();

        // Mock ensureDraftBill
        BillResponse billResp = mock(BillResponse.class);
        when(billResp.id()).thenReturn(billId);
        when(billingService.ensureDraftBill(eq(patientId), eq(encounterId), eq(EncounterType.OUTPATIENT), eq(requestedById)))
                .thenReturn(billResp);

        // Mock DiagnosticTemplate lookup - not a template
        when(templateRepo.findById(catalogItemId)).thenReturn(Optional.empty());

        // Mock existsById for catalogItemId - false (stale or drifted catalog ID)
        when(serviceCatalogItemRepo.existsById(catalogItemId)).thenReturn(false);

        // Fallback name-based lookup mock using testName
        ServiceCatalogItem catalogItem = new ServiceCatalogItem();
        catalogItem.setId(resolvedCatalogItemId);
        catalogItem.setName("Chest X-Ray");
        when(serviceCatalogItemRepo.findActiveByNameIgnoreCase("Chest X-Ray")).thenReturn(List.of(catalogItem));

        // Input requests
        AddDiagnosticOrderRequest.DiagnosticOrderLineRequest item = new AddDiagnosticOrderRequest.DiagnosticOrderLineRequest(
                catalogItemId.toString(), "Chest X-Ray", "RADIOLOGY"
        );

        // Execute
        helper.placeDiagnosticOrderAndBill(List.of(item), patientId, encounterId, EncounterType.OUTPATIENT, requestedById);

        // Verify placeOrder was called with the resolved ID for RADIOLOGY
        ArgumentCaptor<PlaceOrderRequest> captor = ArgumentCaptor.forClass(PlaceOrderRequest.class);
        verify(diagnosticOrderingService).placeOrder(captor.capture());
        PlaceOrderRequest request = captor.getValue();
        assertEquals(1, request.lines().size());
        assertEquals(resolvedCatalogItemId, request.lines().get(0).serviceCatalogItemId());
    }

    @Test
    void testPlaceDiagnosticOrderAndBill_InvalidIdHandledGracefully() {
        UUID patientId = UUID.randomUUID();
        UUID encounterId = UUID.randomUUID();
        UUID requestedById = UUID.randomUUID();
        UUID invalidId = UUID.randomUUID();
        UUID billId = UUID.randomUUID();

        // Mock ensureDraftBill
        BillResponse billResp = mock(BillResponse.class);
        when(billResp.id()).thenReturn(billId);
        when(billingService.ensureDraftBill(eq(patientId), eq(encounterId), eq(EncounterType.OUTPATIENT), eq(requestedById)))
                .thenReturn(billResp);

        // Mock template lookup -> not a template
        when(templateRepo.findById(invalidId)).thenReturn(Optional.empty());

        // existsById -> false
        when(serviceCatalogItemRepo.existsById(invalidId)).thenReturn(false);

        // Fallback lookup -> empty list (no match by name either)
        when(serviceCatalogItemRepo.findActiveByNameIgnoreCase("Non Existent Test")).thenReturn(Collections.emptyList());

        // Input requests
        AddDiagnosticOrderRequest.DiagnosticOrderLineRequest item = new AddDiagnosticOrderRequest.DiagnosticOrderLineRequest(
                invalidId.toString(), "Non Existent Test", "LAB"
        );

        // Execute
        helper.placeDiagnosticOrderAndBill(List.of(item), patientId, encounterId, EncounterType.OUTPATIENT, requestedById);

        // Verify placeOrder was NOT called since no valid service catalog ID could be resolved
        verify(diagnosticOrderingService, never()).placeOrder(any());
    }
}


