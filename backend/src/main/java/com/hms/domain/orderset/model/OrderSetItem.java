package com.hms.domain.orderset.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * OrderSetItem — a single drug or test within an OrderSet.
 * itemType: PHARMACY (drug) | DIAGNOSTIC (test) | PROCEDURE
 */
@Entity
@Table(name = "order_set_items")
@Getter @Setter @NoArgsConstructor
public class OrderSetItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_set_id", nullable = false)
    @JsonBackReference
    private OrderSet orderSet;

    @Column(name = "item_type",               nullable = false, length = 30)  private String itemType = "PHARMACY";
    @Column(name = "service_catalog_item_id")                                  private UUID   serviceCatalogItemId;
    @Column(name = "item_name",               length = 200)                   private String itemName;      // display name
    @Column(name = "diagnostic_type",         length = 20)                    private String diagnosticType;
    @Column(name = "quantity",                nullable = false)                private int    quantity = 1;
    @Column(name = "instruction",             length = 200)                   private String instruction;   // drug-specific notes
    @Column(name = "frequency",               length = 50)                    private String frequency;
    @Column(name = "duration",                length = 50)                    private String duration;
    @Column(name = "route_label",             length = 50)                    private String routeLabel;
}
