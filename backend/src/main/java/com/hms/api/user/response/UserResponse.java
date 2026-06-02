package com.hms.api.user.response;
import com.hms.domain.shared.model.EntityStatus;
import java.util.Set;
import java.util.UUID;
public record UserResponse(
    UUID id,
    String username,
    String firstName,
    String lastName,
    String fullName,
    String email,
    EntityStatus status,
    boolean accountLocked,
    Set<RoleSummary> roles,
    Set<UUID> departmentIds,
    Set<UUID> accountUnitIds,
    UUID consultantId,
    boolean showCasesheet,
    String speechLanguage,
    boolean textAutoSuggest,
    String salutation,
    String phoneNo
) {
    public record RoleSummary(UUID id, String name) {}
}
