package com.hms.api.user.request;
import jakarta.validation.constraints.*;
public record ChangePasswordRequest(
    @NotBlank String currentPassword,
    @NotBlank @Size(min=6) String newPassword
) {}
