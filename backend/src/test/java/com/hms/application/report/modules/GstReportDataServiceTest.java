package com.hms.application.report.modules;

import com.hms.application.report.util.ReportDbUtil;
import com.hms.application.report.util.ReportScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GstReportDataServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private ReportScope scope;

    @InjectMocks
    private GstReportDataService service;

    @BeforeEach
    void setUp() {
        lenient().when(scope.predicate(anyString())).thenReturn(" AND 1=1 ");
        lenient().when(scope.args()).thenReturn(List.of());
    }

    @Test
    void getPharmacySalesGstDetailed_ShouldReturnData() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            List<Map<String, Object>> dummy = new ArrayList<>();
            Map<String, Object> row = new HashMap<>();
            row.put("bill_no", "AVPHSL-0001");
            row.put("total_sales_value", 105.00);
            row.put("gst", 5.00);
            dummy.add(row);
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class))).thenReturn(dummy);

            List<Map<String, Object>> result = service.getPharmacySalesGstDetailed("2026-09-01", "2026-09-16", null);

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("AVPHSL-0001", result.get(0).get("bill_no"));
        }
    }

    @Test
    void getHsnTaxSummary_ShouldReturnData() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            List<Map<String, Object>> dummy = new ArrayList<>();
            Map<String, Object> row = new HashMap<>();
            row.put("hsn_code", "3004");
            row.put("tax_rate", 5.00);
            row.put("taxable_value", 100.00);
            row.put("gst", 5.00);
            dummy.add(row);
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class))).thenReturn(dummy);

            List<Map<String, Object>> result = service.getHsnTaxSummary("2026-09-01", "2026-09-16");

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("3004", result.get(0).get("hsn_code"));
        }
    }

    @Test
    void getGstTaxLiability_ShouldReturnData() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            List<Map<String, Object>> dummy = new ArrayList<>();
            Map<String, Object> row = new HashMap<>();
            row.put("tax_rate", 5.00);
            row.put("output_tax", 100.00);
            row.put("input_tax", 60.00);
            row.put("net_liability", 40.00);
            dummy.add(row);
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class))).thenReturn(dummy);

            List<Map<String, Object>> result = service.getGstTaxLiability("2026-09-01", "2026-09-16");

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals(40.00, result.get(0).get("net_liability"));
        }
    }

    @Test
    void getPurchaseGstDetails_ShouldReturnData() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            List<Map<String, Object>> dummy = new ArrayList<>();
            Map<String, Object> row = new HashMap<>();
            row.put("grn_no", "GRN-0001");
            row.put("purchase_value", 500.00);
            row.put("purchase_tax", 25.00);
            dummy.add(row);
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class))).thenReturn(dummy);

            List<Map<String, Object>> result = service.getPurchaseGstDetails("2026-09-01", "2026-09-16", null);

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("GRN-0001", result.get(0).get("grn_no"));
        }
    }

    @Test
    void getFilingLines_ShouldReturnData() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            List<Map<String, Object>> dummy = new ArrayList<>();
            Map<String, Object> row = new HashMap<>();
            row.put("source", "PHARMACY");
            row.put("invoice_number", "AVPHSL-0001");
            row.put("taxable_value", 100.00);
            row.put("tax_amount", 5.00);
            dummy.add(row);
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class))).thenReturn(dummy);

            List<Map<String, Object>> result = service.getFilingLines("2026-09-01", "2026-09-16");

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("PHARMACY", result.get(0).get("source"));
        }
    }

    @Test
    void getGstTaxLiability_ShouldUseGrlTaxRateInQuery() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class)))
                    .thenReturn(List.of());

            service.getGstTaxLiability("2026-09-01", "2026-09-16");

            mocked.verify(() -> ReportDbUtil.queryForList(
                    any(JdbcTemplate.class),
                    argThat(sql -> sql.contains("COALESCE(grl.tax_rate, ii.tax_rate, 0)")),
                    any(Object[].class)
            ));
        }
    }

    @Test
    void getPurchaseGstDetails_ShouldUseGrlTaxRateInQuery() {
        try (MockedStatic<ReportDbUtil> mocked = mockStatic(ReportDbUtil.class)) {
            mocked.when(() -> ReportDbUtil.queryForList(any(JdbcTemplate.class), anyString(), any(Object[].class)))
                    .thenReturn(List.of());

            service.getPurchaseGstDetails("2026-09-01", "2026-09-16", null);

            mocked.verify(() -> ReportDbUtil.queryForList(
                    any(JdbcTemplate.class),
                    argThat(sql -> sql.contains("COALESCE(grl.tax_rate, ii.tax_rate, 0)")),
                    any(Object[].class)
            ));
        }
    }
}
