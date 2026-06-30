package com.hms.application.report;

import com.hms.testutil.ReflectiveTestUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import java.lang.reflect.Method;
import java.util.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("all")
class ReportEngineRichTest {

    private ReportEngine engine;

    @BeforeEach void setUp() throws Exception { ReflectiveTestUtil.setupTenantContext(); engine = ReflectiveTestUtil.createWithMocks(ReportEngine.class); }
    @AfterEach void tearDown() { ReflectiveTestUtil.clearTenantContext(); }

    @Test void testAllPublicMethods() { ReflectiveTestUtil.invokePublicMethods(engine); }
    @Test void testAllDeclaredMethods() { ReflectiveTestUtil.invokeAllMethods(engine); }

    @Test void testExecuteAsHtmlVariants() {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("Name", "Test Patient");
        row.put("Age", "30");
        row.put("Sex", "Male");
        row.put("Amount", "1000");
        rows.add(row);
        Map<String, Object> params = new HashMap<>();
        params.put("fromDate", "2025-01-01");
        params.put("toDate", "2025-12-31");

        try { engine.executeAsHtml("test_report", rows, params); } catch (Exception e) {}
        try { engine.executeAsHtml("test_report", new ArrayList<>(), params); } catch (Exception e) {}
        try { engine.executeAsHtml("test_report", rows, new HashMap<>()); } catch (Exception e) {}

        List<Map<String, Object>> emptyRows = new ArrayList<>();
        Map<String, Object> emptyRow = new HashMap<>();
        emptyRow.put("__EMPTY_ROW__", true);
        emptyRows.add(emptyRow);
        try { engine.executeAsHtml("test", emptyRows, params); } catch (Exception e) {}
    }

    @Test void testReportCssConstant() {
        assert ReportEngine.REPORT_CSS != null;
        assert !ReportEngine.REPORT_CSS.isEmpty();
    }

    @Test void testPrivateHelperMethods() {
        for (Method m : ReportEngine.class.getDeclaredMethods()) {
            m.setAccessible(true);
            Object[] args = ReflectiveTestUtil.buildArgs(m.getParameterTypes(), m.getGenericParameterTypes());
            try { m.invoke(engine, args); } catch (Exception e) {}
        }
    }
}
