package com.hms.api.insurance.response;

import java.time.LocalDate;
import java.util.UUID;

/** One cheque or remittance received against a claim. Amount in paise. */
public record InsuranceChequeResponse(
    UUID id,
    String chequeNo,
    LocalDate chequeDate,
    String drawnOn,
    String payableAt,
    Long amount,
    String authorisedBy
) {}
