package com.hms.api.grievance;

import com.hms.api.grievance.request.GrievanceRequests;
import com.hms.api.shared.ApiResponse;
import com.hms.application.grievance.GrievanceService;
import com.hms.infrastructure.persistence.grievance.ComplianceContactEntity;
import com.hms.infrastructure.persistence.grievance.GrievanceEntity;
import com.hms.infrastructure.persistence.grievance.GrievanceEventEntity;
import com.hms.exception.ResourceNotFoundException;
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
 * Grievance redressal — DPDP s. 8(9) and s. 13.
 *
 * <p>{@code GRIEVANCE_RAISE} is held by clinical and reception staff, not only
 * administrators. A complaint that can only be logged by an administrator is a
 * complaint that gets talked out of existence at the desk, and the Act asks for
 * an <em>effective</em> mechanism rather than a reachable one.
 *
 * <p>{@link #publishedContact} is the one unauthenticated endpoint here, and
 * necessarily so: a contact point nobody can read without logging in has not
 * been published. It serves organisational contact information only.
 */
@RestController
@RequestMapping("/compliance/grievances")
@RequiredArgsConstructor
public class GrievanceController {

    private final GrievanceService service;

    @PostMapping
    @PreAuthorize("hasPermission('GRIEVANCE_RAISE','')")
    public ResponseEntity<ApiResponse<GrievanceEntity>> raise(
            @Valid @RequestBody GrievanceRequests.Raise body) {

        GrievanceEntity g = service.raise(
            body.patientId(), body.complainantContact(), body.category(),
            body.channel(), body.subject(), body.body());

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
            "Grievance " + g.getGrievanceRef() + " recorded. "
            + "Acknowledge it within three days.", g));
    }

    /**
     * Tell the complainant we have it.
     *
     * <p>Its own endpoint because being heard and being answered are different
     * things, and the gap between them is where someone decides whether to go to
     * the Board instead.
     */
    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasPermission('GRIEVANCE_MANAGE','')")
    public ResponseEntity<ApiResponse<GrievanceEntity>> acknowledge(
            @PathVariable UUID id,
            @RequestBody(required = false) GrievanceRequests.Acknowledge body) {

        return ResponseEntity.ok(ApiResponse.ok(
            "Acknowledged", service.acknowledge(id, body == null ? null : body.note())));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasPermission('GRIEVANCE_MANAGE','')")
    public ResponseEntity<ApiResponse<GrievanceEntity>> assign(
            @PathVariable UUID id, @Valid @RequestBody GrievanceRequests.Assign body) {
        return ResponseEntity.ok(ApiResponse.ok("Assigned", service.assign(id, body.assignee())));
    }

    @PostMapping("/{id}/notes")
    @PreAuthorize("hasPermission('GRIEVANCE_MANAGE','')")
    public ResponseEntity<ApiResponse<GrievanceEventEntity>> addNote(
            @PathVariable UUID id, @Valid @RequestBody GrievanceRequests.Note body) {
        return ResponseEntity.ok(ApiResponse.ok(
            "Note added", service.addNote(id, body.note(), body.communicated())));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasPermission('GRIEVANCE_MANAGE','')")
    public ResponseEntity<ApiResponse<GrievanceEntity>> resolve(
            @PathVariable UUID id, @Valid @RequestBody GrievanceRequests.Resolve body) {
        return ResponseEntity.ok(ApiResponse.ok(
            "Resolved", service.resolve(id, body.resolution())));
    }

    /**
     * The complainant went to the Board.
     *
     * <p>Recorded, not treated as a failure. They are entitled to at any point,
     * and a mechanism that flagged escalation as an error would be measuring the
     * wrong thing.
     */
    @PostMapping("/{id}/escalation")
    @PreAuthorize("hasPermission('GRIEVANCE_MANAGE','')")
    public ResponseEntity<ApiResponse<GrievanceEntity>> recordEscalation(
            @PathVariable UUID id, @Valid @RequestBody GrievanceRequests.Escalate body) {
        return ResponseEntity.ok(ApiResponse.ok(
            "Escalation recorded", service.recordEscalation(id, body.boardReference())));
    }

    /** Link a complaint to the incident it turned out to be about. */
    @PostMapping("/{id}/incident")
    @PreAuthorize("hasPermission('GRIEVANCE_MANAGE','')")
    public ResponseEntity<ApiResponse<GrievanceEntity>> linkIncident(
            @PathVariable UUID id, @Valid @RequestBody GrievanceRequests.LinkIncident body) {
        return ResponseEntity.ok(ApiResponse.ok(
            "Linked to incident", service.linkIncident(id, body.incidentId())));
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasPermission('GRIEVANCE_MANAGE','')")
    public ResponseEntity<ApiResponse<GrievanceEntity>> withdraw(
            @PathVariable UUID id,
            @RequestBody(required = false) GrievanceRequests.Withdraw body) {
        return ResponseEntity.ok(ApiResponse.ok(
            "Withdrawn", service.withdraw(id, body == null ? null : body.reason())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('GRIEVANCE_RAISE','')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> get(@PathVariable UUID id) {
        GrievanceEntity g = service.get(id);
        return ResponseEntity.ok(ApiResponse.of(Map.of(
            "grievance", g,
            "timeline", service.timelineFor(id),
            "overdue", g.isOverdue(java.time.Instant.now()))));
    }

    @GetMapping
    @PreAuthorize("hasPermission('GRIEVANCE_RAISE','')")
    public ResponseEntity<ApiResponse<List<GrievanceEntity>>> queue(
            @RequestParam(required = false) String state) {
        return ResponseEntity.ok(ApiResponse.of(service.queue(state)));
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasPermission('GRIEVANCE_RAISE','')")
    public ResponseEntity<ApiResponse<List<GrievanceEntity>>> history(
            @PathVariable UUID patientId) {
        return ResponseEntity.ok(ApiResponse.of(service.historyFor(patientId)));
    }

    // ── The published contact ─────────────────────────────────────────────

    @PostMapping("/contact")
    @PreAuthorize("hasPermission('COMPLIANCE_CONTACT_MANAGE','')")
    public ResponseEntity<ApiResponse<ComplianceContactEntity>> publishContact(
            @Valid @RequestBody GrievanceRequests.PublishContact body) {

        return ResponseEntity.ok(ApiResponse.ok(
            "Contact published",
            service.publishContact(body.displayName(), body.designation(), body.email(),
                                   body.phone(), body.postalAddress(),
                                   body.isDpo(), body.basedInIndia())));
    }

    @GetMapping("/contact")
    @PreAuthorize("hasPermission('GRIEVANCE_RAISE','')")
    public ResponseEntity<ApiResponse<ComplianceContactEntity>> currentContact() {
        return service.publishedContact()
            .map(c -> ResponseEntity.ok(ApiResponse.of(c)))
            .orElseThrow(() -> new ResourceNotFoundException(
                "No data protection contact has been published for this hospital — "
                + "s. 8(9) requires one"));
    }

    /**
     * The contact point, readable without signing in.
     *
     * <p>Unauthenticated by necessity: a contact point nobody can read without an
     * account has not been published, and the people most likely to need it are
     * precisely those who cannot or will not log in.
     *
     * <p>Serves organisational contact information only — a name or role, an
     * email, a phone number and an address the hospital chose to make public.
     * The tenant is taken as a parameter because there is no session to infer it
     * from; this is the one place in the system where that is the intended
     * behaviour rather than a bug.
     */
    @GetMapping("/contact/public")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> publishedContact(
            @RequestParam UUID tenantId) {

        return service.publishedContactFor(tenantId)
            .map(c -> {
                Map<String, Object> body = Map.of(
                    "displayName", c.getDisplayName(),
                    "designation", c.getDesignation() == null ? "" : c.getDesignation(),
                    "email", c.getEmail(),
                    "phone", c.getPhone() == null ? "" : c.getPhone(),
                    "postalAddress", c.getPostalAddress() == null ? "" : c.getPostalAddress(),
                    "isDataProtectionOfficer", c.isDpo(),
                    "escalation", "If you are not satisfied with our response, you may "
                                + "complain to the Data Protection Board of India."
                );
                return ResponseEntity.ok(ApiResponse.of(body));
            })
            .orElseThrow(() -> new ResourceNotFoundException(
                "No data protection contact has been published for this hospital"));
    }
}
