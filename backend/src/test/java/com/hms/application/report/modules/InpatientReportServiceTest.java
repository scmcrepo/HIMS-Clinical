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
class InpatientReportServiceTest {

    @Mock private ReportEngine reportEngine;
    @Mock private InpatientReportDataService dataService;

    @InjectMocks
    private InpatientReportService service;

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
    void executeDataQuery_ShouldHandle_admissions_report() {
        List<Map<String, Object>> result = service.executeDataQuery("admissions_report", Map.of());
        assertNotNull(result);
    }

    @Test
    void executeDataQuery_ShouldHandle_discharges_report() {
        List<Map<String, Object>> result = service.executeDataQuery("discharges_report", Map.of());
        assertNotNull(result);
    }

    @Test
    void executeDataQuery_ShouldHandle_bed_occupancy_period() {
        List<Map<String, Object>> result = service.executeDataQuery("bed_occupancy_period", Map.of());
        assertNotNull(result);
    }

    @Test
    void executeDataQuery_ShouldHandle_beds_transferred() {
        List<Map<String, Object>> result = service.executeDataQuery("beds_transferred", Map.of());
        assertNotNull(result);
    }

    @Test
    void executeDataQuery_ShouldHandle_bed_occupancy() {
        List<Map<String, Object>> result = service.executeDataQuery("bed_occupancy", Map.of());
        assertNotNull(result);
    }

    @Test
    void executeDataQuery_ShouldHandle_ip_discharge_summary() {
        List<Map<String, Object>> result = service.executeDataQuery("ip_discharge_summary", Map.of());
        assertNotNull(result);
    }
}
