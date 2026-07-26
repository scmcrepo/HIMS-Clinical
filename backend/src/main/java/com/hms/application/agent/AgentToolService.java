package com.hms.application.agent;

import com.hms.api.agent.v1.request.BookSlotToolRequest;
import com.hms.api.agent.v1.response.AgentToolResponses.BedOccupancy;
import com.hms.api.agent.v1.response.AgentToolResponses.BillingLedger;
import com.hms.api.agent.v1.response.AgentToolResponses.BookingResult;
import com.hms.api.agent.v1.response.AgentToolResponses.LedgerEntry;
import com.hms.api.agent.v1.response.AgentToolResponses.SlotOption;
import com.hms.api.appointment.request.BookAppointmentRequest;
import com.hms.api.appointment.response.AppointmentResponse;
import com.hms.api.appointment.response.SlotAvailabilityResponse;
import com.hms.api.bed.response.BedResponse;
import com.hms.api.billing.response.BillSummaryResponse;
import com.hms.application.appointment.AppointmentSchedulingService;
import com.hms.application.bed.BedManagementService;
import com.hms.application.billing.BillingOperationsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The agent tool surface's business layer.
 *
 * <p>Every method delegates to an existing service. Nothing here reimplements
 * scheduling, bed or billing logic: a second copy of a business rule diverges
 * from the first the moment either is edited, and the divergence surfaces as an
 * agent doing something a receptionist could not.
 *
 * <p>What this layer *does* own is the projection from human-facing DTOs to
 * agent-safe ones. That is not ceremony — {@code BedResponse} carries
 * {@code allocatedPatientName} and {@code BillSummaryResponse} carries
 * {@code patientName}, and either would travel from here into an LLM prompt.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentToolService {

    private final AppointmentSchedulingService scheduling;
    private final BedManagementService beds;
    private final BillingOperationsService billing;

    // ── scheduling ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SlotOption> checkSlotAvailability(UUID providerId, LocalDate date) {
        List<SlotAvailabilityResponse> slots = scheduling.getSlotAvailability(providerId, date);
        List<SlotOption> options = new ArrayList<>(slots.size());
        for (SlotAvailabilityResponse s : slots) {
            if (s.availableCount() <= 0) {
                continue; // do not offer the agent a slot it cannot book
            }
            options.add(new SlotOption(
                s.slotId(),
                s.fromTime() == null ? null : s.fromTime().toString(),
                s.toTime() == null ? null : s.toTime().toString(),
                s.availableCount()));
        }
        return options;
    }

    @Transactional
    public BookingResult bookSlot(BookSlotToolRequest req) {
        // Walk-in name/phone fields are deliberately left null: the agent surface
        // does not accept them (see BookSlotToolRequest).
        BookAppointmentRequest delegate = new BookAppointmentRequest(
            req.patientId(), req.providerId(), req.slotId(), req.appointmentDate(),
            req.notes(), null, null, null, null, null);

        AppointmentResponse booked = scheduling.bookAppointment(delegate);
        return new BookingResult(
            booked.id(), req.slotId(), req.appointmentDate().toString(),
            "CONFIRMED", false);
    }

    // ── beds ─────────────────────────────────────────────────────────────────

    /**
     * Occupancy as counts.
     *
     * <p>Aggregated rather than passed through: the underlying {@code BedResponse}
     * names the patient in each allocated bed, and an agent asking "are there beds
     * free" has no business receiving a ward roster.
     */
    @Transactional(readOnly = true)
    public BedOccupancy checkBedOccupancy(String roomCategory) {
        List<BedResponse> all = beds.getAllBeds();

        Map<String, Integer> byStatus = new LinkedHashMap<>();
        int total = 0;
        int occupied = 0;
        int available = 0;

        for (BedResponse bed : all) {
            if (roomCategory != null && !roomCategory.isBlank()
                && !roomCategory.equalsIgnoreCase(bed.roomCategoryName())) {
                continue;
            }
            total++;
            String status = bed.bedStatus() == null ? "UNKNOWN" : bed.bedStatus().name();
            byStatus.merge(status, 1, Integer::sum);
            switch (status) {
                case "ALLOCATED" -> occupied++;
                case "AVAILABLE" -> available++;
                default -> { }
            }
        }
        return new BedOccupancy(total, occupied, available, byStatus);
    }

    // ── billing ──────────────────────────────────────────────────────────────

    /**
     * Financial ledger for one patient.
     *
     * <p>Filtering client-side over a paged search is not ideal. The existing
     * {@code searchBills} takes a free-text query rather than a patient id, and
     * adding a repository method to a live billing path is a change that deserves
     * its own work order rather than arriving as a side effect of the agent
     * surface. Revisit when bill-by-patient is needed elsewhere.
     */
    @Transactional(readOnly = true)
    public BillingLedger fetchBillingLedger(UUID patientId) {
        var page = billing.searchBills(null, null, null, PageRequest.of(0, 200));

        List<LedgerEntry> entries = new ArrayList<>();
        long totalBilled = 0;
        long totalDue = 0;

        for (BillSummaryResponse bill : page.getContent()) {
            if (!patientId.equals(bill.patientId())) {
                continue;
            }
            totalBilled += bill.billAmount();
            totalDue += bill.dueAmount();
            entries.add(new LedgerEntry(
                bill.id(),
                bill.billNumber(),
                bill.billDate() == null ? null : bill.billDate().toString(),
                bill.billAmount(),
                bill.dueAmount(),
                bill.status() == null ? null : bill.status().name()));
        }
        return new BillingLedger(patientId, totalBilled, totalDue, entries);
    }
}
