package com.hms.infrastructure.persistence.grievance;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * The contact point published to data principals.
 *
 * <p>Required of every Fiduciary under s. 8(9), and of a Significant Data
 * Fiduciary as a named India-based DPO under Rule 13.
 *
 * <p>Not encrypted, deliberately: the entire purpose of this record is to be
 * published. It is organisational contact information, not personal data about a
 * data principal, and encrypting it would break the one endpoint that has to
 * serve it without authentication.
 */
@Entity
@Table(name = "compliance_contacts")
@Getter
@Setter
public class ComplianceContactEntity extends AuditableEntity {

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "designation", length = 120)
    private String designation;

    @Column(name = "email", nullable = false, length = 160)
    private String email;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "postal_address", columnDefinition = "TEXT")
    private String postalAddress;

    /**
     * True only where the tenant has determined it is a Significant Data
     * Fiduciary and this is its DPO. Explicit because "we have a DPO" is a legal
     * claim with obligations attached, not a job title.
     */
    @Column(name = "is_dpo", nullable = false)
    private boolean dpo;

    @Column(name = "based_in_india", nullable = false)
    private boolean basedInIndia = true;

    @Column(name = "active_from", nullable = false)
    private Instant activeFrom = Instant.now();

    @Column(name = "active_to")
    private Instant activeTo;

    public boolean isLive() {
        return activeTo == null;
    }
}
