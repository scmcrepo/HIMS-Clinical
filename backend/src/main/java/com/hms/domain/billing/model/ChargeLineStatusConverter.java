package com.hms.domain.billing.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Maps ChargeLineStatus enum to legacy ServiceStatus integer values.
 *
 * Legacy DB values:
 *   NULL = active (no ChargeLineStatus set on the entity)
 *   1    = CANCELLED
 *   2    = MODIFIED
 *   3    = REFUNDED
 *
 * This converter ensures ChargeLineItem.status=null remains null in DB
 * and ChargeLineStatus enum values map to the correct legacy integers.
 */
@Converter(autoApply = false)
public class ChargeLineStatusConverter implements AttributeConverter<ChargeLineStatus, Integer> {

    @Override
    public Integer convertToDatabaseColumn(ChargeLineStatus attribute) {
        if (attribute == null) return null;
        return switch (attribute) {
            case CANCELLED -> 1;
            case MODIFIED  -> 2;
            case REFUNDED  -> 3;
        };
    }

    @Override
    public ChargeLineStatus convertToEntityAttribute(Integer dbData) {
        if (dbData == null) return null;
        return switch (dbData) {
            case 1  -> ChargeLineStatus.CANCELLED;
            case 2  -> ChargeLineStatus.MODIFIED;
            case 3  -> ChargeLineStatus.REFUNDED;
            default -> null;
        };
    }
}
