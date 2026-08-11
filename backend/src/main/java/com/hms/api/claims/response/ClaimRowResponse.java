package com.hms.api.claims.response;

import com.hms.infrastructure.persistence.nhcx.NhcxTransactionEntity;

import java.util.List;
import java.util.UUID;

/**
 * One row of the claims control tower — Screen 5.2.
 *
 * <p>Amounts stay in paise; the frontend formats. Advices are embedded because
 * the metric cards need reconciled bank figures, and a per-row fetch would mean
 * one request per claim to render a single table.
 */
public record ClaimRowResponse(
    UUID id,
    String correlationId,
    String payerCode,
    String exchangeType,
    String state,
    String financialState,
    Long claimedAmount,
    Long approvedAmount,
    Long disallowedAmount,
    Long patientCopayAmount,
    List<PaymentAdviceResponse> advices
) {

    public static ClaimRowResponse from(NhcxTransactionEntity t,
                                        List<PaymentAdviceResponse> advices) {
        return new ClaimRowResponse(
            t.getId(), t.getCorrelationId(), t.getPayerCode(), t.getExchangeType(),
            t.getState(), t.getFinancialState(), t.getClaimedAmount(), t.getApprovedAmount(),
            t.getDisallowedAmount(), t.getPatientCopayAmount(),
            advices == null ? List.of() : advices);
    }
}
