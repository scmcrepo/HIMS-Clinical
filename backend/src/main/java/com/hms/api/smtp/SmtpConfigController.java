package com.hms.api.smtp;

import com.hms.api.shared.ApiResponse;
import com.hms.application.smtp.SmtpConfigService;
import com.hms.domain.smtp.model.SmtpConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for SMTP Configuration management.
 *
 * <p>All endpoints are secured with {@code SETTINGS_SMTP} permission.
 * Passwords are <b>never</b> returned in API responses.
 */
@RestController
@RequestMapping("/smtp-config")
@RequiredArgsConstructor
@PreAuthorize("hasPermission('SETTINGS_SMTP','')")
public class SmtpConfigController {

    private final SmtpConfigService smtpConfigService;

    // ── CRUD ─────────────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<ApiResponse<SmtpConfigResponse>> create(
            @Valid @RequestBody SmtpConfigRequest request) {
        SmtpConfig entity = mapToEntity(request);
        SmtpConfig saved = smtpConfigService.create(entity);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("SMTP configuration created", SmtpConfigResponse.from(saved)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SmtpConfigResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody SmtpConfigRequest request) {
        SmtpConfig entity = mapToEntity(request);
        SmtpConfig updated = smtpConfigService.update(id, entity);
        return ResponseEntity.ok(ApiResponse.ok("SMTP configuration updated", SmtpConfigResponse.from(updated)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SmtpConfigResponse>> getById(@PathVariable UUID id) {
        SmtpConfig config = smtpConfigService.findById(id);
        return ResponseEntity.ok(ApiResponse.ok("OK", SmtpConfigResponse.from(config)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SmtpConfigResponse>>> getAll() {
        List<SmtpConfigResponse> configs = smtpConfigService.findAll().stream()
            .map(SmtpConfigResponse::from)
            .toList();
        return ResponseEntity.ok(ApiResponse.ok("OK", configs));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        smtpConfigService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("SMTP configuration deleted"));
    }

    // ── Test Connection ──────────────────────────────────────────────────────────

    @PostMapping("/test")
    public ResponseEntity<ApiResponse<Void>> testConnection(
            @Valid @RequestBody SmtpTestRequest request) {
        smtpConfigService.testConnection(
            request.smtpHost(), request.smtpPort(), request.username(), request.password(),
            request.protocol(), request.tlsEnabled(), request.sslEnabled(),
            request.fromEmail(), request.fromName(), request.toEmail()
        );
        return ResponseEntity.ok(ApiResponse.ok("Test email sent successfully"));
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────────

    /** Input DTO for create/update — includes password. */
    public record SmtpConfigRequest(
        @NotBlank(message = "SMTP host is required")
        String smtpHost,

        @NotNull(message = "SMTP port is required")
        @Min(value = 1, message = "Port must be between 1 and 65535")
        @Max(value = 65535, message = "Port must be between 1 and 65535")
        Integer smtpPort,

        @NotBlank(message = "Username is required")
        String username,

        String password,

        @NotBlank(message = "Protocol is required")
        @Pattern(regexp = "SMTP|SMTPS", message = "Protocol must be SMTP or SMTPS")
        String protocol,

        boolean tlsEnabled,
        boolean sslEnabled,

        @NotBlank(message = "From email is required")
        @Email(message = "From email must be a valid email address")
        String fromEmail,

        String fromName,
        boolean active
    ) {}

    /** Output DTO — password is excluded for security. */
    public record SmtpConfigResponse(
        UUID id,
        String smtpHost,
        int smtpPort,
        String username,
        String protocol,
        boolean tlsEnabled,
        boolean sslEnabled,
        String fromEmail,
        String fromName,
        boolean active,
        Instant createdAt,
        Instant modifiedAt
    ) {
        static SmtpConfigResponse from(SmtpConfig e) {
            return new SmtpConfigResponse(
                e.getId(), e.getSmtpHost(), e.getSmtpPort(), e.getUsername(),
                e.getProtocol(), e.isTlsEnabled(), e.isSslEnabled(),
                e.getFromEmail(), e.getFromName(), e.isActive(),
                e.getCreatedAt(), e.getModifiedAt()
            );
        }
    }

    /** Input DTO for the test connection endpoint. */
    public record SmtpTestRequest(
        @NotBlank String smtpHost,
        @NotNull @Min(1) @Max(65535) Integer smtpPort,
        @NotBlank String username,
        @NotBlank String password,
        @NotBlank String protocol,
        boolean tlsEnabled,
        boolean sslEnabled,
        @NotBlank @Email String fromEmail,
        String fromName,
        @NotBlank @Email(message = "Recipient email must be a valid email") String toEmail
    ) {}

    // ── Mapping helper ───────────────────────────────────────────────────────────

    private SmtpConfig mapToEntity(SmtpConfigRequest req) {
        SmtpConfig config = new SmtpConfig();
        config.setSmtpHost(req.smtpHost());
        config.setSmtpPort(req.smtpPort());
        config.setUsername(req.username());
        config.setPassword(req.password());
        config.setProtocol(req.protocol());
        config.setTlsEnabled(req.tlsEnabled());
        config.setSslEnabled(req.sslEnabled());
        config.setFromEmail(req.fromEmail());
        config.setFromName(req.fromName());
        config.setActive(req.active());
        return config;
    }
}
