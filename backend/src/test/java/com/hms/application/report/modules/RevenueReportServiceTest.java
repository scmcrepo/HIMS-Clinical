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
class RevenueReportServiceTest {

    @Mock private ReportEngine reportEngine;
    @Mock private RevenueReportDataService dataService;

    @InjectMocks
    private RevenueReportService service;

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
    }
    
    @Test
    void executeDataQuery_ShouldHandleUnknownReport() {
        List<Map<String, Object>> result = service.executeDataQuery("unknown", Map.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void executeDataQuery_ShouldHandle_net_revenue_report() {
        List<Map<String, Object>> result = service.executeDataQuery("net_revenue_report", Map.of());
        assertNotNull(result);
    }

    @Test
    void executeDataQuery_ShouldHandle_department_revenue_opip() {
        List<Map<String, Object>> result = service.executeDataQuery("department_revenue_opip", Map.of());
        assertNotNull(result);
    }

    @Test
    void executeDataQuery_ShouldHandle_department_revenue() {
        List<Map<String, Object>> result = service.executeDataQuery("department_revenue", Map.of());
        assertNotNull(result);
    }

    @Test
    void executeDataQuery_ShouldHandle_consultant_revenue_opip() {
        List<Map<String, Object>> result = service.executeDataQuery("consultant_revenue_opip", Map.of());
        assertNotNull(result);
    }

    @Test
    void executeDataQuery_ShouldHandle_consultant_revenue() {
        List<Map<String, Object>> result = service.executeDataQuery("consultant_revenue", Map.of());
        assertNotNull(result);
    }

    @Test
    void executeDataQuery_ShouldHandle_room_revenue() {
        List<Map<String, Object>> result = service.executeDataQuery("room_revenue", Map.of());
        assertNotNull(result);
    }
}
