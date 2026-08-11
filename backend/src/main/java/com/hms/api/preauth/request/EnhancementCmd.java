package com.hms.api.preauth.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Ask for more cover than was approved — Screen 4.4.
 *
 * <p>The revised estimate is the new total, not the delta. The delta is derived
 * server-side from what is currently approved, so two screens cannot disagree
 * about which number the insurer was sent.
 */
public record EnhancementCmd(
    @NotNull @Positive Long revisedEstimatePaise,
    @NotBlank(message = "explain why more cover is needed") String justification
) {}
