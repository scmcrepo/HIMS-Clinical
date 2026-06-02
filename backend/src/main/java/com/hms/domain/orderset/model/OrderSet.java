package com.hms.domain.orderset.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * OrderSet — pre-configured groups of drugs or diagnostic tests for one-click batch ordering.
 * Types: PRESCRIPTION (drugs) | DIAGNOSTICS (tests) | BOTH
 * Scope: GLOBAL | DEPARTMENT | CONSULTANT — controlled by consultantId/departmentId
 */
@Entity
@Table(name = "order_sets")
@Getter @Setter @NoArgsConstructor
public class OrderSet extends AuditableEntity {

    @Column(name = "name",        nullable = false, length = 150) private String name;
    @Column(name = "description", length = 500)                   private String description;
    @Column(name = "set_type",    length = 30)                    private String setType = "BOTH"; // PRESCRIPTION | DIAGNOSTICS | BOTH
    @Column(name = "is_outpatient")                               private Boolean isOutpatient = true;
    @Column(name = "is_favorite")                                 private Boolean isFavorite   = false;
    @Column(name = "consultant_id")                               private UUID    consultantId;
    @Column(name = "department_id")                               private UUID    departmentId;
    // userRights: GLOBAL | DEPARTMENT | CONSULTANT
    @Column(name = "scope", length = 20) private String scope = "GLOBAL";

    @OneToMany(mappedBy = "orderSet", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonManagedReference
    private List<OrderSetItem> items = new ArrayList<>();
}
