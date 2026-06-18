package com.hms.infrastructure.settings;

import com.hms.infrastructure.persistence.tenant.TenantEntity;
import com.hms.infrastructure.persistence.tenant.TenantJpaRepository;
import com.hms.infrastructure.persistence.tenant.BranchEntity;
import com.hms.infrastructure.persistence.tenant.BranchJpaRepository;
import com.hms.infrastructure.tenant.TenantContext;
import com.hms.infrastructure.tenant.BranchContext;
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
    private final TenantJpaRepository tenantRepo;
    private final BranchJpaRepository branchRepo;
    private volatile Map<String, String> cache = new HashMap<>();

    public SettingsRegistryImpl(SystemSettingJpaRepository repo,
                                TenantJpaRepository tenantRepo,
                                BranchJpaRepository branchRepo) {
        this.repo = repo;
        this.tenantRepo = tenantRepo;
        this.branchRepo = branchRepo;
        reloadCache();
    }

    public Optional<String> get(String type, String key) {
        if ("HOSPITAL_PARAM".equals(type)) {
            UUID tenantId = TenantContext.get();
            if (tenantId != null) {
                UUID branchId = BranchContext.get();
                if (branchId != null) {
                    Optional<BranchEntity> optBranch = branchRepo.findById(branchId);
                    if (optBranch.isPresent()) {
                        BranchEntity branch = optBranch.get();
                        if ("hospital.name.param".equals(key)) {
                            return Optional.ofNullable(branch.getName());
                        } else if ("hospital.address.param".equals(key)) {
                            return Optional.of(branch.getAddress() != null && !branch.getAddress().isBlank() 
                                ? branch.getAddress() 
                                : tenantRepo.findById(tenantId).map(TenantEntity::getAddress).orElse(""));
                        } else if ("hospital.contactNo.param".equals(key)) {
                            return Optional.of(branch.getContactNumber() != null && !branch.getContactNumber().isBlank() 
                                ? branch.getContactNumber() 
                                : tenantRepo.findById(tenantId).map(TenantEntity::getContactNumber).orElse(""));
                        }
                    }
                }
                Optional<TenantEntity> optTenant = tenantRepo.findById(tenantId);
                if (optTenant.isPresent()) {
                    TenantEntity tenant = optTenant.get();
                    if ("hospital.name.param".equals(key)) {
                        return Optional.ofNullable(tenant.getName());
                    } else if ("hospital.address.param".equals(key)) {
                        return Optional.ofNullable(tenant.getAddress());
                    } else if ("hospital.contactNo.param".equals(key)) {
                        return Optional.ofNullable(tenant.getContactNumber());
                    }
                }
            }
        }
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
        if ("HOSPITAL_PARAM".equals(type)) {
            UUID tenantId = TenantContext.get();
            if (tenantId != null) {
                UUID branchId = BranchContext.get();
                if (branchId != null) {
                    branchRepo.findById(branchId).ifPresent(branch -> {
                        if ("hospital.name.param".equals(key)) {
                            branch.setName(value);
                        } else if ("hospital.address.param".equals(key)) {
                            branch.setAddress(value);
                        } else if ("hospital.contactNo.param".equals(key)) {
                            branch.setContactNumber(value);
                        }
                        branchRepo.save(branch);
                    });
                    return;
                }
                tenantRepo.findById(tenantId).ifPresent(tenant -> {
                    if ("hospital.name.param".equals(key)) {
                        tenant.setName(value);
                    } else if ("hospital.address.param".equals(key)) {
                        tenant.setAddress(value);
                    } else if ("hospital.contactNo.param".equals(key)) {
                        tenant.setContactNumber(value);
                    }
                    tenantRepo.save(tenant);
                });
                return;
            }
        }

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
        if ("HOSPITAL_PARAM".equals(type)) {
            Map<String, String> map = new LinkedHashMap<>();
            map.put("hospital.name.param", get("HOSPITAL_PARAM", "hospital.name.param").orElse(""));
            map.put("hospital.address.param", get("HOSPITAL_PARAM", "hospital.address.param").orElse(""));
            map.put("hospital.contactNo.param", get("HOSPITAL_PARAM", "hospital.contactNo.param").orElse(""));
            return map;
        }
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
