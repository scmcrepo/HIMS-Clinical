package com.hms.application.tenant;

import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.role.RoleJpaRepository;
import com.hms.infrastructure.persistence.tenant.BranchEntity;
import com.hms.infrastructure.persistence.tenant.BranchJpaRepository;
import com.hms.infrastructure.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BranchServiceTest {

    @Mock private BranchJpaRepository branchRepo;
    @Mock private RoleJpaRepository roleRepo;
    @Mock private EntityManager entityManager;

    @InjectMocks
    private BranchService branchService;

    private UUID tenantId;
    private MockedStatic<TenantContext> tenantContextMock;
    private BranchEntity branch;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        tenantContextMock = mockStatic(TenantContext.class);
        tenantContextMock.when(TenantContext::require).thenReturn(tenantId);
        
        branch = new BranchEntity();
        branch.setId(UUID.randomUUID());
        branch.setTenantId(tenantId);
        branch.setCode("BR01");
        branch.setName("Test Branch");
    }

    @AfterEach
    void tearDown() {
        tenantContextMock.close();
    }

    @Test
    void listForCurrentTenant_ShouldReturnList() {
        when(branchRepo.findAllByTenantId(tenantId)).thenReturn(List.of(branch));
        
        List<BranchEntity> result = branchService.listForCurrentTenant();
        
        assertEquals(1, result.size());
        verify(branchRepo).findAllByTenantId(tenantId);
    }

    @Test
    void get_ShouldReturnBranch() {
        when(branchRepo.findByIdAndTenantId(branch.getId(), tenantId)).thenReturn(Optional.of(branch));
        
        BranchEntity result = branchService.get(branch.getId());
        
        assertNotNull(result);
        assertEquals("Test Branch", result.getName());
    }

    @Test
    void get_ShouldThrowException_WhenNotFound() {
        UUID randomId = UUID.randomUUID();
        when(branchRepo.findByIdAndTenantId(randomId, tenantId)).thenReturn(Optional.empty());
        
        assertThrows(ResourceNotFoundException.class, () -> branchService.get(randomId));
    }

    @Test
    void create_ShouldThrowException_WhenCodeExists() {
        when(branchRepo.existsByTenantIdAndCode(tenantId, "BR01")).thenReturn(true);
        
        assertThrows(BusinessRuleViolationException.class, () -> 
            branchService.create("BR01", "Test Branch", "Addr", "1234567890"));
    }

    @Test
    void update_ShouldUpdateFieldsAndSave() {
        when(branchRepo.findByIdAndTenantId(branch.getId(), tenantId)).thenReturn(Optional.of(branch));
        when(branchRepo.save(any(BranchEntity.class))).thenReturn(branch);
        
        BranchEntity result = branchService.update(branch.getId(), "New Name", "New Addr", null, null);
        
        assertEquals("New Name", result.getName());
        assertEquals("New Addr", result.getAddress());
        verify(branchRepo).save(branch);
    }

    @Test
    void update_ShouldThrowException_WhenDeactivatingDefaultBranch() {
        branch.setDefault(true);
        when(branchRepo.findByIdAndTenantId(branch.getId(), tenantId)).thenReturn(Optional.of(branch));
        
        assertThrows(BusinessRuleViolationException.class, () -> 
            branchService.update(branch.getId(), null, null, null, (short)0));
    }
}
