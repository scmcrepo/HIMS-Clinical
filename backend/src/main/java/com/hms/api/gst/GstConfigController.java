package com.hms.api.gst;

import com.hms.api.shared.ApiResponse;
import com.hms.application.gst.GstConfigurationService;
import com.hms.domain.gst.model.GstConfiguration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GST configuration for one hospital (A1, A7).
 *
 * <p>Credentials are never returned. The screen shows whether each is set and lets it be
 * replaced; sending them back so a form could pre-fill them would put a hospital's GSP
 * secret into every browser session that opened Settings.
 */
@RestController
@RequestMapping("/gst/config")
@RequiredArgsConstructor
@PreAuthorize("hasPermission('SETTINGS_GST','')")
public class GstConfigController {

    private final GstConfigurationService configService;

    /** GET /gst/config */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> get() {
        return ResponseEntity.ok(ApiResponse.ok("OK", present(configService.getForCurrentTenant())));
    }

    /** PUT /gst/config?confirmProduction=true */
    @PutMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> save(
            @RequestBody GstConfiguration req,
            @RequestParam(name = "confirmProduction", defaultValue = "false") boolean confirmProduction) {
        return ResponseEntity.ok(ApiResponse.ok("GST configuration saved",
            present(configService.save(req, confirmProduction))));
    }

    public record EnableRequest(boolean enabled) {}

    /**
     * PUT /gst/config/integration — D3: platform super-admin only.
     * The role check lives in the service; see {@code GstConfigurationService}.
     */
    @PutMapping("/integration")
    public ResponseEntity<ApiResponse<Map<String, Object>>> setIntegration(@RequestBody EnableRequest req) {
        GstConfiguration saved = configService.setIntegrationEnabled(req.enabled());
        return ResponseEntity.ok(ApiResponse.ok(
            "GST integration " + (req.enabled() ? "enabled" : "disabled"), present(saved)));
    }

    private Map<String, Object> present(GstConfiguration c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("gstin",              c.getGstin());
        m.put("stateCode",          c.getStateCode());
        m.put("legalName",          c.getLegalName());
        m.put("sandboxBaseUrl",     c.getSandboxBaseUrl());
        m.put("productionBaseUrl",  c.getProductionBaseUrl());
        m.put("activeEnvironment",  c.getActiveEnvironment());
        m.put("autoSyncFrequency",  c.getAutoSyncFrequency());
        m.put("integrationEnabled", c.isIntegrationEnabled());
        // Set-or-not, never the value itself.
        m.put("apiKeySet",          c.getApiKey() != null && !c.getApiKey().isBlank());
        m.put("apiSecretSet",       c.getApiSecret() != null && !c.getApiSecret().isBlank());
        m.put("submittable",        c.isSubmittable());
        return m;
    }
}
