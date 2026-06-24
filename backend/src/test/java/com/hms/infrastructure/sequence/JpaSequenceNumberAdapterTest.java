package com.hms.infrastructure.sequence;

import com.hms.domain.billing.model.DocumentType;
import com.hms.exception.BusinessRuleViolationException;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JpaSequenceNumberAdapterTest {

    @Mock
    private SequenceGeneratorJpaRepository repo;

    @InjectMocks
    private JpaSequenceNumberAdapter adapter;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        BranchContext.clear();
    }

    @Test
    void testGenerateNextPatient_WorksWithTenantAndBranch() {
        // Arrange
        BranchContext.set(BRANCH_ID);
        SequenceGeneratorEntity active = mock(SequenceGeneratorEntity.class);
        when(active.formatAndIncrement()).thenReturn("SCMCP-00001");
        
        when(repo.findActiveByDocumentTypeTenantAndBranchForUpdate(DocumentType.PATIENT, TENANT_ID, BRANCH_ID))
            .thenReturn(Optional.of(active));

        // Act
        String result = adapter.generateNext(DocumentType.PATIENT);

        // Assert
        assertEquals("SCMCP-00001", result);
        verify(repo).findActiveByDocumentTypeTenantAndBranchForUpdate(DocumentType.PATIENT, TENANT_ID, BRANCH_ID);
    }

    @Test
    void testGenerateNextBill_WorksWithTenantAndBranch() {
        // Arrange
        BranchContext.set(BRANCH_ID);
        SequenceGeneratorEntity active = mock(SequenceGeneratorEntity.class);
        when(active.formatAndIncrement()).thenReturn("BILL-00001");
        
        when(repo.findActiveByDocumentTypeTenantAndBranchForUpdate(DocumentType.BILL, TENANT_ID, BRANCH_ID))
            .thenReturn(Optional.of(active));

        // Act
        String result = adapter.generateNext(DocumentType.BILL);

        // Assert
        assertEquals("BILL-00001", result);
        verify(repo).findActiveByDocumentTypeTenantAndBranchForUpdate(DocumentType.BILL, TENANT_ID, BRANCH_ID);
    }

    @Test
    void testGenerateNextBill_ThrowsIfNoBranchInContext() {
        // Arrange (BranchContext is null by default in setup)
        
        // Act & Assert
        assertThrows(IllegalStateException.class, () -> adapter.generateNext(DocumentType.BILL));
        verify(repo, never()).findActiveByDocumentTypeTenantAndBranchForUpdate(any(), any(), any());
    }

    @Test
    void testGenerateNext_ThrowsIfNoGeneratorConfigured() {
        // Arrange
        BranchContext.set(BRANCH_ID);
        when(repo.findActiveByDocumentTypeTenantAndBranchForUpdate(DocumentType.BILL, TENANT_ID, BRANCH_ID))
            .thenReturn(Optional.empty());
        when(repo.findAllByDocumentTypeTenantAndBranch(DocumentType.BILL, TENANT_ID, BRANCH_ID))
            .thenReturn(Collections.emptyList());

        // Act & Assert
        BusinessRuleViolationException exception = assertThrows(
            BusinessRuleViolationException.class, 
            () -> adapter.generateNext(DocumentType.BILL)
        );
        assertTrue(exception.getMessage().contains("No sequence generator configured"));
    }
}
