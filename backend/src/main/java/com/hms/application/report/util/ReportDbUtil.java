package com.hms.application.report.util;

import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.ResultSetMetaData;
import java.util.*;

public class ReportDbUtil {
    
    public static List<Map<String, Object>> queryForList(JdbcTemplate jdbcTemplate, String sql, Object... args) {
        return jdbcTemplate.query(sql, rs -> {
            List<Map<String, Object>> list = new ArrayList<>();
            ResultSetMetaData md = rs.getMetaData();
            int cols = md.getColumnCount();
            
            while (rs.next()) {
                Map<String, Object> map = new LinkedHashMap<>();
                for (int i = 1; i <= cols; i++) {
                    map.put(md.getColumnLabel(i), rs.getObject(i));
                }
                list.add(map);
            }
            
            if (list.isEmpty()) {
                Map<String, Object> map = new LinkedHashMap<>();
                for (int i = 1; i <= cols; i++) {
                    map.put(md.getColumnLabel(i), null);
                }
                map.put("__EMPTY_ROW__", true);
                list.add(map);
            }
            return list;
        }, args);
    }
}
