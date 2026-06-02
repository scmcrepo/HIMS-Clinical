package com.hms.api.opip.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record AddOtherChargeRequest(
    @NotBlank String chargeLabel,          // description of the charge
    String    serviceCatalogItemId,        // optional FK to service catalog
    @NotNull @Positive BigDecimal amount,  // charge amount
    int       qty,                         // quantity (default 1)
    String    remarks
) {}
