package com.hms.application.tenant;

import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.role.RoleJpaRepository;
import com.hms.infrastructure.persistence.shared.FeatureJpaRepository;
import com.hms.infrastructure.persistence.shared.RoleEntity;
import com.hms.infrastructure.persistence.shared.UserEntity;
import com.hms.infrastructure.persistence.shared.UserJpaRepository;
import com.hms.infrastructure.persistence.tenant.BranchEntity;
import com.hms.infrastructure.persistence.tenant.BranchJpaRepository;
import com.hms.infrastructure.persistence.tenant.TenantEntity;
import com.hms.infrastructure.persistence.tenant.TenantJpaRepository;
import com.hms.security.FeaturePermissionCacheService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import jakarta.persistence.Query;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock private TenantJpaRepository tenantRepo;
    @Mock private BranchJpaRepository branchRepo;
    @Mock private RoleJpaRepository roleRepo;
    @Mock private FeatureJpaRepository featureRepo;
    @Mock private FeaturePermissionCacheService permissionCache;
    @Mock private UserJpaRepository userRepo;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EntityManager entityManager;

    @InjectMocks
    private TenantService tenantService;

    private UUID tenantId;
    private TenantEntity tenant;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        tenant = new TenantEntity();
        tenant.setId(tenantId);
        tenant.setSlug("test-hospital");
        tenant.setName("Test Hospital");
    }

    @Test
    void create_ShouldSaveTenantAndDefaultBranch() {
        lenient().when(tenantRepo.existsBySlug("test-hospital")).thenReturn(false);
        lenient().when(tenantRepo.save(any(TenantEntity.class))).thenReturn(tenant);
        
        BranchEntity b = new BranchEntity();
        b.setId(UUID.randomUUID());
        lenient().when(branchRepo.findByTenantIdAndIsDefaultTrue(tenantId)).thenReturn(Optional.empty());
        lenient().when(branchRepo.save(any(BranchEntity.class))).thenReturn(b);
        
        Query mockQuery = mock(Query.class);
        lenient().when(entityManager.createNativeQuery(anyString())).thenReturn(mockQuery);
        lenient().when(mockQuery.setParameter(anyInt(), any())).thenReturn(mockQuery);
        lenient().when(mockQuery.executeUpdate()).thenReturn(1);


        TenantEntity result = tenantService.create("test-hospital", "Test Hospital", "Desc", "Addr", "12345");

        assertNotNull(result);
        verify(tenantRepo).save(any(TenantEntity.class));
        verify(branchRepo).save(any(BranchEntity.class));
    }

    @Test
    void create_ShouldThrowException_WhenSlugExists() {
        lenient().when(tenantRepo.existsBySlug("test-hospital")).thenReturn(true);
        
        assertThrows(BusinessRuleViolationException.class, () -> 
            tenantService.create("test-hospital", "Test", null, null, null));
    }

    @Test
    void provisionHospitalAdmin_ShouldCreateAdminUser() {
        lenient().when(userRepo.existsByUsername("admin")).thenReturn(false);
        
        RoleEntity hospitalAdmin = new RoleEntity();
        hospitalAdmin.setName("HOSPITAL_ADMIN");
        lenient().when(roleRepo.findByNameAndTenantId("HOSPITAL_ADMIN", tenantId)).thenReturn(Optional.of(hospitalAdmin));
        
        lenient().when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        lenient().when(userRepo.save(any(UserEntity.class))).thenAnswer(i -> i.getArgument(0));

        UserEntity result = tenantService.provisionHospitalAdmin(tenantId, "admin", "password", "Super", "Admin");

        assertNotNull(result);
        assertEquals("admin", result.getUsername());
        assertEquals("hashed", result.getPasswordHash());
        assertNull(result.getBranchId()); // Hospital admins should be tenant-wide
        verify(permissionCache).rebuildCacheForTenant(tenantId);
    }
    
    @Test
    void get_ShouldReturnTenant() {
        lenient().when(tenantRepo.findById(tenantId)).thenReturn(Optional.of(tenant));
        
        TenantEntity result = tenantService.get(tenantId);
        
        assertEquals("Test Hospital", result.getName());
    }

    @Test
    void update_ShouldUpdateAndSave() {
        lenient().when(tenantRepo.findById(tenantId)).thenReturn(Optional.of(tenant));
        lenient().when(tenantRepo.save(any(TenantEntity.class))).thenReturn(tenant);
        
        TenantEntity result = tenantService.update(tenantId, "New Name", null, null, null, (short)0);
        
        assertEquals("New Name", result.getName());
        assertEquals((short)0, result.getStatus());
        verify(tenantRepo).save(tenant);
    }
}
