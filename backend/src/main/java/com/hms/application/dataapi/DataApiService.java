package com.hms.application.dataapi;

import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.settings.SettingsRegistryImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Executes pre-registered SQL queries stored in system_settings.
 *
 * Safety rules (same as legacy DataAPIService):
 *   1. Only SELECT statements are permitted — any DML/DDL throws.
 *   2. Only queries registered in system_settings (type=DATA_API) can run.
 *   3. Named parameters (:paramName) are substituted from the request map.
 *   4. Result rows are capped at MAX_ROWS to prevent runaway queries.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataApiService {

    private static final int MAX_ROWS = 1000;
    private static final Pattern NAMED_PARAM = Pattern.compile(":([a-zA-Z_][a-zA-Z0-9_]*)");

    private final SettingsRegistryImpl settings;
    private final DataSource dataSource;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> execute(String queryKey, Map<String, String> params) {
        // Only registered queries — never accept raw SQL
        String rawSql = settings.get("DATA_API", queryKey)
            .orElseThrow(() -> new ResourceNotFoundException(
                "No registered query found for key: " + queryKey));

        validateSelectOnly(rawSql, queryKey);

        // Extract named parameters and build positional JDBC SQL
        List<String>   paramNames  = new ArrayList<>();
        Matcher        matcher     = NAMED_PARAM.matcher(rawSql);
        StringBuffer   jdbcSql     = new StringBuffer();

        while (matcher.find()) {
            paramNames.add(matcher.group(1));
            matcher.appendReplacement(jdbcSql, "?");
        }
        matcher.appendTail(jdbcSql);

        String finalSql = jdbcSql.toString();
        log.debug("DataAPI executing key={} sql={}", queryKey, finalSql);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(finalSql)) {

            // Bind parameters in order
            for (int i = 0; i < paramNames.size(); i++) {
                String paramName  = paramNames.get(i);
                String paramValue = params.get(paramName);
                if (paramValue == null) {
                    throw new BusinessRuleViolationException(
                        "Missing required parameter '" + paramName + "' for query '" + queryKey + "'");
                }
                ps.setString(i + 1, paramValue);
            }

            ps.setMaxRows(MAX_ROWS);
            ResultSet          rs   = ps.executeQuery();
            ResultSetMetaData  meta = rs.getMetaData();
            int                cols = meta.getColumnCount();

            List<Map<String, Object>> rows = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int c = 1; c <= cols; c++) {
                    row.put(meta.getColumnLabel(c), rs.getObject(c));
                }
                rows.add(row);
            }

            log.info("DataAPI query '{}' returned {} rows", queryKey, rows.size());
            return rows;

        } catch (BusinessRuleViolationException | ResourceNotFoundException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("DataAPI execution failed for key={}: {}", queryKey, ex.getMessage());
            throw new BusinessRuleViolationException(
                "Query execution failed: " + ex.getMessage());
        }
    }

    public List<Map<String, String>> listRegisteredQueries() {
        List<Map<String, String>> result = new ArrayList<>();
        settings.getByType("DATA_API").forEach(entry -> {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("key",         entry.getSettingKey());
            item.put("description", entry.getDescription() != null ? entry.getDescription() : "");
            result.add(item);
        });
        return result;
    }

    @Transactional
    public void registerQuery(String key, String sql, String description) {
        validateSelectOnly(sql, key);
        settings.save("DATA_API", key, sql);
        // Store description separately
        settings.save("DATA_API_DESC", key, description);
    }

    private void validateSelectOnly(String sql, String key) {
        String trimmed = sql.trim().toUpperCase();
        if (!trimmed.startsWith("SELECT")) {
            throw new BusinessRuleViolationException(
                "Only SELECT statements are permitted in DataAPI. Query '" + key + "' is not a SELECT.");
        }
        // Reject common DML/DDL keywords anywhere in the statement
        for (String forbidden : List.of("INSERT", "UPDATE", "DELETE", "DROP", "CREATE", "ALTER", "TRUNCATE", "EXEC")) {
            if (trimmed.contains(forbidden)) {
                throw new BusinessRuleViolationException(
                    "Forbidden keyword '" + forbidden + "' found in DataAPI query '" + key + "'");
            }
        }
    }
}
