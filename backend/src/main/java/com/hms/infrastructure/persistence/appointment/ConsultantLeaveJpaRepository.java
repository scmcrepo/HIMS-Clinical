package com.hms.infrastructure.persistence.appointment;

import com.hms.domain.appointment.model.ConsultantLeave;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ConsultantLeaveJpaRepository extends JpaRepository<ConsultantLeave, UUID> {

    @Query("""
        SELECT cl FROM ConsultantLeave cl
        WHERE cl.consultantId = :consultantId
          AND cl.status = com.hms.domain.shared.model.EntityStatus.ACTIVE
          AND cl.startDate <= :date AND cl.endDate >= :date
        """)
    List<ConsultantLeave> findActiveByConsultantAndDate(
        @Param("consultantId") UUID consultantId,
        @Param("date") LocalDate date);

    @Query("""
        SELECT cl FROM ConsultantLeave cl
        WHERE cl.consultantId = :consultantId
          AND cl.status = com.hms.domain.shared.model.EntityStatus.ACTIVE
          AND cl.endDate >= :from AND cl.startDate <= :to
        """)
    List<ConsultantLeave> findActiveByConsultantAndDateRange(
        @Param("consultantId") UUID consultantId,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to);

    @Query("""
        SELECT cl FROM ConsultantLeave cl
        WHERE cl.consultantId = :consultantId
          AND cl.status = com.hms.domain.shared.model.EntityStatus.ACTIVE
        ORDER BY cl.startDate DESC
        """)
    List<ConsultantLeave> findActiveByConsultantOrderByStartDateDesc(
        @Param("consultantId") UUID consultantId);
}
