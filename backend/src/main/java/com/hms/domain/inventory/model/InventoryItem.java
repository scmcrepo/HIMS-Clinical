package com.hms.domain.inventory.model;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "inventory_items")
@Getter
@Setter
@NoArgsConstructor
public class InventoryItem extends AuditableEntity {
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "unit_of_measure_id")
    private UUID unitOfMeasureId;

    @Column(name = "conversion_factor", nullable = false)
    private int conversionFactor = 1;

    @Column(name = "requires_batch", nullable = false)
    private boolean requiresBatch = false;

    @Column(name = "requires_prescription", nullable = false)
    private boolean requiresPrescription = false;

    @Column(name = "reorder_level", precision = 10, scale = 2)
    private BigDecimal reorderLevel = BigDecimal.ZERO;

    @Column(name = "hsn_code", length = 20)
    private String hsnCode;

    @Column(name = "tax_rate", precision = 5, scale = 2)
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "cims_id", length = 100)
    private String cimsId;

    @Column(name = "cims_name", length = 200)
    private String cimsName;

    @Column(name = "cims_type", length = 50)
    private String cimsType;

    @Column(name = "manufacturer", length = 200)
    private String manufacturer;

    @Column(name = "rack", length = 50)
    private String rack;

    @Column(name = "mrp", length = 50)
    private String mrp;

    @Column(name = "category_id")
    private java.util.UUID categoryId;

    @Column(name = "second_level_unit", length = 50)
    private String secondLevelUnit;

    @Column(name = "purchase_unit", length = 50)
    private String purchaseUnit;

    @Column(name = "selling_unit", length = 50)
    private String sellingUnit;

    @Column(name = "scheduled_drug", length = 50)
    private String scheduledDrug;
}
