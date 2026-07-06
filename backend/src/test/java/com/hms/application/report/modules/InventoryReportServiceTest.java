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
class InventoryReportServiceTest {

    @Mock private ReportEngine reportEngine;
    @Mock private InventoryReportDataService dataService;

    @InjectMocks
    private InventoryReportService service;

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
    void executeDataQuery_ShouldHandle_current_stock() {
        List<Map<String, Object>> result = service.executeDataQuery("current_stock", Map.of());
        assertNotNull(result);
    }

    @Test
    void executeDataQuery_ShouldHandle_expired_items() {
        List<Map<String, Object>> result = service.executeDataQuery("expired_items", Map.of());
        assertNotNull(result);
    }

    @Test
    void executeDataQuery_ShouldHandle_items_expiring_month() {
        List<Map<String, Object>> result = service.executeDataQuery("items_expiring_month", Map.of());
        assertNotNull(result);
    }

    @Test
    void executeDataQuery_ShouldHandle_slow_moving_items() {
        List<Map<String, Object>> result = service.executeDataQuery("slow_moving_items", Map.of());
        assertNotNull(result);
    }

    @Test
    void executeDataQuery_ShouldHandle_zero_stock_items() {
        List<Map<String, Object>> result = service.executeDataQuery("zero_stock_items", Map.of());
        assertNotNull(result);
    }

    @Test
    void executeDataQuery_ShouldHandle_stock_and_nil_stock() {
        List<Map<String, Object>> result = service.executeDataQuery("stock_and_nil_stock", Map.of());
        assertNotNull(result);
    }

    @Test
    void executeDataQuery_ShouldHandle_scheduled_drug_sales() {
        List<Map<String, Object>> result = service.executeDataQuery("scheduled_drug_sales", Map.of());
        assertNotNull(result);
    }

    @Test
    void executeDataQuery_ShouldHandle_below_reorder_level() {
        List<Map<String, Object>> result = service.executeDataQuery("below_reorder_level", Map.of());
        assertNotNull(result);
    }

    @Test
    void executeDataQuery_ShouldHandle_stock_adjustments() {
        List<Map<String, Object>> result = service.executeDataQuery("stock_adjustments", Map.of());
        assertNotNull(result);
    }
}
