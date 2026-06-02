package com.hms.domain.bed.model;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A category of rooms/beds (e.g. General Ward, Private Room, ICU).
 * Linked to a ServiceCatalogItem so bed charges can be auto-added to the bill.
 */
@Entity
@Table(name = "room_categories")
@Getter
@Setter
@NoArgsConstructor
public class RoomCategory extends AuditableEntity {

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "service_catalog_item_id")
    private UUID serviceCatalogItemId;
}
