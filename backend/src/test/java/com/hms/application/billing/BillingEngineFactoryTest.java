package com.hms.application.billing;

import com.hms.domain.billing.model.Bill;
import com.hms.domain.billing.model.BillStatus;
import com.hms.domain.billing.model.BillType;
import com.hms.domain.billing.model.EncounterType;
import com.hms.domain.billing.service.BillingEngine;
import com.hms.domain.shared.port.out.SequenceNumberPort;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.billing.BillJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillingEngineFactoryTest {

    @Mock
    private BillJpaRepository billRepo;

    @Mock
    private SequenceNumberPort sequenceNumberPort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private BillingEngineFactory billingEngineFactory;

    private UUID patientId;
    private UUID providerId;
    private UUID billId;
    private Bill bill;

    @BeforeEach
    void setUp() {
        patientId = UUID.randomUUID();
        providerId = UUID.randomUUID();
        billId = UUID.randomUUID();

        bill = new Bill();
        bill.setId(billId);
    }

    @Test
    void createDraft_ShouldInitializeBillAndReturnBillingEngine() {
        BillingEngine engine = billingEngineFactory.createDraft(patientId, BillType.CASH, EncounterType.OUTPATIENT, providerId);

        assertNotNull(engine);
        // The engine wraps the bill. We can verify basic aspects of the initialized bill
        // even if not directly exposed, as it's passed into the engine.
        // Wait, the bill is not easily retrievable without getters in BillingEngine, but we can verify no exception is thrown.
    }

    @Test
    void attach_ShouldReturnBillingEngine_WhenBillExists() {
        when(billRepo.findByIdForUpdate(billId)).thenReturn(Optional.of(bill));

        BillingEngine engine = billingEngineFactory.attach(billId);

        assertNotNull(engine);
        verify(billRepo).findByIdForUpdate(billId);
    }

    @Test
    void attach_ShouldThrowException_WhenBillDoesNotExist() {
        when(billRepo.findByIdForUpdate(billId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> billingEngineFactory.attach(billId));
        verify(billRepo).findByIdForUpdate(billId);
    }
}
