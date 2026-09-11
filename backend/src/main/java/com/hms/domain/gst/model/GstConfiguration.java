package com.hms.domain.gst.model;

import com.hms.domain.shared.model.AuditableEntity;
import com.hms.security.encryption.EncryptedStringConverter;
import com.hms.security.encryption.PiiField;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One hospital's GST filing configuration (D2: filing is at hospital level).
 *
 * <p>Credentials and the GSTIN are encrypted at rest through the same converter the rest
 * of the codebase uses for sensitive fields. {@link #stateCode} deliberately is not:
 * it is the first two digits of the GSTIN, it is read on every payload row as the
 * default place of supply, and decrypting the whole GSTIN per row to recover two
 * characters would be wasteful. A state code identifies a state, not a taxpayer.
 */
@Entity
@Table(name = "gst_configurations")
@Getter @Setter @NoArgsConstructor
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class GstConfiguration extends AuditableEntity {

    @PiiField(category = PiiField.PiiCategory.TAX_ID, description = "Hospital GSTIN")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "gstin", length = 512)
    private String gstin;

    /** First two digits of {@link #gstin}. Kept in plaintext — see the class note. */
    @Column(name = "state_code", length = 2)
    private String stateCode;

    @Column(name = "legal_name", length = 200)
    private String legalName;

    @PiiField(category = PiiField.PiiCategory.OTHER, description = "GSP API key")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "api_key", length = 512)
    private String apiKey;

    @PiiField(category = PiiField.PiiCategory.OTHER, description = "GSP API secret")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "api_secret", length = 512)
    private String apiSecret;

    @Column(name = "sandbox_base_url", length = 300)
    private String sandboxBaseUrl;

    @Column(name = "production_base_url", length = 300)
    private String productionBaseUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "active_environment", nullable = false, length = 20)
    private GstEnvironment activeEnvironment = GstEnvironment.SANDBOX;

    /**
     * Whether this hospital sees the filing UI at all (D3).
     * Only a platform super-admin may change this; enforced in the service layer.
     */
    @Column(name = "integration_enabled", nullable = false)
    private boolean integrationEnabled = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "auto_sync_frequency", nullable = false, length = 20)
    private GstSyncFrequency autoSyncFrequency = GstSyncFrequency.MANUAL;

    /** The endpoint the active environment points at, or null if it is not configured. */
    @Transient
    public String activeBaseUrl() {
        return activeEnvironment == GstEnvironment.PRODUCTION ? productionBaseUrl : sandboxBaseUrl;
    }

    /** True when there is enough here to attempt a submission at all. */
    @Transient
    public boolean isSubmittable() {
        return integrationEnabled
            && gstin != null && !gstin.isBlank()
            && apiKey != null && !apiKey.isBlank()
            && apiSecret != null && !apiSecret.isBlank()
            && activeBaseUrl() != null && !activeBaseUrl().isBlank();
    }
}
