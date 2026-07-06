package com.hms.application.report.modules;

import com.hms.application.report.ReportEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillingReportServiceTest {

    @Mock private ReportEngine reportEngine;
    @Mock private BillingReportDataService dataService;

    @InjectMocks
    private BillingReportService service;

    @BeforeEach
    void setUp() {
        lenient().when(reportEngine.dateStr(anyMap(), anyString())).thenReturn("2024-01-01");
        lenient().when(reportEngine.str(anyMap(), anyString())).thenReturn("");
    }
    
    @Test
    void getAvailableReports_ShouldReturnList() {
        assertNotNull(service.getAvailableReports());
    }
    
    @Test
    void getReportInfo_ShouldReturnInfo() {
        Map<String, Object> info = service.getReportInfo("some_report");
        assertNotNull(info);
        assertEquals("some_report", info.get("reportName"));
    }
    
    @Test
    void executeDataQuery_ShouldHandleUnknownReport() {
        List<Map<String, Object>> result = service.executeDataQuery("unknown", Map.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void executeDataQuery_ShouldHandle_bills_raised_daywise() {
        List<Map<String, Object>> result = service.executeDataQuery("bills_raised_daywise", Map.of());
        assertNotNull(result);
    }

    @Test
    void executeDataQuery_ShouldHandle_bills_cancelled_daywise() {
        List<Map<String, Object>> result = service.executeDataQuery("bills_cancelled_daywise", Map.of());
        assertNotNull(result);
    }

    @Test
    void executeDataQuery_ShouldHandle_discount_report() {
        List<Map<String, Object>> result = service.executeDataQuery("discount_report", Map.of());
        assertNotNull(result);
    }

    @Test
    void executeDataQuery_ShouldHandle_bills_overdue() {
        List<Map<String, Object>> result = service.executeDataQuery("bills_overdue", Map.of());
        assertNotNull(result);
    }

    @Test
    void executeDataQuery_ShouldHandle_unsettled_bills() {
        List<Map<String, Object>> result = service.executeDataQuery("unsettled_bills", Map.of());
        assertNotNull(result);
    }

    @Test
    void executeDataQuery_ShouldHandle_bill_raised_summary() {
        List<Map<String, Object>> result = service.executeDataQuery("bill_raised_summary", Map.of());
        assertNotNull(result);
    }

    @Test
    void executeDataQuery_ShouldHandle_bill_cancelled_summary() {
        List<Map<String, Object>> result = service.executeDataQuery("bill_cancelled_summary", Map.of());
        assertNotNull(result);
    }

    @Test
    void executeDataQuery_ShouldHandle_discount_summary() {
        List<Map<String, Object>> result = service.executeDataQuery("discount_summary", Map.of());
        assertNotNull(result);
    }

    @Test
    void executeDataQuery_ShouldHandle_outstanding_bills_summary() {
        List<Map<String, Object>> result = service.executeDataQuery("outstanding_bills_summary", Map.of());
        assertNotNull(result);
    }

    @Test
    void executeDataQuery_ShouldHandle_ip_outstanding_bills_summary() {
        List<Map<String, Object>> result = service.executeDataQuery("ip_outstanding_bills_summary", Map.of());
        assertNotNull(result);
    }

    @Test
    void executeDataQuery_ShouldHandle_overdue_bills_summary() {
        List<Map<String, Object>> result = service.executeDataQuery("overdue_bills_summary", Map.of());
        assertNotNull(result);
    }
}
