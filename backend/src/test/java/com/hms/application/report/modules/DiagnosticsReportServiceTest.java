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
class DiagnosticsReportServiceTest {

    @Mock private ReportEngine reportEngine;
    @Mock private DiagnosticsReportDataService dataService;

    @InjectMocks
    private DiagnosticsReportService service;

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
    void executeDataQuery_ShouldHandle_lab_tests_done() {
        List<Map<String, Object>> result = service.executeDataQuery("lab_tests_done", Map.of());
        assertNotNull(result);
    }

    @Test
    void executeDataQuery_ShouldHandle_lab_tests_done_detail() {
        List<Map<String, Object>> result = service.executeDataQuery("lab_tests_done_detail", Map.of());
        assertNotNull(result);
    }

    @Test
    void executeDataQuery_ShouldHandle_lab_pending() {
        List<Map<String, Object>> result = service.executeDataQuery("lab_pending", Map.of());
        assertNotNull(result);
    }

    @Test
    void executeDataQuery_ShouldHandle_lab_pending_detail() {
        List<Map<String, Object>> result = service.executeDataQuery("lab_pending_detail", Map.of());
        assertNotNull(result);
    }
}
