package com.hms.api.opip.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OtherChargeResponse(
    UUID       id,
    UUID       encounterId,
    String     chargeLabel,
    String     serviceCatalogItemId,
    BigDecimal amount,
    int        qty,
    String     remarks,
    Instant    createdAt
) {}
