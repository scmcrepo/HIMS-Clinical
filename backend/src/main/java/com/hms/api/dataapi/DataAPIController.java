package com.hms.api.dataapi;
import org.springframework.security.access.prepost.PreAuthorize;

import com.hms.api.shared.ApiResponse;
import com.hms.application.dataapi.DataApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * DataAPIController — executes pre-registered SQL queries by key.
 *
 * Mirrors legacy DataAPIController behaviour:
 *   GET /data-api/{queryKey}?param1=val1&param2=val2
 *   → looks up query definition from system_settings (type=DATA_API, key={queryKey})
 *   → injects named parameters from query string
 *   → returns List<Map<String,Object>> result set
 *
 * Security: only queries registered in system_settings are executable —
 * no ad-hoc SQL is accepted. Requires ROLE_DATA_API feature permission.
 */
@RestController
@RequestMapping({"/data-api", "/dataAPI"})
@RequiredArgsConstructor
@PreAuthorize("hasPermission('SETTINGS_DATAQUERY','')")
public class DataAPIController {

    private final DataApiService dataApiService;

    /**
     * Execute a stored query by key.
     * All query string parameters are available as named parameters in the SQL.
     * e.g. GET /data-api/patientsByDate?date=2024-01-15
     */
    @GetMapping("/{queryKey}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> execute(
            @PathVariable("queryKey") String queryKey,
            @RequestParam Map<String, String> params) {
        return ResponseEntity.ok(ApiResponse.ok("OK",
            dataApiService.execute(queryKey, params)));
    }

    /**
     * List all registered query keys (metadata only — no SQL exposed).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> listQueries() {
        return ResponseEntity.ok(ApiResponse.ok("OK", dataApiService.listRegisteredQueries()));
    }

    /**
     * Register a new stored query (admin only).
     * body: { "key": "myQuery", "sql": "SELECT ...", "description": "..." }
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> register(
            @RequestBody Map<String, String> body) {
        String key  = body.get("key");
        String sql  = body.get("sql");
        String desc = body.getOrDefault("description", "");
        if (key == null || sql == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("key and sql are required"));
        }
        dataApiService.registerQuery(key, sql, desc);
        return ResponseEntity.ok(ApiResponse.ok("Query registered successfully"));
    }

    /** PUT /dataAPI — updates a stored query definition */
    @PutMapping
    public ResponseEntity<ApiResponse<Void>> update(@RequestBody java.util.Map<String, String> body) {
        String key  = body.get("key");
        String sql  = body.get("sql");
        String desc = body.getOrDefault("description", "");
        if (key != null && sql != null) dataApiService.registerQuery(key, sql, desc);
        return ResponseEntity.ok(ApiResponse.ok("Query updated successfully"));
    }

    /** DELETE /dataAPI — removes a stored query by key */
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteQuery(@RequestBody java.util.Map<String, String> body) {
        String key = body.get("key");
        if (key != null) dataApiService.registerQuery(key, "/* DELETED */", "");
        return ResponseEntity.ok(ApiResponse.ok("Query deleted successfully"));
    }
}
