package com.hms.application.encounter;

import com.hms.api.billing.response.BillResponse;
import com.hms.api.diagnostic.request.PlaceOrderRequest;
import com.hms.api.opip.request.AddDiagnosticOrderRequest;
import com.hms.application.billing.BillingOperationsService;
import com.hms.application.diagnostic.DiagnosticOrderingService;
import com.hms.domain.billing.model.EncounterType;
import com.hms.domain.diagnostic.model.DiagnosticType;
import com.hms.infrastructure.persistence.catalog.ServiceCatalogItemJpaRepository;
import com.hms.infrastructure.persistence.charge.ChargeJpaRepository;
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
    private final ServiceCatalogItemJpaRepository serviceCatalogItemRepo;
    private final ChargeJpaRepository chargeRepo;

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
            UUID serviceCatalogItemId = resolveServiceCatalogItemId(item.diagnosticTestId(), item.testName());
            if (serviceCatalogItemId == null) {
                log.warn("Could not resolve service catalog item ID for testId={}, testName={}", item.diagnosticTestId(), item.testName());
                continue;
            }

            String category = resolveCategory(item.category(), item.diagnosticTestId(), item.testName());

            PlaceOrderRequest.OrderLineRequest line = new PlaceOrderRequest.OrderLineRequest(
                    serviceCatalogItemId, item.testName(), null, null);

            if ("RADIOLOGY".equalsIgnoreCase(category)) {
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
                log.info("Placed LAB order for encounter {} with {} line(s)", encounterId, labLines.size());
            } catch (Exception ex) {
                log.warn("Failed to place LAB order for encounter {}: {}", encounterId, ex.getMessage(), ex);
            }
        }

        if (!radioLines.isEmpty()) {
            try {
                diagnosticOrderingService.placeOrder(new PlaceOrderRequest(
                        encounterId, patientId, requestedById,
                        DiagnosticType.RADIOLOGY, finalBillId, radioLines));
                log.info("Placed RADIOLOGY order for encounter {} with {} line(s)", encounterId, radioLines.size());
            } catch (Exception ex) {
                log.warn("Failed to place RADIOLOGY order for encounter {}: {}", encounterId, ex.getMessage(), ex);
            }
        }
    }

    private String resolveCategory(String categoryHint, String diagnosticTestId, String testName) {
        if (categoryHint != null && !categoryHint.isBlank()) {
            return categoryHint;
        }

        UUID rawId = parseUUID(diagnosticTestId);
        if (rawId != null) {
            var templateOpt = templateRepo.findById(rawId);
            if (templateOpt.isPresent() && templateOpt.get().getDiagnosticType() != null) {
                return templateOpt.get().getDiagnosticType().name();
            }
        }

        if (testName != null && !testName.isBlank()) {
            String lower = testName.toLowerCase();
            if (lower.contains("xray") || lower.contains("x-ray") || lower.contains("ct scan") ||
                lower.contains("mri") || lower.contains("ultrasound") || lower.contains("usg") ||
                lower.contains("radiology") || lower.contains("ecg") || lower.contains("echo")) {
                return "RADIOLOGY";
            }
        }

        return "LAB";
    }

    private UUID resolveServiceCatalogItemId(String diagnosticTestId, String testName) {
        UUID rawId = parseUUID(diagnosticTestId);

        if (rawId != null) {
            // 1. Direct check in ServiceCatalogItem repo
            if (serviceCatalogItemRepo.existsById(rawId)) {
                return rawId;
            }

            // 2. Direct check in Charge repo
            if (chargeRepo.existsById(rawId)) {
                return rawId;
            }

            // 3. Check if rawId is a DiagnosticTemplate ID
            var templateOpt = templateRepo.findById(rawId);
            if (templateOpt.isPresent()) {
                var template = templateOpt.get();
                UUID chargeId = template.getChargeId();
                if (chargeId != null) {
                    if (serviceCatalogItemRepo.existsById(chargeId)) return chargeId;
                    if (chargeRepo.existsById(chargeId)) return chargeId;
                }

                if (template.getName() != null) {
                    var items = serviceCatalogItemRepo.findActiveByNameIgnoreCase(template.getName());
                    if (!items.isEmpty()) return items.get(0).getId();

                    var charges = chargeRepo.findByNameIgnoreCase(template.getName());
                    if (!charges.isEmpty()) return charges.get(0).getId();
                }
            }
        }

        // 4. Fallback lookup by testName
        if (testName != null && !testName.isBlank()) {
            var items = serviceCatalogItemRepo.findActiveByNameIgnoreCase(testName);
            if (!items.isEmpty()) return items.get(0).getId();

            var charges = chargeRepo.findByNameIgnoreCase(testName);
            if (!charges.isEmpty()) return charges.get(0).getId();
        }

        // 5. Ultimate fallback: if rawId is a valid UUID, return rawId so order creation doesn't fail
        if (rawId != null) {
            return rawId;
        }

        return null;
    }

    private static UUID parseUUID(String s) {
        if (s == null || s.isBlank()) return null;
        try { return UUID.fromString(s); } catch (Exception e) { return null; }
    }
}
