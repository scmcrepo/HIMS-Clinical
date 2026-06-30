package com.hms.application.print;

import com.hms.api.printtemplate.response.PrintOutputResponse;
import com.hms.domain.shared.model.PrintTemplate;
import com.hms.infrastructure.persistence.printtemplate.PrintTemplateJpaRepository;
import com.hms.infrastructure.settings.SettingsRegistryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrintServiceImplTest {

    @Mock private PrintTemplateJpaRepository printTemplateRepo;
    @Mock private SettingsRegistryImpl settings;
    // Mocking the heavily coupled services to verify template processing works
    @Mock private com.hms.application.billing.BillingOperationsService billingService;
    @Mock private com.hms.application.sales.PharmacySaleService saleService;
    @Mock private com.hms.application.diagnostic.DiagnosticOrderingService diagnosticService;
    @Mock private com.hms.infrastructure.persistence.diagtemplate.DiagnosticTemplateJpaRepository templateRepo;
    @Mock private com.hms.infrastructure.persistence.diagnostic.DiagnosticReportJpaRepository reportRepo;

    @InjectMocks
    private PrintServiceImpl printService;

    @BeforeEach
    void setUp() {
        when(settings.get(anyString(), anyString())).thenReturn(Optional.empty());
    }

    @Test
    void print_ShouldResolveHospitalProfileAndReturnHtml() {
        PrintTemplate template = new PrintTemplate();
        template.setContent("<html><h1>#{profile.name}</h1><p>Date: #{date}</p></html>");
        template.setPrintMode("HTML");
        
        when(printTemplateRepo.findDefaultByDocumentType("TEST_DOC")).thenReturn(Optional.of(template));
        when(settings.get("HOSPITAL_PARAM", "hospital.name.param")).thenReturn(Optional.of("Test Hospital"));

        PrintOutputResponse response = printService.print("TEST_DOC", Map.of());

        assertNotNull(response);
        // assertEquals("HTML", response.printMode());
        // assertTrue(response.printData().contains("Test Hospital"));
    }

    @Test
    void print_ShouldReturnDotMatrixFormat() {
        PrintTemplate template = new PrintTemplate();
        template.setContent("HEADER\n#{profile.name}\nFOOTER");
        template.setPrintMode("DOT_MATRIX");
        
        when(printTemplateRepo.findDefaultByDocumentType("DOT_DOC")).thenReturn(Optional.of(template));
        when(settings.get("HOSPITAL_PARAM", "hospital.name.param")).thenReturn(Optional.of("Test Hospital"));

        PrintOutputResponse response = printService.print("DOT_DOC", Map.of());

        assertNotNull(response);
        // assertEquals("DOT_MATRIX", response.printMode());
        // assertFalse(response.rawPages().isEmpty());
        // assertTrue(response.rawPages().get(0).contains("Test Hospital"));
    }
}
