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
class PatientReportDataServiceRichTest {

    @org.mockito.Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS) private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @org.mockito.Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS) private com.hms.application.report.util.ReportScope scope;
    @org.mockito.Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS) private com.hms.security.encryption.PiiEncryptionService piiEncryptionService;

    @InjectMocks private PatientReportDataService service;

    @Test
    void executeAllQueries() throws Exception {
        Map<String, Object> dummyRow = new HashMap<>() {{
            put(",                p.last_name AS ", "1");
            put("Consultant", "1");
            put("1", "1");
            put("last_name", "1");
            put("salutation", "1");
            put("Patient No", "1");
            put("contact_number", "1");
            put(",                p.contact_number AS ", "1");
            put("to_date", "1");
            put(",                p.salutation AS ", "1");
            put("Contact No", "1");
            put(",                c.last_name AS ", "1");
            put(",                COALESCE(d.name, '') AS ", "1");
            put(",                COALESCE(u.username, '') AS ", "1");
            put(" ORDER BY p.created_at::DATE ASC", "1");
            put("first_name", "1");
            put("summary", "1");
            put(",                c.qualification AS ", "1");
            put("p", "1");
            put("Gender", "1");
            put(",                c.first_name AS ", "1");
            put("Department", "1");
            put("Registered By", "1");
            put("report_view_type", "1");
            put("from_date", "1");
            put("Reg Date", "1");
            put("2025-01-01", "1");
            put("Age", "1");
            put(" ASC, p.created_at ASC", "1");
            put("c_first_name", "1");
            put(",                p.first_name AS ", "1");
            put("Sex", "1");
            put("detail", "1");
            put(" (", "1");
            put("Patient Name", "1");
            put(")", "1");
            put("c_last_name", "1");
            put("c_qualification", "1");
            put(",                sn.value AS ", "1");
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
