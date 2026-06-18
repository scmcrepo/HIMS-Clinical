package com.hms.application.bed;

import com.hms.api.billing.response.BillResponse;
import com.hms.application.billing.BillingOperationsService;
import com.hms.domain.billing.model.EncounterType;
import com.hms.domain.billing.model.BillType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Runs bed allocation + billing integration in a SEPARATE transaction (REQUIRES_NEW)
 * so that billing injection failures (such as a bed already being open on a draft bill)
 * do not poison/rollback the primary bed allocation transaction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BedBillingIntegrationHelper {

    private final BillingOperationsService billingService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void autoInjectBedCharge(UUID patientId, UUID encounterId, UUID providerId, String billTypeStr, UUID payorId, UUID bedId, Instant startAt) {
        log.info("autoInjectBedCharge [REQUIRES_NEW] - Patient: {}, Bed: {}", patientId, bedId);
        BillType bt = BillType.CASH;
        if (billTypeStr != null && !billTypeStr.isBlank()) {
            try {
                bt = BillType.valueOf(billTypeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid billType passed in bed allocation: {}, falling back to CASH", billTypeStr);
            }
        }

        var bill = billingService.ensureDraftBill(
                patientId,
                encounterId,
                EncounterType.INPATIENT,
                providerId,
                bt,
                payorId
        );
        billingService.injectBedCharge(bill.id(), bedId, startAt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void autoInjectBedChargeOnTransfer(UUID patientId, UUID encounterId, UUID providerId, UUID newBedId, Instant transferInstant) {
        log.info("autoInjectBedChargeOnTransfer [REQUIRES_NEW] - Encounter: {}, Bed: {}", encounterId, newBedId);
        var bill = billingService.ensureDraftBill(
                patientId,
                encounterId,
                EncounterType.INPATIENT,
                providerId
        );
        billingService.injectBedCharge(bill.id(), newBedId, transferInstant);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void autoCloseBedChargeOnRelease(UUID patientId, UUID encounterId, UUID providerId, Instant now) {
        log.info("autoCloseBedChargeOnRelease [REQUIRES_NEW] - Encounter: {}", encounterId);
        var bill = billingService.ensureDraftBill(
                patientId,
                encounterId,
                EncounterType.INPATIENT,
                providerId
        );
        if (bill != null) {
            billingService.closeActiveBedCharge(bill.id(), now);
        }
    }
}
