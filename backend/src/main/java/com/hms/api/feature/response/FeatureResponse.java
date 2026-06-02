package com.hms.api.feature.response;
import java.util.UUID;
public record FeatureResponse(UUID id, String featureKey, String description, String module) {}
