package com.hms.application.encounter;

import com.hms.api.billing.response.BillResponse;
import com.hms.api.diagnostic.request.PlaceOrderRequest;
import com.hms.api.opip.request.AddDiagnosticOrderRequest;
import com.hms.application.billing.BillingOperationsService;
import com.hms.application.diagnostic.DiagnosticOrderingService;
import com.hms.domain.billing.model.EncounterType;
import com.hms.domain.diagnostic.model.DiagnosticType;
import com.hms.infrastructure.persistence.diagtemplate.DiagnosticTemplateJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Runs diagnostic + billing integration in a SEPARATE transaction (REQUIRES_NEW)
 * so that failures don't poison the caller's transaction with rollback-only.
 *
 * Flow (matching SCMC reference):
 *   1. Ensure a draft bill exists for the encounter
 *   2. Resolve diagnosticTestId (which might be a DiagnosticTemplate ID) to its Service Catalog (Charge) ID
 *   3. Place formal diagnostic order(s) linked to the bill using the resolved Service Catalog ID
 *   4. Diagnostic orders are dynamically injected onto the draft bill via the existing hydrate/inject virtual line logic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DiagnosticBillingIntegrationHelper {

    private final DiagnosticOrderingService diagnosticOrderingService;
    private final BillingOperationsService billingService;
    private final DiagnosticTemplateJpaRepository templateRepo;

    /**
     * Creates/finds a draft bill, and places diagnostic orders.
     * Splits items by LAB vs RADIOLOGY category.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void placeDiagnosticOrderAndBill(
            List<AddDiagnosticOrderRequest.DiagnosticOrderLineRequest> items,
            UUID patientId,
            UUID encounterId,
            EncounterType encounterType,
            UUID requestedById) {

        if (items == null || items.isEmpty()) return;

        // Step 1: Ensure draft bill exists
        UUID billId = null;
        try {
            BillResponse bill = billingService.ensureDraftBill(patientId, encounterId, encounterType, requestedById);
            if (bill != null) {
                billId = bill.id();
                log.info("Draft bill {} ensured for encounter {}", billId, encounterId);
            }
        } catch (Exception ex) {
            log.warn("Could not ensure draft bill for encounter {}: {}", encounterId, ex.getMessage());
        }

        final UUID finalBillId = billId;

        // Step 2: Split items by category (LAB vs RADIOLOGY) & resolve IDs to charge/service catalog IDs
        List<PlaceOrderRequest.OrderLineRequest> labLines = new ArrayList<>();
        List<PlaceOrderRequest.OrderLineRequest> radioLines = new ArrayList<>();

        for (var item : items) {
            UUID serviceCatalogItemId = resolveServiceCatalogItemId(item.diagnosticTestId());
            if (serviceCatalogItemId == null) {
                log.warn("Could not resolve service catalog item ID for testId={}", item.diagnosticTestId());
                continue;
            }

            PlaceOrderRequest.OrderLineRequest line = new PlaceOrderRequest.OrderLineRequest(
                    serviceCatalogItemId, item.testName(), null, null);

            if ("RADIOLOGY".equalsIgnoreCase(item.category())) {
                radioLines.add(line);
            } else {
                labLines.add(line);
            }
        }

        // Step 3: Place diagnostic orders with billId linkage
        if (!labLines.isEmpty()) {
            try {
                diagnosticOrderingService.placeOrder(new PlaceOrderRequest(
                        encounterId, patientId, requestedById,
                        DiagnosticType.LAB, finalBillId, labLines));
            } catch (Exception ex) {
                log.warn("Failed to place LAB order for encounter {}: {}", encounterId, ex.getMessage());
            }
        }

        if (!radioLines.isEmpty()) {
            try {
                diagnosticOrderingService.placeOrder(new PlaceOrderRequest(
                        encounterId, patientId, requestedById,
                        DiagnosticType.RADIOLOGY, finalBillId, radioLines));
            } catch (Exception ex) {
                log.warn("Failed to place RADIOLOGY order for encounter {}: {}", encounterId, ex.getMessage());
            }
        }
    }

    private UUID resolveServiceCatalogItemId(String diagnosticTestId) {
        UUID rawId = parseUUID(diagnosticTestId);
        if (rawId == null) return null;

        // Check if it's a DiagnosticTemplate ID. If so, get its mapped charge_id
        var templateOpt = templateRepo.findById(rawId);
        if (templateOpt.isPresent() && templateOpt.get().getChargeId() != null) {
            return templateOpt.get().getChargeId();
        }

        // Otherwise, the ID from the frontend is already the serviceCatalogItemId (e.g. from favorites/quickadd)
        return rawId;
    }

    private static UUID parseUUID(String s) {
        if (s == null || s.isBlank()) return null;
        try { return UUID.fromString(s); } catch (Exception e) { return null; }
    }
}
