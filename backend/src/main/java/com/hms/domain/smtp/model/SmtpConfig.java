package com.hms.domain.smtp.model;

import com.hms.domain.shared.model.AuditableEntity;
import com.hms.security.encryption.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * SMTP mail-server configuration entity.
 *
 * <p>Each tenant/branch can maintain one or more SMTP configurations.
 * The {@code password} field is transparently encrypted at rest using
 * AES-256-GCM via {@link EncryptedStringConverter}.
 */
@Entity
@Table(name = "smtp_config")
@Getter
@Setter
@NoArgsConstructor
public class SmtpConfig extends AuditableEntity {

    @Column(name = "smtp_host", nullable = false)
    private String smtpHost;

    @Column(name = "smtp_port", nullable = false)
    private int smtpPort;

    @Column(name = "username", nullable = false)
    private String username;

    /** Encrypted at rest via AES-256-GCM. Never returned in API responses. */
    @Column(name = "password", length = 500)
    @Convert(converter = EncryptedStringConverter.class)
    private String password;

    @Column(name = "protocol", nullable = false, length = 50)
    private String protocol = "SMTP";

    @Column(name = "tls_enabled", nullable = false)
    private boolean tlsEnabled;

    @Column(name = "ssl_enabled", nullable = false)
    private boolean sslEnabled;

    @Column(name = "from_email", nullable = false)
    private String fromEmail;

    @Column(name = "from_name")
    private String fromName;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
