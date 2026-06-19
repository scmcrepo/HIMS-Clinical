package com.hms.application.bulkupload;

import com.hms.domain.bed.model.Bed;
import com.hms.domain.bed.model.BedStatus;
import com.hms.domain.bed.model.RoomCategory;
import com.hms.domain.charge.model.Charge;
import com.hms.infrastructure.persistence.bed.BedJpaRepository;
import com.hms.infrastructure.persistence.bed.RoomCategoryJpaRepository;
import com.hms.infrastructure.persistence.charge.ChargeJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BulkImportServiceTest {

    @Mock private BedJpaRepository bedRepo;
    @Mock private RoomCategoryJpaRepository roomCategoryRepo;
    @Mock private ChargeJpaRepository chargeRepo;
    @Mock private PasswordEncoder passwordEncoder;
    
    @Mock private com.hms.infrastructure.persistence.bed.BedOccupancyJpaRepository occupancyRepo;
    @Mock private com.hms.infrastructure.persistence.inventory.InventoryItemJpaRepository itemRepo;
    @Mock private com.hms.infrastructure.persistence.patient.PatientJpaRepository patientRepo;
    @Mock private com.hms.infrastructure.persistence.referral.ReferralJpaRepository referralRepo;
    @Mock private com.hms.infrastructure.persistence.supplier.SupplierJpaRepository supplierRepo;
    @Mock private com.hms.infrastructure.persistence.shared.UserJpaRepository userRepo;
    @Mock private com.hms.infrastructure.persistence.role.RoleJpaRepository roleRepo;
    @Mock private com.hms.infrastructure.persistence.consultant.ConsultantJpaRepository consultantRepo;
    @Mock private com.hms.infrastructure.persistence.staff.StaffJpaRepository staffRepo;
    @Mock private com.hms.infrastructure.persistence.department.DepartmentJpaRepository departmentRepo;
    @Mock private com.hms.infrastructure.persistence.category.CategoryJpaRepository categoryRepo;
    @Mock private com.hms.infrastructure.persistence.molecule.MoleculeJpaRepository moleculeRepo;
    @Mock private com.hms.infrastructure.persistence.inventory.UnitOfMeasureJpaRepository uomRepo;
    @Mock private com.hms.infrastructure.persistence.payor.PayorJpaRepository payorRepo;
    @Mock private com.hms.infrastructure.persistence.diagtemplate.DiagnosticTemplateJpaRepository diagnosticTemplateRepo;
    @Mock private com.hms.infrastructure.persistence.inventory.InventoryBatchJpaRepository batchRepo;
    @Mock private com.hms.infrastructure.persistence.catalog.ServiceCatalogItemJpaRepository catalogItemRepo;
    @Mock private com.hms.infrastructure.persistence.catalog.ServiceCategoryJpaRepository serviceCategoryRepo;
    @Mock private com.hms.domain.shared.port.out.SequenceNumberPort sequencePort;
    @Mock private com.hms.infrastructure.sequence.NumberSequenceJpaRepository numberSequenceRepo;
    @Mock private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Mock private com.hms.infrastructure.persistence.specimen.SpecimenJpaRepository specimenRepo;
    @Mock private com.hms.infrastructure.persistence.diagtemplate.LabTemplateDetailJpaRepository labDetailRepo;
    @Mock private com.hms.infrastructure.persistence.printtemplate.PrintTemplateJpaRepository printTemplateRepo;

    @InjectMocks
    private BulkImportService bulkImportService;

    @Test
    void testImportBed_SuccessWithNameLookup() {
        String csvContent = "Bed No,Bed Type\nB-101,General Ward\n";
        MockMultipartFile file = new MockMultipartFile("file", "beds.csv", "text/csv", csvContent.getBytes());

        UUID categoryId = UUID.randomUUID();
        RoomCategory category = new RoomCategory();
        category.setId(categoryId);
        category.setName("General Ward");

        when(bedRepo.findByName("B-101")).thenReturn(Optional.empty());
        when(roomCategoryRepo.findByNameIgnoreCase("General_Ward")).thenReturn(Optional.empty());
        when(roomCategoryRepo.findByNameIgnoreCase("General Ward")).thenReturn(Optional.of(category));

        ImportResult result = bulkImportService.importCsv("bed", file);

        assertEquals(1, result.createdCount());
        assertEquals(0, result.errorCount());

        ArgumentCaptor<Bed> bedCaptor = ArgumentCaptor.forClass(Bed.class);
        verify(bedRepo).save(bedCaptor.capture());
        Bed savedBed = bedCaptor.getValue();
        assertEquals("B-101", savedBed.getName());
        assertEquals(categoryId, savedBed.getRoomCategoryId());
    }

    @Test
    void testImportBed_SuccessWithSemicolonSeparatedType() {
        String csvContent = "Bed No,Bed Type\nB-102,General Ward; ICU\n";
        MockMultipartFile file = new MockMultipartFile("file", "beds.csv", "text/csv", csvContent.getBytes());

        UUID categoryId = UUID.randomUUID();
        RoomCategory category = new RoomCategory();
        category.setId(categoryId);
        category.setName("General Ward");

        when(bedRepo.findByName("B-102")).thenReturn(Optional.empty());
        when(roomCategoryRepo.findByNameIgnoreCase("General_Ward")).thenReturn(Optional.empty());
        when(roomCategoryRepo.findByNameIgnoreCase("General Ward")).thenReturn(Optional.of(category));

        ImportResult result = bulkImportService.importCsv("bed", file);

        assertEquals(1, result.createdCount());
        assertEquals(0, result.errorCount());

        ArgumentCaptor<Bed> bedCaptor = ArgumentCaptor.forClass(Bed.class);
        verify(bedRepo).save(bedCaptor.capture());
        Bed savedBed = bedCaptor.getValue();
        assertEquals("B-102", savedBed.getName());
        assertEquals(categoryId, savedBed.getRoomCategoryId());
    }

    @Test
    void testImportBedType_SuccessWithChargeLink() {
        UUID tenantId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        com.hms.infrastructure.tenant.TenantContext.set(tenantId);
        com.hms.infrastructure.tenant.BranchContext.set(branchId);
        try {
            String csvContent = "Bed Type,Charge Name\nICU,ICU Room Charge\n";
            MockMultipartFile file = new MockMultipartFile("file", "bed_types.csv", "text/csv", csvContent.getBytes());

            UUID chargeId = UUID.randomUUID();
            Charge charge = new Charge();
            charge.setId(chargeId);
            charge.setName("ICU Room Charge");

            when(chargeRepo.findByTenantIdAndBranchIdAndNameIgnoreCase(eq(tenantId), eq(branchId), eq("ICU Room Charge"))).thenReturn(List.of(charge));
            when(roomCategoryRepo.findByTenantIdAndBranchIdAndNameIgnoreCase(eq(tenantId), eq(branchId), eq("ICU"))).thenReturn(Optional.empty());

            ImportResult result = bulkImportService.importCsv("bed_type", file);

            assertEquals(1, result.createdCount());
            assertEquals(0, result.errorCount());

            ArgumentCaptor<RoomCategory> categoryCaptor = ArgumentCaptor.forClass(RoomCategory.class);
            verify(roomCategoryRepo).saveAndFlush(categoryCaptor.capture());
            RoomCategory savedCategory = categoryCaptor.getValue();
            assertEquals("ICU", savedCategory.getName());
            assertEquals(chargeId, savedCategory.getServiceCatalogItemId());
        } finally {
            com.hms.infrastructure.tenant.TenantContext.clear();
            com.hms.infrastructure.tenant.BranchContext.clear();
        }
    }

    @Test
    void testImportPatient_Success() {
        String csvContent = "First Name,Last Name,Sex,Patient No\nJohn,Doe,MALE,OP-10023\n";
        MockMultipartFile file = new MockMultipartFile("file", "patients.csv", "text/csv", csvContent.getBytes());

        ImportResult result = bulkImportService.importCsv("patient", file);

        assertEquals(1, result.createdCount());
        assertEquals(0, result.errorCount());

        ArgumentCaptor<com.hms.domain.patient.model.Patient> patientCaptor = ArgumentCaptor.forClass(com.hms.domain.patient.model.Patient.class);
        verify(patientRepo).save(patientCaptor.capture());
        com.hms.domain.patient.model.Patient savedPatient = patientCaptor.getValue();
        assertEquals("John", savedPatient.getFirstName());
        assertEquals("Doe", savedPatient.getLastName());
        assertEquals(com.hms.domain.patient.model.Gender.MALE, savedPatient.getGender());

        ArgumentCaptor<com.hms.infrastructure.sequence.NumberSequenceEntity> seqCaptor = ArgumentCaptor.forClass(com.hms.infrastructure.sequence.NumberSequenceEntity.class);
        verify(numberSequenceRepo).save(seqCaptor.capture());
        com.hms.infrastructure.sequence.NumberSequenceEntity savedSeq = seqCaptor.getValue();
        assertEquals(savedPatient.getId(), savedSeq.getId());
        assertEquals("OP-10023", savedSeq.getValue());
    }

    @Test
    void testImportItem_Success() {
        UUID tenantId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        com.hms.infrastructure.tenant.TenantContext.set(tenantId);
        com.hms.infrastructure.tenant.BranchContext.set(branchId);
        try {
            String csvContent = "Item Name,CIMS Id,Batch Required,Base Unit,Category\nAspirin,C123,true,Tablet,Medicines\n";
            MockMultipartFile file = new MockMultipartFile("file", "items.csv", "text/csv", csvContent.getBytes());

            com.hms.domain.inventory.model.UnitOfMeasure uom = new com.hms.domain.inventory.model.UnitOfMeasure();
            uom.setId(UUID.randomUUID());
            uom.setName("Tablet");

            com.hms.domain.shared.model.Category category = new com.hms.domain.shared.model.Category();
            category.setId(UUID.randomUUID());
            category.setName("Medicines");

            when(uomRepo.findByTenantIdAndNameIgnoreCase(any(), eq("Tablet"))).thenReturn(Optional.of(uom));
            when(categoryRepo.findByTenantIdAndBranchIdAndNameIgnoreCase(any(), any(), eq("Medicines"))).thenReturn(Optional.of(category));
            when(itemRepo.findByName("Aspirin")).thenReturn(Optional.empty());

            ImportResult result = bulkImportService.importCsv("item", file);

            assertEquals(1, result.createdCount());
            assertEquals(0, result.errorCount());

            ArgumentCaptor<com.hms.domain.inventory.model.InventoryItem> itemCaptor = ArgumentCaptor.forClass(com.hms.domain.inventory.model.InventoryItem.class);
            verify(itemRepo).save(itemCaptor.capture());
            com.hms.domain.inventory.model.InventoryItem savedItem = itemCaptor.getValue();
            assertEquals("Aspirin", savedItem.getName());
            assertEquals("C123", savedItem.getCimsId());
            assertTrue(savedItem.isRequiresBatch());
            assertEquals(uom.getId(), savedItem.getUnitOfMeasureId());
            assertEquals(category.getId(), savedItem.getCategoryId());
        } finally {
            com.hms.infrastructure.tenant.TenantContext.clear();
            com.hms.infrastructure.tenant.BranchContext.clear();
        }
    }

    @Test
    void testImportPayor_Success() {
        UUID tenantId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        com.hms.infrastructure.tenant.TenantContext.set(tenantId);
        com.hms.infrastructure.tenant.BranchContext.set(branchId);
        try {
            String csvContent = "Payer Name,Payer Type\nStar Health,Insurance\n";
            MockMultipartFile file = new MockMultipartFile("file", "payors.csv", "text/csv", csvContent.getBytes());

            when(payorRepo.findByTenantIdAndBranchIdAndNameIgnoreCase(eq(tenantId), eq(branchId), eq("Star Health")))
                .thenReturn(Optional.empty());

            ImportResult result = bulkImportService.importCsv("payor", file);

            assertEquals(1, result.createdCount());
            assertEquals(0, result.errorCount());

            ArgumentCaptor<com.hms.domain.patient.model.Payor> payorCaptor = ArgumentCaptor.forClass(com.hms.domain.patient.model.Payor.class);
            verify(payorRepo).saveAndFlush(payorCaptor.capture());
            com.hms.domain.patient.model.Payor savedPayor = payorCaptor.getValue();
            assertEquals("Star Health", savedPayor.getName());
            assertEquals("STARHEALTH", savedPayor.getCode());
            assertEquals("Insurance", savedPayor.getType());
        } finally {
            com.hms.infrastructure.tenant.TenantContext.clear();
            com.hms.infrastructure.tenant.BranchContext.clear();
        }
    }

    @Test
    void testImportCategory_Success() {
        UUID tenantId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        com.hms.infrastructure.tenant.TenantContext.set(tenantId);
        com.hms.infrastructure.tenant.BranchContext.set(branchId);
        try {
            String csvContent = "Category Name,Type\nCardiology,Charge\n";
            MockMultipartFile file = new MockMultipartFile("file", "categories.csv", "text/csv", csvContent.getBytes());

            when(categoryRepo.findByTenantIdAndBranchIdAndNameIgnoreCase(eq(tenantId), eq(branchId), eq("Cardiology")))
                .thenReturn(Optional.empty());

            ImportResult result = bulkImportService.importCsv("category", file);

            assertEquals(1, result.createdCount());
            assertEquals(0, result.errorCount());

            ArgumentCaptor<com.hms.domain.shared.model.Category> categoryCaptor = ArgumentCaptor.forClass(com.hms.domain.shared.model.Category.class);
            verify(categoryRepo).saveAndFlush(categoryCaptor.capture());
            com.hms.domain.shared.model.Category savedCategory = categoryCaptor.getValue();
            assertEquals("Cardiology", savedCategory.getName());
            assertEquals(com.hms.domain.shared.model.CategoryType.CHARGE, savedCategory.getType());
        } finally {
            com.hms.infrastructure.tenant.TenantContext.clear();
            com.hms.infrastructure.tenant.BranchContext.clear();
        }
    }

    @Test
    void testImportCharge_Success() {
        UUID tenantId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        com.hms.infrastructure.tenant.TenantContext.set(tenantId);
        com.hms.infrastructure.tenant.BranchContext.set(branchId);
        try {
            String csvContent = "category,name,cash,credit\nCardiology,ECG Charge,150,200\n";
            MockMultipartFile file = new MockMultipartFile("file", "charges.csv", "text/csv", csvContent.getBytes());

            com.hms.domain.shared.model.Category category = new com.hms.domain.shared.model.Category();
            category.setId(UUID.randomUUID());
            category.setName("Cardiology");

            when(chargeRepo.findByTenantIdAndBranchIdAndNameIgnoreCase(eq(tenantId), eq(branchId), eq("ECG Charge")))
                .thenReturn(Collections.emptyList());
            when(chargeRepo.save(any(Charge.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(categoryRepo.findByTenantIdAndBranchIdAndNameIgnoreCase(eq(tenantId), eq(branchId), eq("Cardiology")))
                .thenReturn(Optional.of(category));
            when(categoryRepo.findById(eq(category.getId()))).thenReturn(Optional.of(category));
            when(serviceCategoryRepo.findByNameAndTenantIdIgnoreCaseNative(eq("Cardiology"), any())).thenReturn(Optional.empty());
            
            com.hms.domain.catalog.model.ServiceCategory serviceCat = new com.hms.domain.catalog.model.ServiceCategory();
            serviceCat.setId(UUID.randomUUID());
            serviceCat.setName("Cardiology");
            when(serviceCategoryRepo.save(any())).thenReturn(serviceCat);
            
            when(catalogItemRepo.findById(any())).thenReturn(Optional.empty());

            ImportResult result = bulkImportService.importCsv("charge", file);

            assertEquals(1, result.createdCount());
            assertEquals(0, result.errorCount());

            ArgumentCaptor<Charge> chargeCaptor = ArgumentCaptor.forClass(Charge.class);
            verify(chargeRepo).save(chargeCaptor.capture());
            Charge savedCharge = chargeCaptor.getValue();
            assertEquals("ECG Charge", savedCharge.getName());
            assertEquals(tenantId, savedCharge.getTenantId());
            assertEquals(branchId, savedCharge.getBranchId());
        } finally {
            com.hms.infrastructure.tenant.TenantContext.clear();
            com.hms.infrastructure.tenant.BranchContext.clear();
        }
    }

    @Test
    void testImportCharge_SkipDuplicate() {
        UUID tenantId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        com.hms.infrastructure.tenant.TenantContext.set(tenantId);
        com.hms.infrastructure.tenant.BranchContext.set(branchId);
        try {
            String csvContent = "category,name,cash,credit\nCardiology,ECG Charge,150,200\n";
            MockMultipartFile file = new MockMultipartFile("file", "charges.csv", "text/csv", csvContent.getBytes());

            Charge existingCharge = new Charge();
            existingCharge.setName("ECG Charge");

            when(chargeRepo.findByTenantIdAndBranchIdAndNameIgnoreCase(eq(tenantId), eq(branchId), eq("ECG Charge")))
                .thenReturn(List.of(existingCharge));

            ImportResult result = bulkImportService.importCsv("charge", file);

            assertEquals(0, result.createdCount());
            assertEquals(1, result.skippedCount());
            assertEquals(0, result.errorCount());
        } finally {
            com.hms.infrastructure.tenant.TenantContext.clear();
            com.hms.infrastructure.tenant.BranchContext.clear();
        }
    }

    @Test
    void testImportCharge_BranchAwareness() {
        UUID tenantId = UUID.randomUUID();
        UUID branchA = UUID.randomUUID();
        UUID branchB = UUID.randomUUID();
        String csvContent = "category,name,cash,credit\nCardiology,ECG Charge,150,200\n";
        MockMultipartFile file = new MockMultipartFile("file", "charges.csv", "text/csv", csvContent.getBytes());

        com.hms.domain.shared.model.Category category = new com.hms.domain.shared.model.Category();
        category.setId(UUID.randomUUID());
        category.setName("Cardiology");

        // 1. Switch to Branch A
        com.hms.infrastructure.tenant.TenantContext.set(tenantId);
        com.hms.infrastructure.tenant.BranchContext.set(branchA);
        try {
            when(chargeRepo.findByTenantIdAndBranchIdAndNameIgnoreCase(eq(tenantId), eq(branchA), eq("ECG Charge")))
                .thenReturn(Collections.emptyList());
            when(chargeRepo.save(any(Charge.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(categoryRepo.findByTenantIdAndBranchIdAndNameIgnoreCase(eq(tenantId), eq(branchA), eq("Cardiology")))
                .thenReturn(Optional.of(category));
            when(categoryRepo.findById(eq(category.getId()))).thenReturn(Optional.of(category));
            when(serviceCategoryRepo.findByNameAndTenantIdIgnoreCaseNative(eq("Cardiology"), any())).thenReturn(Optional.empty());
            
            com.hms.domain.catalog.model.ServiceCategory serviceCat = new com.hms.domain.catalog.model.ServiceCategory();
            serviceCat.setId(UUID.randomUUID());
            serviceCat.setName("Cardiology");
            when(serviceCategoryRepo.save(any())).thenReturn(serviceCat);
            
            when(catalogItemRepo.findById(any())).thenReturn(Optional.empty());

            ImportResult result = bulkImportService.importCsv("charge", file);
            assertEquals(1, result.createdCount());
        } finally {
            com.hms.infrastructure.tenant.TenantContext.clear();
            com.hms.infrastructure.tenant.BranchContext.clear();
        }

        // Reset mocks for Branch B
        reset(chargeRepo, categoryRepo, serviceCategoryRepo, catalogItemRepo);

        // 2. Switch to Branch B
        com.hms.infrastructure.tenant.TenantContext.set(tenantId);
        com.hms.infrastructure.tenant.BranchContext.set(branchB);
        try {
            when(chargeRepo.findByTenantIdAndBranchIdAndNameIgnoreCase(eq(tenantId), eq(branchB), eq("ECG Charge")))
                .thenReturn(Collections.emptyList());
            when(chargeRepo.save(any(Charge.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(categoryRepo.findByTenantIdAndBranchIdAndNameIgnoreCase(eq(tenantId), eq(branchB), eq("Cardiology")))
                .thenReturn(Optional.of(category));
            when(categoryRepo.findById(eq(category.getId()))).thenReturn(Optional.of(category));
            when(serviceCategoryRepo.findByNameAndTenantIdIgnoreCaseNative(eq("Cardiology"), any())).thenReturn(Optional.empty());
            
            com.hms.domain.catalog.model.ServiceCategory serviceCat = new com.hms.domain.catalog.model.ServiceCategory();
            serviceCat.setId(UUID.randomUUID());
            serviceCat.setName("Cardiology");
            when(serviceCategoryRepo.save(any())).thenReturn(serviceCat);
            
            when(catalogItemRepo.findById(any())).thenReturn(Optional.empty());

            ImportResult result = bulkImportService.importCsv("charge", file);
            assertEquals(1, result.createdCount());
        } finally {
            com.hms.infrastructure.tenant.TenantContext.clear();
            com.hms.infrastructure.tenant.BranchContext.clear();
        }
    }
}
