package com.hms.application.role;

import com.hms.api.role.request.CreateRoleRequest;
import com.hms.api.role.response.RoleResponse;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.CrossTenantAccessException;
import com.hms.infrastructure.persistence.shared.FeatureEntity;
import com.hms.infrastructure.persistence.shared.FeatureJpaRepository;
import com.hms.infrastructure.persistence.shared.RoleEntity;
import com.hms.infrastructure.persistence.role.RoleJpaRepository;
import com.hms.infrastructure.tenant.BranchContext;
import com.hms.infrastructure.tenant.TenantContext;
import com.hms.security.FeaturePermissionCacheService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleManagementServiceTest {

    @Mock private RoleJpaRepository roleRepo;
    @Mock private FeatureJpaRepository featureRepo;
    @Mock private FeaturePermissionCacheService permissionCacheService;

    @InjectMocks
    private RoleManagementService roleService;

    private UUID tenantId;
    private UUID branchId;
    private MockedStatic<TenantContext> tenantContextMock;
    private MockedStatic<BranchContext> branchContextMock;
    
    private RoleEntity role;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        branchId = UUID.randomUUID();
        tenantContextMock = mockStatic(TenantContext.class);
        tenantContextMock.when(TenantContext::require).thenReturn(tenantId);
        
        branchContextMock = mockStatic(BranchContext.class);
        branchContextMock.when(BranchContext::require).thenReturn(branchId);
        branchContextMock.when(BranchContext::get).thenReturn(branchId);
        
        role = new RoleEntity();
        role.setId(UUID.randomUUID());
        role.setName("Admin");
        role.setBranchId(branchId);
        role.setTenantId(tenantId);
        role.setFeatures(new HashSet<>());
    }
    
    @AfterEach
    void tearDown() {
        tenantContextMock.close();
        branchContextMock.close();
    }

    @Test
    void createRole_ShouldSaveAndReturnResponse() {
        CreateRoleRequest req = new CreateRoleRequest("Admin", "Admin Role", Set.of(UUID.randomUUID(), UUID.randomUUID()));

        when(roleRepo.findByNameAndTenantIdAndBranchId("Admin", tenantId, branchId)).thenReturn(Optional.empty());
        
        FeatureEntity f1 = new FeatureEntity();
        f1.setId(UUID.randomUUID());
        f1.setTenantId(tenantId);
        FeatureEntity f2 = new FeatureEntity();
        f2.setId(UUID.randomUUID());
        f2.setTenantId(tenantId);
        
        when(featureRepo.findAllById(any())).thenReturn(List.of(f1, f2));
        when(roleRepo.save(any(RoleEntity.class))).thenReturn(role);

        RoleResponse response = roleService.createRole(req);

        assertNotNull(response);
        verify(roleRepo).save(any(RoleEntity.class));
        verify(permissionCacheService).rebuildCacheForTenant(tenantId);
    }

    @Test
    void createRole_ShouldThrowException_WhenRoleExists() {
        CreateRoleRequest req = new CreateRoleRequest("Admin", "Admin Role", Set.of(UUID.randomUUID()));

        when(roleRepo.findByNameAndTenantIdAndBranchId("Admin", tenantId, branchId)).thenReturn(Optional.of(role));

        assertThrows(BusinessRuleViolationException.class, () -> roleService.createRole(req));
    }

    @Test
    void createRole_ShouldThrowException_WhenFeatureCrossTenant() {
        CreateRoleRequest req = new CreateRoleRequest("Admin", "Admin Role", Set.of(UUID.randomUUID()));

        when(roleRepo.findByNameAndTenantIdAndBranchId("Admin", tenantId, branchId)).thenReturn(Optional.empty());
        
        FeatureEntity f1 = new FeatureEntity();
        f1.setId(UUID.randomUUID());
        f1.setTenantId(UUID.randomUUID()); // Different tenant
        
        when(featureRepo.findAllById(any())).thenReturn(List.of(f1));

        assertThrows(CrossTenantAccessException.class, () -> roleService.createRole(req));
    }
}
