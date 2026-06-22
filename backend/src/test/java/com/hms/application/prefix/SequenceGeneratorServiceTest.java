package com.hms.application.prefix;

import com.hms.api.prefix.request.CreateSequenceGeneratorRequest;
import com.hms.api.prefix.request.UpdateSequenceGeneratorRequest;
import com.hms.api.prefix.response.SequenceGeneratorResponse;
import com.hms.domain.billing.model.DocumentType;
import com.hms.domain.billing.model.SequenceResetPolicy;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.infrastructure.sequence.SequenceGeneratorEntity;
import com.hms.infrastructure.sequence.SequenceGeneratorJpaRepository;
import com.hms.infrastructure.tenant.TenantContext;
import com.hms.infrastructure.tenant.BranchContext;
import com.hms.infrastructure.persistence.tenant.BranchJpaRepository;
import com.hms.infrastructure.persistence.tenant.BranchEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SequenceGeneratorServiceTest {

    @Mock
    private SequenceGeneratorJpaRepository repo;

    @Mock
    private BranchJpaRepository branchRepo;

    @InjectMocks
    private SequenceGeneratorService service;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
        BranchContext.set(BRANCH_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        BranchContext.clear();
    }

    @Test
    void testCreatePatientGenerator_ScopesToTenantOnly() {
        // Arrange
        CreateSequenceGeneratorRequest req = new CreateSequenceGeneratorRequest(
            "PAT-", DocumentType.PATIENT, SequenceResetPolicy.NEVER
        );

        when(repo.findActiveByDocumentTypeTenantAndBranchForUpdate(DocumentType.PATIENT, TENANT_ID, null))
            .thenReturn(Optional.empty());
        when(repo.findConflictingPrefixes("PAT-", TENANT_ID, null, true))
            .thenReturn(Collections.emptyList());
        when(repo.save(any(SequenceGeneratorEntity.class))).thenAnswer(invocation -> {
            SequenceGeneratorEntity e = invocation.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        // Act
        SequenceGeneratorResponse response = service.create(req);

        // Assert
        assertNotNull(response);
        verify(repo).findActiveByDocumentTypeTenantAndBranchForUpdate(DocumentType.PATIENT, TENANT_ID, null);
        verify(repo).findConflictingPrefixes("PAT-", TENANT_ID, null, true);
        verify(repo).save(argThat(entity -> 
            entity.getTenantId().equals(TENANT_ID) && entity.getBranchId() == null && entity.isActivated()
        ));
    }

    @Test
    void testCreateBillGenerator_ScopesToTenantAndBranch() {
        // Arrange
        CreateSequenceGeneratorRequest req = new CreateSequenceGeneratorRequest(
            "BILL-", DocumentType.BILL, SequenceResetPolicy.NEVER
        );

        when(repo.findActiveByDocumentTypeTenantAndBranchForUpdate(DocumentType.BILL, TENANT_ID, BRANCH_ID))
            .thenReturn(Optional.empty());
        when(repo.findConflictingPrefixes("BILL-", TENANT_ID, BRANCH_ID, false))
            .thenReturn(Collections.emptyList());
        when(repo.save(any(SequenceGeneratorEntity.class))).thenAnswer(invocation -> {
            SequenceGeneratorEntity e = invocation.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        // Act
        SequenceGeneratorResponse response = service.create(req);

        // Assert
        assertNotNull(response);
        verify(repo).findActiveByDocumentTypeTenantAndBranchForUpdate(DocumentType.BILL, TENANT_ID, BRANCH_ID);
        verify(repo).findConflictingPrefixes("BILL-", TENANT_ID, BRANCH_ID, false);
        verify(repo).save(argThat(entity -> 
            entity.getTenantId().equals(TENANT_ID) && entity.getBranchId().equals(BRANCH_ID) && entity.isActivated()
        ));
    }

    @Test
    void testCreateConflictingPrefix_ThrowsException() {
        // Arrange
        CreateSequenceGeneratorRequest req = new CreateSequenceGeneratorRequest(
            "PAT-", DocumentType.PATIENT, SequenceResetPolicy.NEVER
        );

        SequenceGeneratorEntity existing = new SequenceGeneratorEntity();
        existing.setActivated(true);
        existing.setDocumentType(DocumentType.PATIENT);

        when(repo.findActiveByDocumentTypeTenantAndBranchForUpdate(DocumentType.PATIENT, TENANT_ID, null))
            .thenReturn(Optional.empty());
        when(repo.findConflictingPrefixes("PAT-", TENANT_ID, null, true))
            .thenReturn(List.of(existing));

        // Act & Assert
        assertThrows(BusinessRuleViolationException.class, () -> service.create(req));
    }
}
