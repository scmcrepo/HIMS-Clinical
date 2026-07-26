package com.hms.infrastructure.persistence.abha;

import com.hms.domain.shared.model.AuditableEntity;
import com.hms.security.encryption.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A patient's ABHA identity.
 *
 * <p>ABHA number and address are personal identifiers, so both are encrypted.
 * Because ciphertext here is non-deterministic they cannot be searched directly
 * — hence the blind-index token columns, matching the pattern already used for
 * staff contact details.
 *
 * <p>Note what is absent: no Aadhaar column. Aadhaar is passed to ABDM for OTP
 * and discarded. Storing it would create an obligation nothing here needs.
 */
@Entity
@Table(name = "abha_linkages")
@Getter
@Setter
public class AbhaLinkageEntity extends AuditableEntity {

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "abha_number")
    private String abhaNumber;

    /** Deterministic blind index — encrypted columns are not searchable. */
    @Column(name = "abha_number_token", length = 64)
    private String abhaNumberToken;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "abha_address")
    private String abhaAddress;

    @Column(name = "abha_address_token", length = 64)
    private String abhaAddressToken;

    /** PENDING_OTP | LINKED | FAILED | NOT_INTEGRATED */
    @Column(name = "linkage_state", nullable = false, length = 24)
    private String linkageState = "PENDING_OTP";

    @Column(name = "transaction_id", length = 64)
    private String transactionId;

    @Column(name = "linked_at")
    private Instant linkedAt;

    @Column(name = "failure_code", length = 60)
    private String failureCode;

    /**
     * DPDP consent is separate from ABDM's own consent-manager artefact. A
     * patient consenting to treatment has not consented to a national health-id
     * linkage, and the two must be recorded independently.
     */
    @Column(name = "consent_recorded_at")
    private Instant consentRecordedAt;

    @Column(name = "consent_version", length = 20)
    private String consentVersion;
}
