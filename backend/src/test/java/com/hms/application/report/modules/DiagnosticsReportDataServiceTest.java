package com.hms.application.report.modules;

import com.hms.application.report.util.ReportScope;
import com.hms.security.encryption.PiiEncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import com.hms.application.report.util.ReportDbUtil;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiagnosticsReportDataServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private ReportScope scope;
    @Mock private PiiEncryptionService piiEncryptionService;

    @InjectMocks
    private DiagnosticsReportDataService service;

    @BeforeEach
    void setUp() {
        lenient().when(scope.predicate(anyString())).thenReturn(" AND 1=1 ");
        lenient().when(scope.args()).thenReturn(List.of());
        lenient().when(piiEncryptionService.looksEncrypted(anyString())).thenReturn(false);
    }

    @Test
    void getLabTestsDoneSummary_ShouldReturnData() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            List<Map<String, Object>> dummy = new ArrayList<>();
            Map<String, Object> row = new HashMap<>();
            row.put("Patient", "Test Patient");
            dummy.add(row);
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class))).thenReturn(dummy);
            
            List<Map<String, Object>> result = service.getLabTestsDoneSummary("", "");
            
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Test
    void getLabTestsDoneDetail_ShouldReturnData() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            List<Map<String, Object>> dummy = new ArrayList<>();
            Map<String, Object> row = new HashMap<>();
            row.put("Patient", "Test Patient");
            dummy.add(row);
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class))).thenReturn(dummy);
            
            List<Map<String, Object>> result = service.getLabTestsDoneDetail("", "");
            
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Test
    void getPendingLabTestsSummary_ShouldReturnData() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            List<Map<String, Object>> dummy = new ArrayList<>();
            Map<String, Object> row = new HashMap<>();
            row.put("Patient", "Test Patient");
            dummy.add(row);
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class))).thenReturn(dummy);
            
            List<Map<String, Object>> result = service.getPendingLabTestsSummary("", "");
            
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Test
    void getPendingLabTestsDetail_ShouldReturnData() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            List<Map<String, Object>> dummy = new ArrayList<>();
            Map<String, Object> row = new HashMap<>();
            row.put("Patient", "Test Patient");
            dummy.add(row);
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class))).thenReturn(dummy);
            
            List<Map<String, Object>> result = service.getPendingLabTestsDetail("", "");
            
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }
}
