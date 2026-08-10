package com.hms.infrastructure.persistence.policy;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** One exclusion, restriction or sub-limit attached to a coverage snapshot. */
@Entity
@Table(name = "policy_benefit_exclusions")
@Getter
@Setter
public class PolicyExclusionEntity extends AuditableEntity {

    @Column(name = "coverage_id", nullable = false)
    private UUID coverageId;

    /** EXCLUSION | RESTRICTION | SUB_LIMIT */
    @Column(name = "kind", nullable = false, length = 20)
    private String kind = "EXCLUSION";

    @Column(name = "code", length = 60)
    private String code;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    /** Present for SUB_LIMIT; a capped benefit rather than an excluded one. */
    @Column(name = "limit_paise")
    private Long limitPaise;
}
