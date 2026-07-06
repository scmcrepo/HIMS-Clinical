package com.hms.application.bulkupload;

import com.hms.domain.shared.port.out.SequenceNumberPort;
import com.hms.infrastructure.persistence.bed.BedJpaRepository;
import com.hms.infrastructure.persistence.bed.BedOccupancyJpaRepository;
import com.hms.infrastructure.persistence.bed.RoomCategoryJpaRepository;
import com.hms.infrastructure.persistence.bulkupload.BulkImportJobEntity;
import com.hms.infrastructure.persistence.bulkupload.BulkImportJobJpaRepository;
import com.hms.infrastructure.persistence.catalog.ServiceCatalogItemJpaRepository;
import com.hms.infrastructure.persistence.catalog.ServiceCategoryJpaRepository;
import com.hms.infrastructure.persistence.category.CategoryJpaRepository;
import com.hms.infrastructure.persistence.charge.ChargeJpaRepository;
import com.hms.infrastructure.persistence.consultant.ConsultantJpaRepository;
import com.hms.infrastructure.persistence.department.DepartmentJpaRepository;
import com.hms.infrastructure.persistence.diagtemplate.DiagnosticTemplateJpaRepository;
import com.hms.infrastructure.persistence.diagtemplate.LabTemplateDetailJpaRepository;
import com.hms.infrastructure.persistence.inventory.InventoryBatchJpaRepository;
import com.hms.infrastructure.persistence.inventory.InventoryItemJpaRepository;
import com.hms.infrastructure.persistence.inventory.UnitOfMeasureJpaRepository;
import com.hms.infrastructure.persistence.molecule.MoleculeJpaRepository;
import com.hms.infrastructure.persistence.patient.PatientJpaRepository;
import com.hms.infrastructure.persistence.payor.PayorJpaRepository;
import com.hms.infrastructure.persistence.printtemplate.PrintTemplateJpaRepository;
import com.hms.infrastructure.persistence.referral.ReferralJpaRepository;
import com.hms.infrastructure.persistence.role.RoleJpaRepository;
import com.hms.infrastructure.persistence.shared.UserJpaRepository;
import com.hms.infrastructure.persistence.specimen.SpecimenJpaRepository;
import com.hms.infrastructure.persistence.staff.StaffJpaRepository;
import com.hms.infrastructure.persistence.supplier.SupplierJpaRepository;
import com.hms.infrastructure.persistence.tenant.BranchJpaRepository;
import com.hms.infrastructure.sequence.NumberSequenceJpaRepository;
import com.hms.infrastructure.tenant.BranchContext;
import com.hms.infrastructure.tenant.TenantContext;
import com.hms.security.encryption.PiiSearchTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BulkImportServiceTest {

    @Mock private BedJpaRepository bedRepo;
    @Mock private BedOccupancyJpaRepository occupancyRepo;
    @Mock private InventoryItemJpaRepository itemRepo;
    @Mock private PatientJpaRepository patientRepo;
    @Mock private ReferralJpaRepository referralRepo;
    @Mock private SupplierJpaRepository supplierRepo;
    @Mock private UserJpaRepository userRepo;
    @Mock private RoleJpaRepository roleRepo;
    @Mock private BranchJpaRepository branchRepo;
    @Mock private ConsultantJpaRepository consultantRepo;
    @Mock private StaffJpaRepository staffRepo;
    @Mock private DepartmentJpaRepository departmentRepo;
    @Mock private CategoryJpaRepository categoryRepo;
    @Mock private MoleculeJpaRepository moleculeRepo;
    @Mock private UnitOfMeasureJpaRepository uomRepo;
    @Mock private RoomCategoryJpaRepository roomCategoryRepo;
    @Mock private PayorJpaRepository payorRepo;
    @Mock private DiagnosticTemplateJpaRepository diagnosticTemplateRepo;
    @Mock private ChargeJpaRepository chargeRepo;
    @Mock private InventoryBatchJpaRepository batchRepo;
    @Mock private ServiceCatalogItemJpaRepository catalogItemRepo;
    @Mock private ServiceCategoryJpaRepository serviceCategoryRepo;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private SpecimenJpaRepository specimenRepo;
    @Mock private LabTemplateDetailJpaRepository labDetailRepo;
    @Mock private PrintTemplateJpaRepository printTemplateRepo;
    @Mock private SequenceNumberPort sequencePort;
    @Mock private NumberSequenceJpaRepository numberSequenceRepo;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private PiiSearchTokenService tokenService;
    @Mock private BulkImportJobJpaRepository jobRepo;
    @Mock private BulkImportAsyncService asyncService;

    @InjectMocks
    private BulkImportService bulkImportService;

    private MockedStatic<TenantContext> mockedTenantContext;
    private MockedStatic<BranchContext> mockedBranchContext;
    private UUID tenantId;
    private UUID branchId;
    private BulkImportJobEntity jobEntity;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        branchId = UUID.randomUUID();

        mockedTenantContext = mockStatic(TenantContext.class);
        mockedTenantContext.when(TenantContext::require).thenReturn(tenantId);
        mockedTenantContext.when(TenantContext::get).thenReturn(tenantId);

        mockedBranchContext = mockStatic(BranchContext.class);
        mockedBranchContext.when(BranchContext::get).thenReturn(branchId);

        jobEntity = new BulkImportJobEntity();
        jobEntity.setId(UUID.randomUUID());
        jobEntity.setJobStatus("PENDING");

        org.springframework.test.util.ReflectionTestUtils.setField(bulkImportService, "asyncService", asyncService);
    }

    @AfterEach
    void tearDown() {
        mockedTenantContext.close();
        mockedBranchContext.close();
    }

    @Test
    void getExpectedHeaders_ShouldReturnHeaders_ForValidType() {
        var headers = bulkImportService.getExpectedHeaders("patient");
        assertFalse(headers.isEmpty());
        assertTrue(headers.contains("First Name"));
    }

    @Test
    void getExpectedHeaders_ShouldThrowException_ForInvalidType() {
        assertThrows(com.hms.exception.BusinessRuleViolationException.class, () -> bulkImportService.getExpectedHeaders("invalid_type"));
    }

    @Test
    void submitImportJob_ShouldCreateJobAndTriggerAsync() {
        String csvContent = "Name,CIMS Id\nMol1,CIMS01\nMol2,CIMS02";
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", csvContent.getBytes());

        when(jobRepo.save(any(BulkImportJobEntity.class))).thenReturn(jobEntity);

        UUID jobId = bulkImportService.submitImportJob("molecule", file);

        System.out.println("jobId=" + jobId);
        System.out.println("tenantId=" + tenantId);
        System.out.println("branchId=" + branchId);

        assertNotNull(jobId);
        verify(jobRepo, atLeastOnce()).save(any(BulkImportJobEntity.class));
        verify(asyncService).processImportAsync(eq(jobId), eq("molecule"), anyList(), eq(tenantId), eq(branchId));
    }

    @Test
    void getJob_ShouldReturnJob() {
        UUID jobId = UUID.randomUUID();
        when(jobRepo.findById(jobId)).thenReturn(Optional.of(jobEntity));
        
        var result = bulkImportService.getJob(jobId);
        
        assertTrue(result.isPresent());
        assertEquals(jobEntity, result.get());
    }

    @Test
    void markJobAsFailed_ShouldUpdateStatus() {
        UUID jobId = UUID.randomUUID();
        when(jobRepo.findById(jobId)).thenReturn(Optional.of(jobEntity));

        bulkImportService.markJobAsFailed(jobId, "Error msg");

        assertEquals("FAILED", jobEntity.getJobStatus());
        assertFalse(jobEntity.getErrors().isEmpty());
        assertEquals("Error msg", jobEntity.getErrors().get(0));
        verify(jobRepo).save(jobEntity);
    }
}
