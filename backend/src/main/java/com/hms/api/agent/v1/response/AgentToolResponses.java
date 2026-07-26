package com.hms.api.agent.v1.response;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent-facing response shapes.
 *
 * <p>These exist rather than reusing the human-facing DTOs because those carry
 * PII the agent must not receive: {@code BedResponse} includes
 * {@code allocatedPatientName}, {@code BillSummaryResponse} includes
 * {@code patientName}. Passing them straight through would put patient names into
 * the orchestrator, into LLM prompts, and from there into a model provider's
 * logs — a DPDP exposure arriving by accident rather than by decision.
 *
 * <p>The rule applied here: ids and figures out, names and contacts never.
 */
public final class AgentToolResponses {

    private AgentToolResponses() {
    }

    /** One bookable slot. Carries what the agent needs to choose, nothing more. */
    public record SlotOption(
        UUID slotId,
        String startTime,
        String endTime,
        Integer remainingCapacity
    ) {
    }

    /** Occupancy aggregated to counts. No bed-level patient detail. */
    public record BedOccupancy(
        int total,
        int occupied,
        int available,
        Map<String, Integer> byStatus
    ) {
    }

    /** Financial summary for one bill. patientName deliberately omitted. */
    public record LedgerEntry(
        UUID billId,
        String billNumber,
        String billDate,
        long billAmount,
        long dueAmount,
        String status
    ) {
    }

    public record BillingLedger(
        UUID patientId,
        long totalBilled,
        long totalDue,
        List<LedgerEntry> entries
    ) {
    }

    public record BookingResult(
        UUID appointmentId,
        UUID slotId,
        String appointmentDate,
        String status,
        boolean replayed
    ) {
    }
}
