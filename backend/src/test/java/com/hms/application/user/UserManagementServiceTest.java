package com.hms.application.user;

import com.hms.api.user.request.CreateUserRequest;
import com.hms.api.user.response.UserResponse;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.infrastructure.persistence.department.DepartmentJpaRepository;
import com.hms.infrastructure.persistence.role.RoleJpaRepository;
import com.hms.infrastructure.persistence.shared.RoleEntity;
import com.hms.infrastructure.persistence.shared.UserEntity;
import com.hms.infrastructure.persistence.shared.UserJpaRepository;
import com.hms.infrastructure.persistence.tenant.BranchEntity;
import com.hms.infrastructure.persistence.tenant.BranchJpaRepository;
import com.hms.infrastructure.tenant.BranchContext;
import com.hms.infrastructure.tenant.TenantContext;
import com.hms.security.FeaturePermissionCacheService;
import com.hms.security.HmsUserDetails;
import com.hms.security.encryption.PiiSearchTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

    @Mock private UserJpaRepository userRepo;
    @Mock private RoleJpaRepository roleRepo;
    @Mock private DepartmentJpaRepository departmentRepo;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private com.hms.infrastructure.persistence.consultant.ConsultantJpaRepository consultantRepo;
    @Mock private FeaturePermissionCacheService permissionCache;
    @Mock private BranchJpaRepository branchRepo;
    @Mock private PiiSearchTokenService tokenService;

    @Mock private SecurityContext securityContext;
    @Mock private Authentication authentication;
    @Mock private HmsUserDetails userDetails;

    @InjectMocks
    private UserManagementService userService;

    private UUID tenantId;
    private UUID branchId;
    private MockedStatic<TenantContext> tenantContextMock;
    private MockedStatic<BranchContext> branchContextMock;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        branchId = UUID.randomUUID();
        tenantContextMock = mockStatic(TenantContext.class);
        tenantContextMock.when(TenantContext::get).thenReturn(tenantId);
        
        branchContextMock = mockStatic(BranchContext.class);
        branchContextMock.when(BranchContext::get).thenReturn(branchId);

        SecurityContextHolder.setContext(securityContext);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.getPrincipal()).thenReturn(userDetails);
        lenient().when(userDetails.isSuperAdmin()).thenReturn(false);
        lenient().when(userDetails.isHospitalAdmin()).thenReturn(true);
        lenient().when(userDetails.getBranchId()).thenReturn(branchId);
    }

    @AfterEach
    void tearDown() {
        tenantContextMock.close();
        branchContextMock.close();
        SecurityContextHolder.clearContext();
    }

    @Test
    void createUser_ShouldSaveNewUser() {
        CreateUserRequest req = new CreateUserRequest("johndoe", "pass123", "John", "Doe", "j@j.com", Set.of(UUID.randomUUID()), Set.of(), Set.of(), null, false, "en-IN", "Mr", "9999", branchId, Set.of());

        lenient().when(userRepo.findByUsername("johndoe")).thenReturn(Optional.empty());
        lenient().when(tokenService.phoneToken("9999")).thenReturn("t9999");
        lenient().when(tokenService.token("j@j.com")).thenReturn("tj@j.com");
        
        RoleEntity role = new RoleEntity();
        role.setName("DOCTOR");
        lenient().when(roleRepo.findAllById(any())).thenReturn(List.of(role));
        
        BranchEntity branch = new BranchEntity();
        branch.setId(branchId);
        lenient().when(branchRepo.findByIdAndTenantId(branchId, tenantId)).thenReturn(Optional.of(branch));
        
        lenient().when(userRepo.save(any(UserEntity.class))).thenAnswer(i -> {
            UserEntity u = i.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        UserResponse response = userService.createUser(req);

        assertNotNull(response);
        assertEquals("johndoe", response.username());
        verify(userRepo).save(any(UserEntity.class));
        verify(permissionCache).rebuildCacheForTenant(tenantId);
    }

    @Test
    void createUser_ShouldThrowException_WhenUsernameExists() {
        CreateUserRequest req = new CreateUserRequest("johndoe", "pass123", "John", "Doe", "j@j.com", Set.of(UUID.randomUUID()), Set.of(), Set.of(), null, false, "en-IN", "Mr", "9999", null, Set.of());

        UserEntity existing = new UserEntity();
        existing.setTenantId(UUID.randomUUID()); // Different tenant
        
        lenient().when(userRepo.findByUsername("johndoe")).thenReturn(Optional.of(existing));

        assertThrows(BusinessRuleViolationException.class, () -> userService.createUser(req));
    }
}
