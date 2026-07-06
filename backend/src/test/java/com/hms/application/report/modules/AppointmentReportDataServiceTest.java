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
class AppointmentReportDataServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private ReportScope scope;
    @Mock private PiiEncryptionService piiEncryptionService;

    @InjectMocks
    private AppointmentReportDataService service;

    @BeforeEach
    void setUp() {
        lenient().when(scope.predicate(anyString())).thenReturn(" AND 1=1 ");
        lenient().when(scope.args()).thenReturn(List.of());
        lenient().when(piiEncryptionService.looksEncrypted(anyString())).thenReturn(false);
    }

    @Test
    void getAppointmentsDaywise_ShouldReturnData() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            List<Map<String, Object>> dummy = new ArrayList<>();
            Map<String, Object> row = new HashMap<>();
            row.put("Patient", "Test Patient");
            dummy.add(row);
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class))).thenReturn(dummy);
            
            List<Map<String, Object>> result = service.getAppointmentsDaywise("", "", "");
            
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Test
    void getAppointmentScheduledDetails_ShouldReturnData() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            List<Map<String, Object>> dummy = new ArrayList<>();
            Map<String, Object> row = new HashMap<>();
            row.put("Patient", "Test Patient");
            dummy.add(row);
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class))).thenReturn(dummy);
            
            List<Map<String, Object>> result = service.getAppointmentScheduledDetails("", "", "");
            
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Test
    void getAppointmentCancelledDetails_ShouldReturnData() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            List<Map<String, Object>> dummy = new ArrayList<>();
            Map<String, Object> row = new HashMap<>();
            row.put("Patient", "Test Patient");
            dummy.add(row);
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class))).thenReturn(dummy);
            
            List<Map<String, Object>> result = service.getAppointmentCancelledDetails("", "", "");
            
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Test
    void getAppointmentsConsultantwise_ShouldReturnData() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            List<Map<String, Object>> dummy = new ArrayList<>();
            Map<String, Object> row = new HashMap<>();
            row.put("Patient", "Test Patient");
            dummy.add(row);
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class))).thenReturn(dummy);
            
            List<Map<String, Object>> result = service.getAppointmentsConsultantwise("", "");
            
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Test
    void getAppointmentsCancelledDaywise_ShouldReturnData() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            List<Map<String, Object>> dummy = new ArrayList<>();
            Map<String, Object> row = new HashMap<>();
            row.put("Patient", "Test Patient");
            dummy.add(row);
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class))).thenReturn(dummy);
            
            List<Map<String, Object>> result = service.getAppointmentsCancelledDaywise("", "", "");
            
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Test
    void getAppointmentsCancelledConsultantwise_ShouldReturnData() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            List<Map<String, Object>> dummy = new ArrayList<>();
            Map<String, Object> row = new HashMap<>();
            row.put("Patient", "Test Patient");
            dummy.add(row);
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class))).thenReturn(dummy);
            
            List<Map<String, Object>> result = service.getAppointmentsCancelledConsultantwise("", "");
            
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }
}
