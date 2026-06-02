package com.hms.api.sms;

import com.hms.api.shared.ApiResponse;
import com.hms.infrastructure.settings.SettingsRegistryImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * SmsTemplateController — CRUD for SMS message templates stored in system_settings.
 *
 * Templates are stored with type=SMS_TEMPLATE, key={templateKey}.
 * The template body uses $variable$ placeholders:
 *   $hospitalName$, $firstName$, $patientNo$, $date$, $amount$, etc.
 *
 * Standard template keys (mirrors legacy SmsTemplate enum):
 *   RegisterPatient, BookAppointment, AdvanceCollection,
 *   PaymentCollection, SettleBilling, IPBilling, CancelAppointment
 */
@RestController
@RequestMapping("/sms-templates")
@RequiredArgsConstructor
public class SmsTemplateController {

    private final SettingsRegistryImpl settingsRegistry;

    private static final List<String> STANDARD_KEYS = List.of(
        "RegisterPatient", "BookAppointment", "AdvanceCollection",
        "PaymentCollection", "SettleBilling", "IPBilling", "CancelAppointment"
    );

    /** Get all SMS templates (standard + custom). */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> getAll() {
        // Merge standard keys with whatever is saved
        Map<String, String> saved = settingsRegistry.getValueMapByType("SMS_TEMPLATE");
        List<Map<String, String>> result = new ArrayList<>();

        // Standard templates first
        for (String key : STANDARD_KEYS) {
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("key",     key);
            entry.put("body",    saved.getOrDefault(key, ""));
            entry.put("isStandard", "true");
            result.add(entry);
        }

        // Any custom templates not in the standard list
        saved.forEach((k, v) -> {
            if (!STANDARD_KEYS.contains(k)) {
                Map<String, String> entry = new LinkedHashMap<>();
                entry.put("key",  k);
                entry.put("body", v);
                entry.put("isStandard", "false");
                result.add(entry);
            }
        });

        return ResponseEntity.ok(ApiResponse.ok("OK", result));
    }

    /** Get a single template by key. */
    @GetMapping("/{templateKey}")
    public ResponseEntity<ApiResponse<Map<String, String>>> getByKey(
            @PathVariable("templateKey") String templateKey) {
        String body = settingsRegistry.get("SMS_TEMPLATE", templateKey).orElse("");
        return ResponseEntity.ok(ApiResponse.ok("OK",
            Map.of("key", templateKey, "body", body)));
    }

    /** Save or update a template body. */
    @PutMapping("/{templateKey}")
    public ResponseEntity<ApiResponse<Void>> save(
            @PathVariable("templateKey") String templateKey,
            @RequestBody Map<String, String> body) {
        String templateBody = body.get("body");
        if (templateBody == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("body field is required"));
        }
        settingsRegistry.save("SMS_TEMPLATE", templateKey, templateBody);
        return ResponseEntity.ok(ApiResponse.ok("Template saved successfully"));
    }

    /** Delete a custom template (standard templates cannot be deleted, only emptied). */
    @DeleteMapping("/{templateKey}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable("templateKey") String templateKey) {
        // Empty the body rather than delete the row — preserves key awareness
        settingsRegistry.save("SMS_TEMPLATE", templateKey, "");
        return ResponseEntity.ok(ApiResponse.ok("Template cleared"));
    }

    /** Returns the list of supported placeholder variables. */
    @GetMapping("/placeholders")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> getPlaceholders() {
        List<Map<String, String>> vars = List.of(
            Map.of("variable", "$hospitalName$",   "description", "Hospital name from config"),
            Map.of("variable", "$firstName$",      "description", "Patient first name"),
            Map.of("variable", "$patientNo$",      "description", "Patient sequence number"),
            Map.of("variable", "$date$",           "description", "Event date (dd/MM/yyyy)"),
            Map.of("variable", "$slot$",           "description", "Appointment slot time"),
            Map.of("variable", "$doctorName$",     "description", "Doctor full name"),
            Map.of("variable", "$amount$",         "description", "Payment amount in ₹"),
            Map.of("variable", "$billNo$",         "description", "Bill sequence number"),
            Map.of("variable", "$status$",         "description", "Appointment or bill status")
        );
        return ResponseEntity.ok(ApiResponse.ok("OK", vars));
    }
}
