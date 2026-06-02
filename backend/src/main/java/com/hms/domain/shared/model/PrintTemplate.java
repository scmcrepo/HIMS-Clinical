package com.hms.domain.shared.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

/**
 * PrintTemplate — persisted print layout definition.
 *
 * Supports two output modes:
 *   HTML       — rendered via Pug/Jade or legacy HTML, printed via window.print()
 *   DOT_MATRIX — rendered to ESC/POS raw strings, dispatched via QZ Tray WebSocket
 */
@Entity
@Table(name = "print_templates")
@Getter
@Setter
@NoArgsConstructor
public class PrintTemplate extends AuditableEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** Maps to TemplateType enum value (e.g. BILL, LAB, PRESCRIPTION). */
    @Column(name = "document_type", nullable = false, length = 50)
    private String documentType;

    /** HTML or DOT_MATRIX */
    @Column(name = "print_mode", nullable = false, length = 20)
    private String printMode = "HTML";

    // ── Page geometry ──────────────────────────────────────────────────────────

    @Column(name = "height", nullable = false, length = 20)
    private String height = "297mm";

    @Column(name = "width", nullable = false, length = 20)
    private String width = "210mm";

    @Column(name = "margin_top", nullable = false, length = 20)
    private String marginTop = "10mm";

    @Column(name = "margin_bottom", nullable = false, length = 20)
    private String marginBottom = "10mm";

    @Column(name = "margin_left", nullable = false, length = 20)
    private String marginLeft = "10mm";

    @Column(name = "margin_right", nullable = false, length = 20)
    private String marginRight = "10mm";

    @Column(name = "margin", length = 20)
    private String margin;

    @Column(name = "page_size", nullable = false, length = 20)
    private String pageSize = "A4";

    // ── Template content ───────────────────────────────────────────────────────

    /** Pug/Jade source — compiled server-side at print time */
    @Column(name = "pug_template", columnDefinition = "TEXT")
    private String pugTemplate;

    /** Legacy raw HTML — used if pug_template is empty */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /** Named system printer for DOT_MATRIX mode; used as QZ Tray config name */
    @Column(name = "default_printer", length = 100)
    private String defaultPrinter;

    @JsonProperty("isDefault")
    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;
}
