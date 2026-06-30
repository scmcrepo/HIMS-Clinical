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
class EncounterReportServiceTest {

    @Mock private ReportEngine reportEngine;
    @Mock private EncounterReportDataService dataService;

    @InjectMocks
    private EncounterReportService service;

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
    void executeDataQuery_ShouldHandle_encounters_report() {
        List<Map<String, Object>> result = service.executeDataQuery("encounters_report", Map.of());
        assertNotNull(result);
    }

    @Test
    void executeDataQuery_ShouldHandle_visit_details() {
        List<Map<String, Object>> result = service.executeDataQuery("visit_details", Map.of());
        assertNotNull(result);
    }

    @Test
    void executeDataQuery_ShouldHandle_consultant_wise_visit() {
        List<Map<String, Object>> result = service.executeDataQuery("consultant_wise_visit", Map.of());
        assertNotNull(result);
    }

    @Test
    void executeDataQuery_ShouldHandle_department_wise_visit() {
        List<Map<String, Object>> result = service.executeDataQuery("department_wise_visit", Map.of());
        assertNotNull(result);
    }

    @Test
    void executeDataQuery_ShouldHandle_consultation_summary() {
        List<Map<String, Object>> result = service.executeDataQuery("consultation_summary", Map.of());
        assertNotNull(result);
    }

    @Test
    void executeDataQuery_ShouldHandle_consultant_wise_consulted() {
        List<Map<String, Object>> result = service.executeDataQuery("consultant_wise_consulted", Map.of());
        assertNotNull(result);
    }

    @Test
    void executeDataQuery_ShouldHandle_consultant_wise_visit_detail() {
        List<Map<String, Object>> result = service.executeDataQuery("consultant_wise_visit_detail", Map.of());
        assertNotNull(result);
    }

    @Test
    void executeDataQuery_ShouldHandle_dept_wise_consultant_visit() {
        List<Map<String, Object>> result = service.executeDataQuery("dept_wise_consultant_visit", Map.of());
        assertNotNull(result);
    }
}
