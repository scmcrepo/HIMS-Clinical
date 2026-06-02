package com.hms.api.user.request;
import jakarta.validation.constraints.*;
import java.util.Set;
import java.util.UUID;
public record CreateUserRequest(
    @NotBlank @Size(min=3,max=14) String username,
    @NotBlank @Size(min=6)        String password,
    @NotBlank @Size(min=3,max=20) String firstName,
    @NotBlank @Size(min=1,max=20) String lastName,
    @Email String email,
    @NotNull @Size(min=1) Set<UUID> roleIds,
    Set<UUID> departmentIds,
    Set<UUID> accountUnitIds,
    UUID consultantId,
    boolean showCasesheet,
    String speechLanguage,
    String salutation,
    String phoneNo
) {}
