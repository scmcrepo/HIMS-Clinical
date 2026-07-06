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
class EncounterReportDataServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private ReportScope scope;
    @Mock private PiiEncryptionService piiEncryptionService;

    @InjectMocks
    private EncounterReportDataService service;

    @BeforeEach
    void setUp() {
        lenient().when(scope.predicate(anyString())).thenReturn(" AND 1=1 ");
        lenient().when(scope.args()).thenReturn(List.of());
        lenient().when(piiEncryptionService.looksEncrypted(anyString())).thenReturn(false);
    }

    @Test
    void getEncountersReport_ShouldReturnData() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            List<Map<String, Object>> dummy = new ArrayList<>();
            Map<String, Object> row = new HashMap<>();
            row.put("Patient", "Test Patient");
            dummy.add(row);
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class))).thenReturn(dummy);
            
            List<Map<String, Object>> result = service.getEncountersReport("", "", "");
            
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Test
    void getVisitDetails_ShouldReturnData() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            List<Map<String, Object>> dummy = new ArrayList<>();
            Map<String, Object> row = new HashMap<>();
            row.put("Patient", "Test Patient");
            dummy.add(row);
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class))).thenReturn(dummy);
            
            List<Map<String, Object>> result = service.getVisitDetails("", "", "");
            
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Test
    void getDepartmentWiseVisitReport_ShouldReturnData() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            List<Map<String, Object>> dummy = new ArrayList<>();
            Map<String, Object> row = new HashMap<>();
            row.put("Patient", "Test Patient");
            dummy.add(row);
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class))).thenReturn(dummy);
            
            List<Map<String, Object>> result = service.getDepartmentWiseVisitReport("", "");
            
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Test
    void getActiveClinicalDepartments_ShouldReturnData() {
        List<Map<String, Object>> dummy = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put("name", "Cardiology");
        dummy.add(row);
        when(jdbcTemplate.queryForList(anyString())).thenReturn(dummy);
        
        List<Map<String, Object>> result = service.getActiveClinicalDepartments();
        
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void getDepartmentWiseConsultedReport_ShouldReturnData() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            List<Map<String, Object>> dummy = new ArrayList<>();
            Map<String, Object> row = new HashMap<>();
            row.put("Patient", "Test Patient");
            dummy.add(row);
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class))).thenReturn(dummy);
            
            List<Map<String, Object>> result = service.getDepartmentWiseConsultedReport("", "");
            
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Test
    void getConsultationSummaryReport_ShouldReturnData() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            List<Map<String, Object>> dummy = new ArrayList<>();
            Map<String, Object> row = new HashMap<>();
            row.put("Patient", "Test Patient");
            dummy.add(row);
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class))).thenReturn(dummy);
            
            List<Map<String, Object>> result = service.getConsultationSummaryReport("", "");
            
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Test
    void getConsultantWiseVisitReport_ShouldReturnData() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            List<Map<String, Object>> dummy = new ArrayList<>();
            Map<String, Object> row = new HashMap<>();
            row.put("Patient", "Test Patient");
            dummy.add(row);
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class))).thenReturn(dummy);
            
            List<Map<String, Object>> result = service.getConsultantWiseVisitReport("", "");
            
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Test
    void getConsultantWiseConsultedReport_ShouldReturnData() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            List<Map<String, Object>> dummy = new ArrayList<>();
            Map<String, Object> row = new HashMap<>();
            row.put("Patient", "Test Patient");
            dummy.add(row);
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class))).thenReturn(dummy);
            
            List<Map<String, Object>> result = service.getConsultantWiseConsultedReport("", "", "");
            
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Test
    void getConsultantWiseVisitDetail_ShouldReturnData() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            List<Map<String, Object>> dummy = new ArrayList<>();
            Map<String, Object> row = new HashMap<>();
            row.put("Patient", "Test Patient");
            dummy.add(row);
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class))).thenReturn(dummy);
            
            List<Map<String, Object>> result = service.getConsultantWiseVisitDetail("", "", "");
            
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Test
    void getDeptWiseConsultantVisit_ShouldReturnData() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            List<Map<String, Object>> dummy = new ArrayList<>();
            Map<String, Object> row = new HashMap<>();
            row.put("Patient", "Test Patient");
            dummy.add(row);
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class))).thenReturn(dummy);
            
            List<Map<String, Object>> result = service.getDeptWiseConsultantVisit("", "", "");
            
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }
}
