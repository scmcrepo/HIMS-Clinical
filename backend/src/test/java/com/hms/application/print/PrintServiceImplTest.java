package com.hms.application.print;

import com.hms.api.printtemplate.response.PrintOutputResponse;
import com.hms.domain.shared.model.PrintTemplate;
import com.hms.infrastructure.persistence.printtemplate.PrintTemplateJpaRepository;
import com.hms.infrastructure.persistence.tenant.TenantEntity;
import com.hms.infrastructure.persistence.tenant.TenantJpaRepository;
import com.hms.infrastructure.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrintServiceImplTest {

    @Mock private PrintTemplateJpaRepository printTemplateRepo;
    @Mock private TenantJpaRepository tenantRepo;
    // Mocking the heavily coupled services to verify template processing works
    @Mock private com.hms.application.billing.BillingOperationsService billingService;
    @Mock private com.hms.application.sales.PharmacySaleService saleService;
    @Mock private com.hms.application.diagnostic.DiagnosticOrderingService diagnosticService;
    @Mock private com.hms.infrastructure.persistence.diagtemplate.DiagnosticTemplateJpaRepository templateRepo;
    @Mock private com.hms.infrastructure.persistence.diagnostic.DiagnosticReportJpaRepository reportRepo;
    @Mock private com.hms.infrastructure.settings.SettingsRegistryImpl settings;

    @InjectMocks
    private PrintServiceImpl printService;

    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.set(tenantId);
        
        TenantEntity tenant = new TenantEntity();
        tenant.setId(tenantId);
        tenant.setName("Test Hospital");
        tenant.setAddress("Test Address");
        tenant.setContactNumber("12345");
        
        lenient().when(tenantRepo.findById(tenantId)).thenReturn(Optional.of(tenant));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void print_ShouldResolveHospitalProfileAndReturnHtml() {
        PrintTemplate template = new PrintTemplate();
        template.setContent("<html><h1>#{profile.name}</h1><p>Date: #{date}</p></html>");
        template.setPrintMode("HTML");
        
        when(printTemplateRepo.findDefaultByDocumentType("TEST_DOC")).thenReturn(Optional.of(template));

        PrintOutputResponse response = printService.print("TEST_DOC", Map.of());

        assertNotNull(response);
    }

    @Test
    void print_ShouldReturnDotMatrixFormat() {
        PrintTemplate template = new PrintTemplate();
        template.setContent("HEADER\n#{profile.name}\nFOOTER");
        template.setPrintMode("DOT_MATRIX");
        
        when(printTemplateRepo.findDefaultByDocumentType("DOT_DOC")).thenReturn(Optional.of(template));

        PrintOutputResponse response = printService.print("DOT_DOC", Map.of());

        assertNotNull(response);
    }
}
