package com.hms.api.config;
import com.hms.api.shared.ApiResponse;
import com.hms.infrastructure.settings.SettingsRegistryImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
@RestController @RequestMapping("/config") @RequiredArgsConstructor
public class ConfigController {
    private final SettingsRegistryImpl settingsRegistry;
    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> getAll() {
        List<Map<String, String>> all = settingsRegistry.getByType("APP_CONFIGURATION").stream()
            .map(s -> Map.of("type", s.getSettingType(), "key", s.getSettingKey(), "value", s.getSettingValue() != null ? s.getSettingValue() : "")).toList();
        return ResponseEntity.ok(ApiResponse.ok("OK", all));
    }
    @GetMapping("/values")
    public ResponseEntity<ApiResponse<Map<String, String>>> getValues() {
        return ResponseEntity.ok(ApiResponse.ok("OK", settingsRegistry.getValueMapByType("APP_CONFIGURATION")));
    }
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> save(@RequestBody Map<String, String> body) {
        String key = body.get("key");
        if (key == null || key.isBlank()) return ResponseEntity.badRequest().body(ApiResponse.error("key is required"));
        settingsRegistry.save(body.getOrDefault("type", "APP_CONFIGURATION"), key, body.get("value"));
        return ResponseEntity.ok(ApiResponse.ok("Config saved successfully"));
    }
    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<Void>> saveBatch(@RequestBody List<Map<String, String>> entries) {
        entries.forEach(e -> { String k = e.get("key"); if (k != null && !k.isBlank()) settingsRegistry.save(e.getOrDefault("type","APP_CONFIGURATION"), k, e.get("value")); });
        return ResponseEntity.ok(ApiResponse.ok("Configuration saved successfully"));
    }
    @GetMapping("/current-date")
    public ResponseEntity<ApiResponse<String>> currentDate() {
        return ResponseEntity.ok(ApiResponse.ok("OK", LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
    }
    @GetMapping("/session-timeout")
    public ResponseEntity<ApiResponse<Integer>> sessionTimeout() { 
        return ResponseEntity.ok(ApiResponse.ok("OK", settingsRegistry.getSessionTimeoutMinutes())); 
    }
    @GetMapping("/hospital")
    public ResponseEntity<ApiResponse<Map<String, String>>> getHospitalProfile() {
        return ResponseEntity.ok(ApiResponse.ok("OK", settingsRegistry.getValueMapByType("HOSPITAL_PARAM")));
    }
    @PostMapping("/hospital")
    public ResponseEntity<ApiResponse<Void>> saveHospitalProfile(@RequestBody Map<String, String> body) {
        if (body.containsKey("name"))    settingsRegistry.save("HOSPITAL_PARAM","hospital.name.param",body.get("name"));
        if (body.containsKey("address")) settingsRegistry.save("HOSPITAL_PARAM","hospital.address.param",body.get("address"));
        if (body.containsKey("phone"))   settingsRegistry.save("HOSPITAL_PARAM","hospital.contactNo.param",body.get("phone"));
        return ResponseEntity.ok(ApiResponse.ok("Hospital profile saved successfully"));
    }
}
