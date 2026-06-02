package com.hms.domain.shared.model;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
public enum EntityStatus {
    INACTIVE(0), ACTIVE(1), DELETED(2);
    private final int ordinalValue;
    EntityStatus(int v) { this.ordinalValue = v; }

    /** Serialize as plain string: "ACTIVE", "INACTIVE", "DELETED" */
    @JsonValue
    public String toJson() { return name(); }

    /** Deserialize from string name */
    @JsonCreator
    public static EntityStatus fromJson(String value) {
        if (value == null) return ACTIVE;
        for (EntityStatus s : values()) if (s.name().equalsIgnoreCase(value)) return s;
        // Fallback: try integer ordinal from legacy payloads
        try { return fromOrdinal(Integer.parseInt(value)); } catch (NumberFormatException ignored) {}
        return ACTIVE;
    }

    @JsonIgnore
    public int getOrdinalValue() { return ordinalValue; }

    public static EntityStatus fromOrdinal(int o) {
        for (EntityStatus s : values()) if (s.ordinalValue == o) return s;
        throw new IllegalArgumentException("Unknown EntityStatus ordinal: " + o);
    }

    @JsonIgnore
    public boolean isActive()  { return this == ACTIVE;  }

    @JsonIgnore
    public boolean isDeleted() { return this == DELETED; }
}
