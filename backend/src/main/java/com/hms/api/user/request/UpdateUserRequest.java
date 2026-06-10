package com.hms.api.user.request;
import com.hms.domain.shared.model.EntityStatus;
import jakarta.validation.constraints.*;
import java.util.Set;
import java.util.UUID;
public record UpdateUserRequest(
    @Size(min=3,max=20) String firstName,
    @Size(min=1,max=20) String lastName,
    @Email String email,
    Set<UUID> roleIds,
    Set<UUID> departmentIds,
    Set<UUID> accountUnitIds,
    UUID consultantId,
    EntityStatus status,
    boolean showCasesheet,
    String speechLanguage,
    boolean textAutoSuggest,
    String salutation,
    String phoneNo
) {}
