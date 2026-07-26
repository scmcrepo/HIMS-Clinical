package com.hms.api.agent.v1;

import com.hms.api.shared.ApiResponse;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool definitions for the orchestrator, derived from the live OpenAPI document.
 *
 * <p>Generated rather than hand-maintained. Two copies of a schema — one in Java
 * annotations, one in Python tool definitions — diverge the first time either
 * changes, and the first symptom is a model confidently passing a parameter that
 * no longer exists.
 */
@RestController
@RequestMapping("/agent/v1/tools")
@RequiredArgsConstructor
public class AgentToolSchemaController {

    private static final String TOOLS_PREFIX = "/agent/v1/tools";

    private final ObjectProvider<OpenAPI> openApiProvider;

    @GetMapping("/schema")
    @PreAuthorize("hasPermission('AGENT_TOOLS_READ','')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> schema() {
        OpenAPI api = openApiProvider.getIfAvailable();
        List<Map<String, Object>> tools = new ArrayList<>();

        if (api != null && api.getPaths() != null) {
            api.getPaths().forEach((path, item) -> {
                if (!path.contains(TOOLS_PREFIX) || path.endsWith("/schema")) {
                    return;
                }
                addIfPresent(tools, path, "get", item.getGet());
                addIfPresent(tools, path, "post", item.getPost());
            });
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("version", "v1");
        payload.put("tools", tools);
        return ResponseEntity.ok(ApiResponse.ok("Agent tool schema", payload));
    }

    private static void addIfPresent(List<Map<String, Object>> out, String path,
                                     String method, Operation operation) {
        if (operation == null) {
            return;
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        List<Parameter> params = operation.getParameters();
        if (params != null) {
            for (Parameter p : params) {
                Map<String, Object> prop = new LinkedHashMap<>();
                prop.put("type", p.getSchema() == null ? "string" : p.getSchema().getType());
                // The description is what the model reads to decide whether this
                // is the right tool, so an empty one is a real defect.
                prop.put("description", p.getDescription() == null ? "" : p.getDescription());
                properties.put(p.getName(), prop);
                if (Boolean.TRUE.equals(p.getRequired())) {
                    required.add(p.getName());
                }
            }
        }

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", required);

        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", toolName(path));
        tool.put("description", operation.getSummary() == null
            ? operation.getOperationId() : operation.getSummary());
        tool.put("method", method.toUpperCase(java.util.Locale.ROOT));
        tool.put("path", path);
        tool.put("parameters", parameters);
        out.add(tool);
    }

    static String toolName(String path) {
        String tail = path.substring(path.lastIndexOf('/') + 1);
        return tail.replace('-', '_');
    }

    /** Unused import guard for PathItem; kept for readability of the walk above. */
    @SuppressWarnings("unused")
    private static void unusedPathItemReference(PathItem item) {
    }
}
