package com.hms.application.smtp;

import com.hms.domain.smtp.model.SmtpConfig;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.smtp.SmtpConfigRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Properties;
import java.util.UUID;

/**
 * Service layer for SMTP configuration management.
 *
 * <p>Handles CRUD operations and SMTP connection testing via dynamic
 * {@link JavaMailSenderImpl} construction from stored configuration.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SmtpConfigService {

    private final SmtpConfigRepository repo;

    @Transactional
    public SmtpConfig create(SmtpConfig config) {
        return repo.save(config);
    }

    @Transactional
    public SmtpConfig update(UUID id, SmtpConfig updated) {
        SmtpConfig existing = findByIdOrThrow(id);

        existing.setSmtpHost(updated.getSmtpHost());
        existing.setSmtpPort(updated.getSmtpPort());
        existing.setUsername(updated.getUsername());
        // Only update password if a new one is provided (non-null, non-blank)
        if (updated.getPassword() != null && !updated.getPassword().isBlank()) {
            existing.setPassword(updated.getPassword());
        }
        existing.setProtocol(updated.getProtocol());
        existing.setTlsEnabled(updated.isTlsEnabled());
        existing.setSslEnabled(updated.isSslEnabled());
        existing.setFromEmail(updated.getFromEmail());
        existing.setFromName(updated.getFromName());
        existing.setActive(updated.isActive());

        return repo.save(existing);
    }

    @Transactional(readOnly = true)
    public SmtpConfig findById(UUID id) {
        return findByIdOrThrow(id);
    }

    @Transactional(readOnly = true)
    public List<SmtpConfig> findAll() {
        return repo.findAll();
    }

    @Transactional
    public void delete(UUID id) {
        SmtpConfig config = findByIdOrThrow(id);
        config.softDelete();
        repo.save(config);
    }

    /**
     * Tests the SMTP connection by building a dynamic {@link JavaMailSenderImpl}
     * and sending a test email.
     *
     * @param host      SMTP host
     * @param port      SMTP port
     * @param username  SMTP username
     * @param password  SMTP password (plain text for testing)
     * @param protocol  SMTP or SMTPS
     * @param tls       TLS enabled
     * @param ssl       SSL enabled
     * @param fromEmail sender email
     * @param fromName  sender display name
     * @param toEmail   recipient for the test email
     */
    public void testConnection(String host, int port, String username, String password,
                               String protocol, boolean tls, boolean ssl,
                               String fromEmail, String fromName, String toEmail) {
        JavaMailSenderImpl mailSender = buildMailSender(host, port, username, password, protocol, tls, ssl);
        try {
            // Test the connection first
            mailSender.testConnection();

            // Send a test email
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromEmail, fromName != null ? fromName : "HMS System");
            helper.setTo(toEmail);
            helper.setSubject("HMS — SMTP Test Email");
            helper.setText(
                "This is a test email from HMS (Hospital Management System).\n\n" +
                "If you are reading this, your SMTP configuration is working correctly.\n\n" +
                "SMTP Host: " + host + "\n" +
                "SMTP Port: " + port + "\n" +
                "Protocol: " + protocol + "\n" +
                "TLS: " + tls + "\n" +
                "SSL: " + ssl
            );
            mailSender.send(message);
            log.info("SMTP test email sent successfully to {} via {}:{}", toEmail, host, port);
        } catch (Exception e) {
            log.error("SMTP test connection failed: {}", e.getMessage());
            throw new RuntimeException("SMTP test failed: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public void sendResetPasswordOtp(String toEmail, String otp) {
        SmtpConfig activeConfig = repo.findAll().stream()
            .filter(SmtpConfig::isActive)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No active SMTP configuration found"));

        JavaMailSenderImpl mailSender = buildMailSender(
            activeConfig.getSmtpHost(),
            activeConfig.getSmtpPort(),
            activeConfig.getUsername(),
            activeConfig.getPassword(),
            activeConfig.getProtocol(),
            activeConfig.isTlsEnabled(),
            activeConfig.isSslEnabled()
        );

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(activeConfig.getFromEmail(), activeConfig.getFromName() != null ? activeConfig.getFromName() : "HMS Clinical");
            helper.setTo(toEmail);
            helper.setSubject("HMS — Password Reset OTP");
            helper.setText(
                "You have requested to reset your password.\n\n" +
                "Your OTP is: " + otp + "\n\n" +
                "This OTP is valid for 5 minutes. If you did not request this, please ignore this email.\n"
            );
            mailSender.send(message);
            log.info("Reset password OTP sent successfully to {} using active SMTP", toEmail);
        } catch (Exception e) {
            log.error("Failed to send reset password OTP to {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private SmtpConfig findByIdOrThrow(UUID id) {
        return repo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("SMTP Configuration", id));
    }

    private JavaMailSenderImpl buildMailSender(String host, int port, String username,
                                               String password, String protocol,
                                               boolean tls, boolean ssl) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(username);
        sender.setPassword(password);
        sender.setProtocol(protocol != null && protocol.equalsIgnoreCase("SMTPS") ? "smtps" : "smtp");

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", sender.getProtocol());
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");

        if (tls) {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }
        if (ssl) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.ssl.trust", host);
        }

        return sender;
    }
}
