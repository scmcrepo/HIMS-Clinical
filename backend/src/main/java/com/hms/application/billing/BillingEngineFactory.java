package com.hms.application.billing;
import com.hms.domain.billing.model.*;
import com.hms.domain.billing.service.BillingEngine;
import com.hms.domain.shared.port.out.SequenceNumberPort;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.billing.BillJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import java.util.UUID;
@Component @RequiredArgsConstructor
public class BillingEngineFactory {
    private final BillJpaRepository billRepo;
    private final SequenceNumberPort sequenceNumberPort;
    private final ApplicationEventPublisher eventPublisher;
    public BillingEngine createDraft(UUID patientId, BillType billType, EncounterType encounterType, UUID providerId) {
        Bill bill = new Bill();
        bill.setPatientId(patientId);
        bill.setBillType(billType);
        bill.setEncounterType(encounterType);
        bill.setPrimaryProviderId(providerId);
        bill.setBillStatus(BillStatus.DRAFT);
        bill.setBillDate(java.time.LocalDate.now());
        return new BillingEngine(bill, true, sequenceNumberPort, eventPublisher);
    }
    public BillingEngine attach(UUID billId) {
        Bill bill = billRepo.findByIdForUpdate(billId)
            .orElseThrow(() -> new ResourceNotFoundException("Bill", billId));
        return new BillingEngine(bill, sequenceNumberPort, eventPublisher);
    }
}
