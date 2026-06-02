package com.hms.infrastructure.settings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

/**
 * Replaces legacy ConfigReader + ConfigHolder.
 * In-memory cache is rebuilt on every write so changes take effect immediately
 * without restart — same behaviour as legacy ConfigAspect reloadSysConfigProperties().
 */
@Service
public class SettingsRegistryImpl {

    private final SystemSettingJpaRepository repo;
    private volatile Map<String, String> cache = new HashMap<>();

    public SettingsRegistryImpl(SystemSettingJpaRepository repo) {
        this.repo = repo;
        reloadCache();
    }

    public Optional<String> get(String type, String key) {
        return Optional.ofNullable(cache.get(type + "." + key));
    }

    public boolean getBoolean(String type, String key, boolean defaultValue) {
        return get(type, key).map("1"::equals).orElse(defaultValue);
    }

    public boolean isBedChargeAutomated() {
        return getBoolean("APP_CONFIGURATION", "bed.type.calculation", false);
    }

    public boolean isPatientPrefixMultiple() {
        return getBoolean("APP_CONFIGURATION", "prefix.patient.multiple", false);
    }

    public String getHospitalName() {
        return get("HOSPITAL_PARAM", "hospital.name.param").orElse("HMS Hospital");
    }

    public int getSessionTimeoutMinutes() {
        try {
            return Integer.parseInt(get("APP_CONFIGURATION", "max.inactive.time").orElse("15"));
        } catch (NumberFormatException e) {
            return 15;
        }
    }

    @Transactional
    public void save(String type, String key, String value) {
        repo.findBySettingTypeAndSettingKey(type, key).ifPresentOrElse(
            existing -> {
                existing.setSettingValue(value);
                existing.setModifiedAt(Instant.now());
                repo.save(existing);
            },
            () -> {
                SystemSettingEntity s = new SystemSettingEntity();
                s.setSettingType(type); s.setSettingKey(key); s.setSettingValue(value);
                s.setCreatedAt(Instant.now()); s.setModifiedAt(Instant.now());
                repo.save(s);
            });
        reloadCache();
    }

    @Transactional(readOnly = true)
    public List<SystemSettingEntity> getByType(String type) {
        return repo.findBySettingType(type);
    }

    @Transactional(readOnly = true)
    public Map<String, String> getValueMapByType(String type) {
        Map<String, String> map = new LinkedHashMap<>();
        repo.findBySettingType(type).forEach(s ->
            map.put(s.getSettingKey(), s.getSettingValue() != null ? s.getSettingValue() : ""));
        return map;
    }

    public void reloadCache() {
        Map<String, String> fresh = new HashMap<>();
        repo.findAll().forEach(s ->
            fresh.put(s.getSettingType() + "." + s.getSettingKey(), s.getSettingValue()));
        this.cache = fresh;
    }
}
