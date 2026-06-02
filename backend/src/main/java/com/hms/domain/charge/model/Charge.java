package com.hms.domain.charge.model;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.*;

/**
 * A billable service charge — the price master.
 *
 * Versioning rule (C2.7): if bills use current tariffs and rate changes:
 *   - only name/category change → update in place
 *   - rate/type change → new Charge created, old soft-deleted (endDate=now)
 *
 * ChargeType: Charge=normal, Package=IP package, IP=inpatient
 * chargeType is inferred from category.chargeCategoryType via setChargeType()
 */
@Entity
@Table(name = "charges", indexes = {
    @Index(name = "idx_charge_name", columnList = "name"),
    @Index(name = "idx_charge_cat",  columnList = "category_id")
})
@Getter @Setter @NoArgsConstructor
public class Charge extends AuditableEntity {

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "category_id")
    private UUID categoryId;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "charge_type", nullable = false)
    private ChargeType chargeType = ChargeType.CHARGE;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    /** Include-packages: category IDs whose charges are absorbed by this IP package */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "charge_package_includes",
        joinColumns = @JoinColumn(name = "charge_id"))
    @Column(name = "category_id")
    private Set<UUID> includePackageCategories = new HashSet<>();

    /** Exclude-packages: category IDs exempt from IP package absorption */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "charge_package_excludes",
        joinColumns = @JoinColumn(name = "charge_id"))
    @Column(name = "category_id")
    private Set<UUID> excludePackageCategories = new HashSet<>();

    @Column(name = "quantitative", nullable = false)
    private Boolean quantitative = false;

    @OneToMany(mappedBy = "charge", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Tariff> tariffs = new ArrayList<>();

    @OneToMany(mappedBy = "packageId", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.EAGER)
    private Set<Packages> packageCharges = new HashSet<>();

    public void addTariff(Tariff t) { t.setCharge(this); tariffs.add(t); }

    public void addPackageCharge(Packages pc) {
        pc.setPackageId(this);
        this.packageCharges.add(pc);
    }

    public void removePackageCharge(Packages pc) {
        this.packageCharges.remove(pc);
        pc.setPackageId(null);
    }

    /** Soft-delete — preserve for billing history */
    public void retire(LocalDate date) {
        this.endDate = date;
        this.deactivate();
    }

    public boolean isRetired() { return endDate != null && endDate.isBefore(LocalDate.now()); }
}
