package com.hms.api.role.response;
import java.util.Set;
import java.util.UUID;
public record RoleResponse(UUID id, String name, String description, short status, Set<FeatureSummary> features) {
    public record FeatureSummary(UUID id, String featureKey, String description, String module) {}
}
