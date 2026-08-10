package com.hms.api.claims.response;

import com.hms.application.claims.ClaimSettlementCalculator;
import com.hms.infrastructure.persistence.payment.ClaimPaymentAdviceEntity;

import java.time.Instant;
import java.util.UUID;

/**
 * An insurer payment advice for Screen 5.3.
 *
 * <p>The UTR is shown in full deliberately. It is a bank reference rather than
 * personal data, and it is the value an accountant matches against a statement
 * line — masking it would make the screen useless for its one job.
 *
 * <p>Amounts in paise; the frontend formats.
 */
public record PaymentAdviceResponse(
    UUID id,
    String utrNumber,
    Instant paymentDate,
    Long grossAmount,
    Long tdsAmount,
    Long deductionAmount,
    Long netDisbursedAmount,
    boolean reconciled,
    Long bankCreditedAmount,
    Long reconciliationGap,
    String reconciliationNote
) {

    public static PaymentAdviceResponse from(ClaimPaymentAdviceEntity e) {
        Long gap = e.getBankCreditedAmount() == null
            ? null
            : ClaimSettlementCalculator.reconciliationGap(
                  e.getNetDisbursedAmount(), e.getBankCreditedAmount());

        return new PaymentAdviceResponse(
            e.getId(), e.getUtrNumber(), e.getPaymentDate(),
            e.getGrossAmount(), e.getTdsAmount(), e.getDeductionAmount(),
            e.getNetDisbursedAmount(), e.isReconciled(), e.getBankCreditedAmount(),
            gap, e.getReconciliationNote());
    }
}
