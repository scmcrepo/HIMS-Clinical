package com.hms.domain.appointment.model;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Stores leave/unavailability ranges for a consultant (doctor).
 * During these dates, new appointments cannot be booked.
 */
@Entity
@Table(name = "consultant_leaves", indexes = {
    @Index(name = "idx_leave_consultant", columnList = "consultant_id"),
    @Index(name = "idx_leave_range", columnList = "consultant_id, start_date, end_date"),
    @Index(name = "idx_leave_status", columnList = "status")
})
@Getter @Setter @NoArgsConstructor
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@org.hibernate.annotations.Filter(name = "branchFilter", condition = "(branch_id IS NULL OR branch_id = :branchId)")
public class ConsultantLeave extends AuditableEntity {

    @Column(name = "consultant_id", nullable = false)
    private UUID consultantId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "reason", length = 255)
    private String reason;
}
