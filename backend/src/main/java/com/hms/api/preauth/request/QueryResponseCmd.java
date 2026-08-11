package com.hms.api.preauth.request;

import jakarta.validation.constraints.NotBlank;

/** Answer an insurer query — Screen 4.3. */
public record QueryResponseCmd(
    @NotBlank(message = "enter a response for the insurer") String responseText,
    /** Comma-separated ids from the existing attachments table. */
    String attachmentIds
) {}
