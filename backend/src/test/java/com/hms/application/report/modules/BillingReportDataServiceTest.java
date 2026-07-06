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
class BillingReportDataServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private ReportScope scope;
    @Mock private PiiEncryptionService piiEncryptionService;

    @InjectMocks
    private BillingReportDataService service;

    @BeforeEach
    void setUp() {
        lenient().when(scope.predicate(anyString())).thenReturn(" AND 1=1 ");
        lenient().when(scope.args()).thenReturn(List.of());
        lenient().when(piiEncryptionService.looksEncrypted(anyString())).thenReturn(false);
    }

    @Test
    void getBillsRaisedDaywise_ShouldReturnData() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            List<Map<String, Object>> dummy = new ArrayList<>();
            Map<String, Object> row = new HashMap<>();
            row.put("Patient", "Test Patient");
            dummy.add(row);
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class))).thenReturn(dummy);
            
            List<Map<String, Object>> result = service.getBillsRaisedDaywise("", "", "");
            
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Test
    void getBillsCancelledDaywise_ShouldReturnData() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            List<Map<String, Object>> dummy = new ArrayList<>();
            Map<String, Object> row = new HashMap<>();
            row.put("Patient", "Test Patient");
            dummy.add(row);
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class))).thenReturn(dummy);
            
            List<Map<String, Object>> result = service.getBillsCancelledDaywise("", "", "");
            
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Test
    void getDiscountReport_ShouldReturnData() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            List<Map<String, Object>> dummy = new ArrayList<>();
            Map<String, Object> row = new HashMap<>();
            row.put("Patient", "Test Patient");
            dummy.add(row);
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class))).thenReturn(dummy);
            
            List<Map<String, Object>> result = service.getDiscountReport("", "", "");
            
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Test
    void getBillsOverdue_ShouldReturnData() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            List<Map<String, Object>> dummy = new ArrayList<>();
            Map<String, Object> row = new HashMap<>();
            row.put("Patient", "Test Patient");
            dummy.add(row);
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class))).thenReturn(dummy);
            
            List<Map<String, Object>> result = service.getBillsOverdue();
            
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Test
    void getUnsettledBills_ShouldReturnData() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            List<Map<String, Object>> dummy = new ArrayList<>();
            Map<String, Object> row = new HashMap<>();
            row.put("Patient", "Test Patient");
            dummy.add(row);
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class))).thenReturn(dummy);
            
            List<Map<String, Object>> result = service.getUnsettledBills("", "", "");
            
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Test
    void getBillRaisedSummary_ShouldReturnData() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            List<Map<String, Object>> dummy = new ArrayList<>();
            Map<String, Object> row = new HashMap<>();
            row.put("Patient", "Test Patient");
            dummy.add(row);
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class))).thenReturn(dummy);
            
            List<Map<String, Object>> result = service.getBillRaisedSummary("", "");
            
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Test
    void getBillCancelledSummary_ShouldReturnData() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            List<Map<String, Object>> dummy = new ArrayList<>();
            Map<String, Object> row = new HashMap<>();
            row.put("Patient", "Test Patient");
            dummy.add(row);
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class))).thenReturn(dummy);
            
            List<Map<String, Object>> result = service.getBillCancelledSummary("", "");
            
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Test
    void getDiscountSummary_ShouldReturnData() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            List<Map<String, Object>> dummy = new ArrayList<>();
            Map<String, Object> row = new HashMap<>();
            row.put("Patient", "Test Patient");
            dummy.add(row);
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class))).thenReturn(dummy);
            
            List<Map<String, Object>> result = service.getDiscountSummary("", "");
            
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Test
    void getOutstandingBillsSummary_ShouldReturnData() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            List<Map<String, Object>> dummy = new ArrayList<>();
            Map<String, Object> row = new HashMap<>();
            row.put("Patient", "Test Patient");
            dummy.add(row);
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class))).thenReturn(dummy);
            
            List<Map<String, Object>> result = service.getOutstandingBillsSummary("", "");
            
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Test
    void getIpOutstandingBillsSummary_ShouldReturnData() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            List<Map<String, Object>> dummy = new ArrayList<>();
            Map<String, Object> row = new HashMap<>();
            row.put("Patient", "Test Patient");
            dummy.add(row);
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class))).thenReturn(dummy);
            
            List<Map<String, Object>> result = service.getIpOutstandingBillsSummary("", "");
            
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Test
    void getOverDueBillRaisedSummary_ShouldReturnData() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            List<Map<String, Object>> dummy = new ArrayList<>();
            Map<String, Object> row = new HashMap<>();
            row.put("Patient", "Test Patient");
            dummy.add(row);
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class))).thenReturn(dummy);
            
            List<Map<String, Object>> result = service.getOverDueBillRaisedSummary();
            
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }
}
