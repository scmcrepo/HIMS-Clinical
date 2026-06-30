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
class InpatientReportDataServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private ReportScope scope;
    @Mock private PiiEncryptionService piiEncryptionService;

    @InjectMocks
    private InpatientReportDataService service;

    @BeforeEach
    void setUp() {
        lenient().when(scope.predicate(anyString())).thenReturn(" AND 1=1 ");
        lenient().when(scope.args()).thenReturn(List.of());
        lenient().when(piiEncryptionService.looksEncrypted(anyString())).thenReturn(false);
    }

    @Test
    void getAdmissionsReport_ShouldReturnData() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            List<Map<String, Object>> dummy = new ArrayList<>();
            Map<String, Object> row = new HashMap<>();
            row.put("Patient", "Test Patient");
            dummy.add(row);
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class))).thenReturn(dummy);
            
            List<Map<String, Object>> result = service.getAdmissionsReport("", "", "");
            
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Test
    void getAdmissionsSummaryReport_ShouldReturnData() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            List<Map<String, Object>> dummy = new ArrayList<>();
            Map<String, Object> row = new HashMap<>();
            row.put("Patient", "Test Patient");
            dummy.add(row);
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class))).thenReturn(dummy);
            
            List<Map<String, Object>> result = service.getAdmissionsSummaryReport("", "");
            
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Test
    void getDischargesReport_ShouldReturnData() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            List<Map<String, Object>> dummy = new ArrayList<>();
            Map<String, Object> row = new HashMap<>();
            row.put("Patient", "Test Patient");
            dummy.add(row);
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class))).thenReturn(dummy);
            
            List<Map<String, Object>> result = service.getDischargesReport("", "");
            
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Test
    void getBedOccupancyPeriodReport_ShouldReturnData() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            List<Map<String, Object>> dummy = new ArrayList<>();
            Map<String, Object> row = new HashMap<>();
            row.put("Patient", "Test Patient");
            dummy.add(row);
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class))).thenReturn(dummy);
            
            List<Map<String, Object>> result = service.getBedOccupancyPeriodReport("", "");
            
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Test
    void getBedsTransferredReport_ShouldReturnData() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            List<Map<String, Object>> dummy = new ArrayList<>();
            Map<String, Object> row = new HashMap<>();
            row.put("Patient", "Test Patient");
            dummy.add(row);
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class))).thenReturn(dummy);
            
            List<Map<String, Object>> result = service.getBedsTransferredReport("", "");
            
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Test
    void getBedOccupancy_ShouldReturnData() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            List<Map<String, Object>> dummy = new ArrayList<>();
            Map<String, Object> row = new HashMap<>();
            row.put("Patient", "Test Patient");
            dummy.add(row);
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class))).thenReturn(dummy);
            
            List<Map<String, Object>> result = service.getBedOccupancy();
            
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }
}
