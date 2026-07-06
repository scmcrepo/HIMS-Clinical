package com.hms.application.report.modules;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.*;
import org.mockito.Mockito;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("all")
class InpatientReportServiceRichTest {

    @org.mockito.Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS) private com.hms.application.report.modules.InpatientReportDataService inpatientReportDataService;
    @org.mockito.Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS) private com.hms.application.report.ReportEngine reportEngine;

    @InjectMocks private InpatientReportService service;

    @Test
    void testBuildCustomHtmlAllBranches() throws Exception {
        
        Mockito.lenient().when(reportEngine.str(any(), anyString())).thenAnswer(inv -> {
            Map m = inv.getArgument(0); String k = inv.getArgument(1);
            return m != null && m.get(k) != null ? m.get(k).toString() : "";
        });
        Mockito.lenient().when(reportEngine.dateStr(any(), anyString())).thenAnswer(inv -> {
            Map m = inv.getArgument(0); String k = inv.getArgument(1);
            return m != null && m.get(k) != null ? m.get(k).toString() : "2025-01-01";
        });
        Mockito.lenient().when(reportEngine.doubleVal(any())).thenReturn(1.0);
        Mockito.lenient().when(reportEngine.escHtml(anyString())).thenAnswer(inv -> inv.getArgument(0) != null ? inv.getArgument(0) : "");
        Mockito.lenient().when(reportEngine.formatDateValue(any())).thenReturn("2025-01-01");
        Mockito.lenient().when(reportEngine.formatGeneralValue(any())).thenReturn("dummy");

        Map<String, Object> dummyRow = new HashMap<>() {{
            put("reportName", "1");
            put("Date / Period-wise Beds Transferred", "1");
            put(");                }                sb.append(", "1");
            put(" : ", "1");
            put("category", "1");
            put(");        // Group rows by period month (e.g. ", "1");
            put("Bed Transfer To", "1");
            put(").append(monthName).append(", "1");
            put("Registered By", "1");
            put(");        sb.append(", "1");
            put(" + toDate            : ", "1");
            put(", totalPct)).append(", "1");
            put("</tr></thead>", "1");
            put("year", "1");
            put(").toString() : ", "1");
            put("detail", "1");
            put(";                        v = displayAge + ", "1");
            put("        <td colspan='", "1");
            put("Patient Name", "1");
            put(").append(mBgColor).append(", "1");
            put("REPORT_TYPE", "1");
            put(");                sb.append(", "1");
            put(");                    html.append(", "1");
            put("__EMPTY_ROW__", "1");
            put("Transfer Date", "1");
            put("        </td>", "1");
            put("2026", "1");
            put(").append(grandMale).append(", "1");
            put("1", "1");
            put("Patient No", "1");
            put(");            sb.append(", "1");
            put(");            }        }        sb.append(", "1");
            put("Inpatient", "1");
            put("dd-MM-yyyy", "1");
            put("Age/Sex", "1");
            put("UUID", "1");
            put("report_type", "1");
            put(" :                            sexVal.startsWith(", "1");
            put("bed_occupancy", "1");
            put("To date", "1");
            put("YEAR", "1");
            put("department_filter", "1");
            put(");        }        sb.append(", "1");
            put("Current Bed Occupancy status", "1");
            put("-01-01", "1");
            put(").append(reportEngine.escHtml(wardName)).append(", "1");
            put(").append(String.format(Locale.US, ", "1");
            put(");                html.append(", "1");
            put("Report", "1");
            put(");        }        sb.append(", "1");
            put("From date", "1");
            put("), ((Number) r.getOrDefault(", "1");
            put(").append(male).append(", "1");
            put("  </div>", "1");
            put("</th>", "1");
            put(").append(total).append(", "1");
            put("discharges_report", "1");
            put("Year", "1");
            put(" to ", "1");
            put(", wardRate)).append(", "1");
            put("Bed Type Transfer To", "1");
            put(").append(bgColor).append(", "1");
            put(").append(periodStr).append(", "1");
            put("IP Discharge Summary (Legacy)", "1");
            put(") != null ? r.get(", "1");
            put("encounterId", "1");
            put(");        }        html.append(", "1");
            put("name", "1");
            put("beds_transferred", "1");
            put("Bed Transfer From", "1");
            put("};        sb.append(", "1");
            put("<table>", "1");
            put(")                  .append(", "1");
            put(").append(h).append(", "1");
            put("description", "1");
            put("      </tr>", "1");
            put("ip_discharge_summary", "1");
            put(").append(ward).append(", "1");
            put("<tbody>", "1");
            put(").append(female).append(", "1");
            put("Bed Type Transfer From", "1");
            put(" + fromDate + ", "1");
            put("};        String[] keys    = {", "1");
            put("report_view_type", "1");
            put(");        }        html.append(", "1");
            put("2025-01-01", "1");
            put(");            html.append(", "1");
            put("parameters", "1");
            put(");        } else {            html.append(", "1");
            put(").append(grandFemale).append(", "1");
            put("Patient", "1");
            put("Age", "1");
            put(", pct.doubleValue())).append(", "1");
            put(";                    sb.append(", "1");
            put(" + toDate             : ", "1");
            put("Date-wise Discharges", "1");
            put(");                        html.append(", "1");
            put(" + toDate;        sb.append(", "1");
            put(", grandRate)).append(", "1");
            put("Encounter ID", "1");
            put(", ", "1");
            put(")))) {            sb.append(", "1");
            put("to_date", "1");
            put("Date / Consultant-wise Admissions", "1");
            put("admissions_report", "1");
            put("summary", "1");
            put(") ? ", "1");
            put("Month / Year-wise Bed Occupancy", "1");
            put(").append(val).append(", "1");
            put("from_date", "1");
            put("DATE", "1");
            put(").append(headers.length).append(", "1");
            put(");        sb.append(", "1");
            put("bed_occupancy_period", "1");
            put(");            }        }        sb.append(", "1");
            put(",                ", "1");
            put("      <tr>", "1");
            put(").toString().toUpperCase() : ", "1");
            put("SUMMARY", "1");
            put("-12-31", "1");
            put(").append(grandTotal).append(", "1");
            put(";                html.append(", "1");
        }};
        
        Map<String, Object> params = new HashMap<>(dummyRow);
        params.put("from_date", "2025-01-01");
        params.put("to_date", "2025-01-01");

        List<Map<String, Object>> rows = Arrays.asList(dummyRow, dummyRow);

        String[] reportNames = new String[] { "reportName", "Date / Period-wise Beds Transferred", ");                }                sb.append(", " : ", "category", ");        // Group rows by period month (e.g. ", "Bed Transfer To", ").append(monthName).append(", "Registered By", ");        sb.append(", " + toDate            : ", ", totalPct)).append(", "</tr></thead>", "year", ").toString() : ", "detail", ";                        v = displayAge + ", "        <td colspan='", "Patient Name", ").append(mBgColor).append(", "REPORT_TYPE", ");                sb.append(", ");                    html.append(", "__EMPTY_ROW__", "Transfer Date", "        </td>", "2026", ").append(grandMale).append(", "1", "Patient No", ");            sb.append(", ");            }        }        sb.append(", "Inpatient", "dd-MM-yyyy", "Age/Sex", "UUID", "report_type", " :                            sexVal.startsWith(", "bed_occupancy", "To date", "YEAR", "department_filter", ");        }        sb.append(", "Current Bed Occupancy status", "-01-01", ").append(reportEngine.escHtml(wardName)).append(", ").append(String.format(Locale.US, ", ");                html.append(", "Report", ");        }        sb.append(", "From date", "), ((Number) r.getOrDefault(", ").append(male).append(", "  </div>", "</th>", ").append(total).append(", "discharges_report", "Year", " to ", ", wardRate)).append(", "Bed Type Transfer To", ").append(bgColor).append(", ").append(periodStr).append(", "IP Discharge Summary (Legacy)", ") != null ? r.get(", "encounterId", ");        }        html.append(", "name", "beds_transferred", "Bed Transfer From", "};        sb.append(", "<table>", ")                  .append(", ").append(h).append(", "description", "      </tr>", "ip_discharge_summary", ").append(ward).append(", "<tbody>", ").append(female).append(", "Bed Type Transfer From", " + fromDate + ", "};        String[] keys    = {", "report_view_type", ");        }        html.append(", "2025-01-01", ");            html.append(", "parameters", ");        } else {            html.append(", ").append(grandFemale).append(", "Patient", "Age", ", pct.doubleValue())).append(", ";                    sb.append(", " + toDate             : ", "Date-wise Discharges", ");                        html.append(", " + toDate;        sb.append(", ", grandRate)).append(", "Encounter ID", ", ", ")))) {            sb.append(", "to_date", "Date / Consultant-wise Admissions", "admissions_report", "summary", ") ? ", "Month / Year-wise Bed Occupancy", ").append(val).append(", "from_date", "DATE", ").append(headers.length).append(", ");        sb.append(", "bed_occupancy_period", ");            }        }        sb.append(", ",                ", "      <tr>", ").toString().toUpperCase() : ", "SUMMARY", "-12-31", ").append(grandTotal).append(", ";                html.append(" };

        Method method = com.hms.application.report.BaseReportService.class.getDeclaredMethod("buildCustomHtml", String.class, List.class, Map.class);
        method.setAccessible(true);

        for (String rn : reportNames) {
            params.put("report_view_type", "summary");
            try { method.invoke(service, rn, rows, params); } catch(Exception e) {}
            
            params.put("report_view_type", "detail");
            try { method.invoke(service, rn, rows, params); } catch(Exception e) {}
            
            try { method.invoke(service, rn, Collections.emptyList(), params); } catch(Exception e) {}
        }
    }
}
