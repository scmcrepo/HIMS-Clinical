package com.hms.api.insurance.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Stage 7 — settlement: what the TPA paid, and what it refused to pay (WO-020).
 *
 * <p>Two different things arrive together because the desk keys them from one
 * remittance advice, but they land in two different places: cheques become rows
 * in {@code insurance_cheque_receipts}, and deductions are written to
 * {@code charge_line_items.disallowed_amount} through
 * {@code BillingOperationsService} — the component that already owns bill money.
 * A second write path to a bill is how two totals begin to disagree.
 */
public record DisallowanceStageRequest(

    @Valid List<ChequeReceiptItem> cheques,

    @Valid List<DisallowanceLine> disallowances
) {

    /** One cheque or electronic remittance from the TPA. */
    public record ChequeReceiptItem(

        /** Null for a new receipt; set when editing one already recorded. */
        UUID id,

        /** Cheque number or NEFT/RTGS UTR. */
        @NotBlank @Size(max = 100) String chequeNo,

        LocalDate chequeDate,

        /** Issuing bank. */
        @Size(max = 150) String drawnOn,

        /** Issuing branch. */
        @Size(max = 150) String payableAt,

        /** Net disbursed, in paise. Positive — a refund to the insurer is not this. */
        @NotNull @Positive Long amount,

        @Size(max = 150) String authorisedBy
    ) {}

    /** One charge line the TPA refused to pay, in full or in part. */
    public record DisallowanceLine(

        /** The charge_line_items row being deducted against. */
        @NotNull UUID chargeLineItemId,

        /**
         * Amount disallowed on this line, in paise. Zero is meaningful — it
         * clears a deduction keyed in error.
         */
        @NotNull @PositiveOrZero Long disallowedAmount
    ) {}
}
