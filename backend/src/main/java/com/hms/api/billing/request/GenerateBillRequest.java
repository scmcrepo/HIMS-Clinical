package com.hms.api.billing.request;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
public record GenerateBillRequest(@NotNull LocalDate billDate, Instant dischargeAt) {}
