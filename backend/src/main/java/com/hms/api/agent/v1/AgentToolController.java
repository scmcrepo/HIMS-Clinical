package com.hms.api.agent.v1;

import com.hms.api.agent.v1.request.BookSlotToolRequest;
import com.hms.api.agent.v1.response.AgentToolResponses.BedOccupancy;
import com.hms.api.agent.v1.response.AgentToolResponses.BillingLedger;
import com.hms.api.agent.v1.response.AgentToolResponses.BookingResult;
import com.hms.api.agent.v1.response.AgentToolResponses.SlotOption;
import com.hms.api.shared.ApiResponse;
import com.hms.application.agent.AgentIdempotencyService;
import com.hms.application.agent.AgentToolAuditService;
import com.hms.application.agent.AgentToolService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The agent tool surface.
 *
 * <p>Deliberately small. Exposing the existing 176 controllers to an agent would
 * be a far larger attack surface and an unversionable contract; a curated set of
 * well-named tools is easier to reason about, easier to audit, and easier to
 * describe to a model.
 *
 * <p>Each method carries its own {@code @PreAuthorize} scope rather than relying
 * on a class-level annotation, so a read-only token cannot reach a write tool.
 */
@RestController
@RequestMapping("/agent/v1/tools")
@RequiredArgsConstructor
public class AgentToolController {

    private final AgentToolService tools;
    private final AgentToolAuditService audit;
    private final AgentIdempotencyService idempotency;

    private static UUID tokenId(HttpServletRequest request) {
        Object attr = request.getAttribute("agentTokenId");
        return attr instanceof UUID uuid ? uuid : null;
    }

    @GetMapping("/slot-availability")
    @PreAuthorize("hasPermission('AGENT_SCHEDULING_READ','')")
    public ResponseEntity<ApiResponse<List<SlotOption>>> slotAvailability(
            @RequestParam UUID providerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            HttpServletRequest request) {

        List<SlotOption> slots = audit.record(
            "check_slot_availability", tokenId(request),
            () -> tools.checkSlotAvailability(providerId, date));
        return ResponseEntity.ok(ApiResponse.ok("Slot availability", slots));
    }

    @PostMapping("/book-slot")
    @PreAuthorize("hasPermission('AGENT_SCHEDULING_WRITE','')")
    public ResponseEntity<ApiResponse<BookingResult>> bookSlot(
            @Valid @RequestBody BookSlotToolRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {

        // Idempotency wraps the audited action, so a replay is recorded as a
        // replay rather than as a second booking.
        AgentIdempotencyService.Outcome<BookingResult> outcome = idempotency.execute(
            idempotencyKey, "book_slot", body,
            () -> audit.record("book_slot", tokenId(request), idempotencyKey,
                               () -> tools.bookSlot(body), BookingResult::appointmentId),
            BookingResult.class,
            // WO-024/D-005: the cached responseBody carries this patient's
            // appointment detail. Without the id, an erasure request has nothing
            // to match on and the cached copy outlives the record it came from.
            body.patientId());

        BookingResult result = outcome.replayed()
            ? new BookingResult(outcome.value().appointmentId(), outcome.value().slotId(),
                                outcome.value().appointmentDate(), outcome.value().status(), true)
            : outcome.value();

        return ResponseEntity.ok()
            .header("Idempotency-Replayed", Boolean.toString(outcome.replayed()))
            .body(ApiResponse.ok(outcome.replayed() ? "Replayed" : "Appointment booked", result));
    }

    @GetMapping("/bed-occupancy")
    @PreAuthorize("hasPermission('AGENT_BED_READ','')")
    public ResponseEntity<ApiResponse<BedOccupancy>> bedOccupancy(
            @RequestParam(required = false) String ward,
            HttpServletRequest request) {

        BedOccupancy occupancy = audit.record(
            "check_bed_occupancy", tokenId(request), () -> tools.checkBedOccupancy(ward));
        return ResponseEntity.ok(ApiResponse.ok("Bed occupancy", occupancy));
    }

    @GetMapping("/billing-ledger")
    @PreAuthorize("hasPermission('AGENT_BILLING_READ','')")
    public ResponseEntity<ApiResponse<BillingLedger>> billingLedger(
            @RequestParam UUID patientId,
            HttpServletRequest request) {

        BillingLedger ledger = audit.record(
            "fetch_billing_ledger", tokenId(request), () -> tools.fetchBillingLedger(patientId));
        return ResponseEntity.ok(ApiResponse.ok("Billing ledger", ledger));
    }
}
