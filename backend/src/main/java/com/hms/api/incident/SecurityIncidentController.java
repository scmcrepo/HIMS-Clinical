package com.hms.api.incident;

import com.hms.api.incident.request.IncidentRequests;
import com.hms.api.shared.ApiResponse;
import com.hms.application.incident.SecurityIncidentService;
import com.hms.infrastructure.persistence.incident.IncidentAffectedPrincipalEntity;
import com.hms.infrastructure.persistence.incident.SecurityIncidentEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The breach register — DPDP s. 8(6) and Rule 7.
 *
 * <p>Three permissions, deliberately asymmetric. {@code INCIDENT_RAISE} is held
 * by clinical and reception staff: whoever notices something wrong must be able
 * to say so without hunting for an administrator, and a near-miss nobody could
 * file is a near-miss nobody learns from. {@code INCIDENT_MANAGE} triages and
 * closes. {@code INCIDENT_NOTIFY} records external notifications, and is the
 * narrowest because telling the Board is an irreversible act with legal weight.
 *
 * <p>There is no endpoint that notifies the Board automatically. This records
 * that a human filed something, because a system that could file on a hospital's
 * behalf could file wrongly on a hospital's behalf.
 */
@RestController
@RequestMapping("/compliance/incidents")
@RequiredArgsConstructor
public class SecurityIncidentController {

    private final SecurityIncidentService service;

    @PostMapping
    @PreAuthorize("hasPermission('INCIDENT_RAISE','')")
    public ResponseEntity<ApiResponse<SecurityIncidentEntity>> raise(
            @Valid @RequestBody IncidentRequests.Raise body) {

        SecurityIncidentEntity incident = service.raise(
            body.category(), body.severity(), body.summary(), body.detail(),
            "MANUAL_REPORT", body.dataCategories(), body.detectedAt(), body.scopeUncertain());

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
            "Incident " + incident.getIncidentRef() + " recorded", incident));
    }

    /**
     * Record who was affected.
     *
     * <p>Separate from raising, because scope is almost never known at the
     * moment of discovery and forcing it up front produces a confident zero
     * where an honest "not yet known" belongs.
     */
    @PostMapping("/{id}/affected")
    @PreAuthorize("hasPermission('INCIDENT_MANAGE','')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordAffected(
            @PathVariable UUID id,
            @Valid @RequestBody IncidentRequests.AffectedPatients body) {

        int added = service.recordAffectedPatients(id, body.patientIds());
        return ResponseEntity.ok(ApiResponse.ok(
            "Affected list updated", Map.of("added", added)));
    }

    @PostMapping("/{id}/contain")
    @PreAuthorize("hasPermission('INCIDENT_MANAGE','')")
    public ResponseEntity<ApiResponse<SecurityIncidentEntity>> contain(
            @PathVariable UUID id,
            @Valid @RequestBody IncidentRequests.Contain body) {

        return ResponseEntity.ok(ApiResponse.ok(
            "Marked contained", service.markContained(id, body.remediation())));
    }

    /** Records that a human filed with the Board. Does not file anything itself. */
    @PostMapping("/{id}/board-notification")
    @PreAuthorize("hasPermission('INCIDENT_NOTIFY','')")
    public ResponseEntity<ApiResponse<SecurityIncidentEntity>> recordBoardNotification(
            @PathVariable UUID id,
            @Valid @RequestBody IncidentRequests.BoardNotification body) {

        return ResponseEntity.ok(ApiResponse.ok(
            "Board notification recorded",
            service.recordBoardNotification(id, body.boardReference(), body.detailReport())));
    }

    /**
     * The notice owed to an affected person.
     *
     * <p>Generated rather than left to whoever is drafting at 2am. Rule 7 wants
     * four things — nature, likely consequences, remedial measures, contact
     * details — and the one most often dropped under pressure is consequences,
     * which is the part that lets someone decide whether to act.
     */
    @GetMapping("/{id}/notice")
    @PreAuthorize("hasPermission('INCIDENT_NOTIFY','')")
    public ResponseEntity<ApiResponse<Map<String, String>>> draftNotice(
            @PathVariable UUID id,
            @RequestParam(required = false) String contactPoint) {

        return ResponseEntity.ok(ApiResponse.of(
            Map.of("notice", service.draftPrincipalNotice(id, contactPoint))));
    }

    @PostMapping("/{id}/notify-principals")
    @PreAuthorize("hasPermission('INCIDENT_NOTIFY','')")
    public ResponseEntity<ApiResponse<SecurityIncidentService.NotificationOutcome>> notifyPrincipals(
            @PathVariable UUID id,
            @Valid @RequestBody IncidentRequests.NotifyPrincipals body) {

        var outcome = service.recordPrincipalNotifications(id, body.channel());
        String message = outcome.failed() == 0
            ? "All affected people notified"
            : outcome.failed() + " notification(s) failed — the incident stays open "
              + "until everyone has been reached";
        return ResponseEntity.ok(ApiResponse.ok(message, outcome));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasPermission('INCIDENT_MANAGE','')")
    public ResponseEntity<ApiResponse<SecurityIncidentEntity>> close(
            @PathVariable UUID id,
            @Valid @RequestBody IncidentRequests.Close body) {

        return ResponseEntity.ok(ApiResponse.ok(
            "Incident closed", service.close(id, body.rootCause())));
    }

    @PostMapping("/{id}/dismiss")
    @PreAuthorize("hasPermission('INCIDENT_MANAGE','')")
    public ResponseEntity<ApiResponse<SecurityIncidentEntity>> dismiss(
            @PathVariable UUID id,
            @Valid @RequestBody IncidentRequests.Dismiss body) {

        return ResponseEntity.ok(ApiResponse.ok(
            "Incident dismissed", service.dismiss(id, body.reason())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('INCIDENT_RAISE','')")
    public ResponseEntity<ApiResponse<SecurityIncidentEntity>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(service.get(id)));
    }

    @GetMapping("/{id}/affected")
    @PreAuthorize("hasPermission('INCIDENT_MANAGE','')")
    public ResponseEntity<ApiResponse<List<IncidentAffectedPrincipalEntity>>> affected(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(service.affectedFor(id)));
    }

    @GetMapping
    @PreAuthorize("hasPermission('INCIDENT_RAISE','')")
    public ResponseEntity<ApiResponse<List<SecurityIncidentEntity>>> queue(
            @RequestParam(required = false) String state) {
        return ResponseEntity.ok(ApiResponse.of(service.queue(state)));
    }
}
