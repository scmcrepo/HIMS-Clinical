package com.hms.api.compliance.response;

import com.hms.infrastructure.persistence.compliance.ErasureRequestEntity;
import com.hms.infrastructure.persistence.compliance.ErasureTargetEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response shapes for the rights surface.
 *
 * <p>Note what is absent: no patient name, no contact detail, nothing decrypted.
 * A queue of erasure requests is a list of people who asked to be forgotten, and
 * rendering their names into an admin screen would be its own small irony.
 * Operators work from the patient id and open the patient record separately,
 * where that access is audited.
 */
public final class RightsResponses {

    private RightsResponses() {}

    public record RightsRequest(
        UUID id,
        UUID patientId,
        String requestType,
        String state,
        Instant requestedAt,
        String requestedVia,
        boolean requestedByPatient,
        Instant requesterVerifiedAt,
        String verificationMethod,
        Instant dueAt,
        boolean overdue,
        Instant completedAt,
        String rejectionReason,
        String retainedReason
    ) {
        public static RightsRequest from(ErasureRequestEntity e) {
            boolean open = "RECEIVED".equals(e.getState()) || "IN_PROGRESS".equals(e.getState());
            return new RightsRequest(
                e.getId(), e.getPatientId(), e.getRequestType(), e.getState(),
                e.getRequestedAt(), e.getRequestedVia(), e.isRequestedByPatient(),
                e.getRequesterVerifiedAt(), e.getVerificationMethod(),
                e.getDueAt(),
                open && e.getDueAt() != null && e.getDueAt().isBefore(Instant.now()),
                e.getCompletedAt(), e.getRejectionReason(), e.getRetainedReason());
        }
    }

    /**
     * What happened in one store. This is the patient-facing evidence that the
     * erasure was real and the account of anything that was kept.
     */
    public record TargetOutcome(
        String store,
        String outcome,
        Integer rowsAffected,
        String detail,
        Instant processedAt
    ) {
        public static TargetOutcome from(ErasureTargetEntity t) {
            return new TargetOutcome(t.getTargetStore(), t.getOutcome(),
                                     t.getRowsAffected(), t.getDetail(), t.getProcessedAt());
        }
    }

    public record ErasureReceipt(
        RightsRequest request,
        List<TargetOutcome> targets,
        int erased,
        int anonymised,
        int retained,
        int failed
    ) {
        public static ErasureReceipt of(ErasureRequestEntity e, List<ErasureTargetEntity> targets) {
            return new ErasureReceipt(
                RightsRequest.from(e),
                targets.stream().map(TargetOutcome::from).toList(),
                (int) targets.stream().filter(t -> "ERASED".equals(t.getOutcome())).count(),
                (int) targets.stream().filter(t -> "ANONYMISED".equals(t.getOutcome())).count(),
                (int) targets.stream().filter(t -> "RETAINED".equals(t.getOutcome())).count(),
                (int) targets.stream().filter(t -> "FAILED".equals(t.getOutcome())).count());
        }
    }
}
