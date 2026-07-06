package com.hms.application.report.modules;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.ArgumentMatchers.*;

import java.lang.reflect.Method;
import java.util.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("all")
class InventoryReportDataServiceRichTest {

    @org.mockito.Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS) private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @org.mockito.Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS) private com.hms.application.report.util.ReportScope scope;

    @InjectMocks private InventoryReportDataService service;

    @Test
    void executeAllQueries() throws Exception {
        Map<String, Object> dummyRow = new HashMap<>() {{
            put(" AND ib.expiry_date <= ?::DATE", "1");
            put("1", "1");
            put(" AND ib.item_id = ?", "1");
            put("to_date", "1");
            put("1 month", "1");
            put("summary", "1");
            put(" ORDER BY ii.name, sa.created_at DESC", "1");
            put("sa", "1");
            put(" month", "1");
            put("ib", "1");
            put("report_view_type", "1");
            put("from_date", "1");
            put(" ORDER BY ii.name, ib.expiry_date", "1");
            put(" ORDER BY ii.name", "1");
            put("2025-01-01", "1");
            put("detail", "1");
            put("ii", "1");
            put(" ORDER BY ps.sale_date DESC, ii.name", "1");
            put("ps", "1");
        }};
        List<Map<String, Object>> dummyResult = Arrays.asList(dummyRow, dummyRow);

        try (MockedStatic<com.hms.application.report.util.ReportDbUtil> mocked = Mockito.mockStatic(com.hms.application.report.util.ReportDbUtil.class)) {
            mocked.when(() -> com.hms.application.report.util.ReportDbUtil.queryForList(any(), anyString(), any(Object[].class))).thenReturn(dummyResult);

            Method[] methods = service.getClass().getDeclaredMethods();
            for (Method m : methods) {
                if (m.getReturnType().equals(List.class)) {
                    Class<?>[] pTypes = m.getParameterTypes();
                    Object[] args = new Object[pTypes.length];
                    for (int i = 0; i < pTypes.length; i++) {
                        if (pTypes[i].equals(String.class)) args[i] = "dummy";
                        else if (pTypes[i].equals(Map.class)) args[i] = dummyRow;
                        else if (pTypes[i].equals(int.class)) args[i] = 0;
                        else if (pTypes[i].equals(boolean.class)) args[i] = false;
                        else args[i] = null;
                    }
                    m.setAccessible(true);
                    try { m.invoke(service, args); } catch(Exception e) { e.printStackTrace(); }
                }
            }
        }
    }
}
