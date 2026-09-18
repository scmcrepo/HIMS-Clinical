package com.hms.infrastructure.persistence.appointment;

import com.hms.domain.appointment.model.ConsultantLeave;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ConsultantLeaveJpaRepository extends JpaRepository<ConsultantLeave, UUID> {

    /**
     * Every active block covering a date, of either kind.
     *
     * <p>Callers deciding "is this doctor gone for the whole day?" want
     * {@link #findActiveFullDayByConsultantAndDate} instead — a partial block
     * matches here too, and treating it as a full day is exactly the bug
     * partial blocking exists to fix.
     */
    @Query("""
        SELECT cl FROM ConsultantLeave cl
        WHERE cl.consultantId = :consultantId
          AND cl.status = com.hms.domain.shared.model.EntityStatus.ACTIVE
          AND cl.startDate <= :date AND cl.endDate >= :date
        """)
    List<ConsultantLeave> findActiveByConsultantAndDate(
        @Param("consultantId") UUID consultantId,
        @Param("date") LocalDate date);

    /** Full-day leave covering a date — the "doctor is not in at all" check. */
    @Query("""
        SELECT cl FROM ConsultantLeave cl
        WHERE cl.consultantId = :consultantId
          AND cl.status = com.hms.domain.shared.model.EntityStatus.ACTIVE
          AND cl.blockType = com.hms.domain.appointment.model.BlockType.FULL_DAY
          AND cl.startDate <= :date AND cl.endDate >= :date
        """)
    List<ConsultantLeave> findActiveFullDayByConsultantAndDate(
        @Param("consultantId") UUID consultantId,
        @Param("date") LocalDate date);

    /** Partial-day blocks covering a date — the windows to subtract from the day's slots. */
    @Query("""
        SELECT cl FROM ConsultantLeave cl
        WHERE cl.consultantId = :consultantId
          AND cl.status = com.hms.domain.shared.model.EntityStatus.ACTIVE
          AND cl.blockType = com.hms.domain.appointment.model.BlockType.TIME_RANGE
          AND cl.startDate <= :date AND cl.endDate >= :date
        ORDER BY cl.startTime
        """)
    List<ConsultantLeave> findActiveTimeBlocksByConsultantAndDate(
        @Param("consultantId") UUID consultantId,
        @Param("date") LocalDate date);

    /** Every consultant away on one date — one query behind the whole day board. */
    @Query("""
        SELECT cl FROM ConsultantLeave cl
        WHERE cl.status = com.hms.domain.shared.model.EntityStatus.ACTIVE
          AND cl.startDate <= :date AND cl.endDate >= :date
        """)
    List<ConsultantLeave> findAllActiveOnDate(@Param("date") LocalDate date);

    /**
     * Every consultant fully away on one date.
     *
     * <p>The day board renders an "on leave" banner against a doctor's name and
     * hides their sessions; a doctor blocked 09:00–12:00 is still working, so
     * only full-day rows belong here.
     */
    @Query("""
        SELECT cl FROM ConsultantLeave cl
        WHERE cl.status = com.hms.domain.shared.model.EntityStatus.ACTIVE
          AND cl.blockType = com.hms.domain.appointment.model.BlockType.FULL_DAY
          AND cl.startDate <= :date AND cl.endDate >= :date
        """)
    List<ConsultantLeave> findAllActiveFullDayOnDate(@Param("date") LocalDate date);

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

    /**
     * Every active block of every consultant intersecting a date range, or of
     * one consultant when {@code consultantId} is supplied.
     *
     * <p>Powers the appointment calendar's background events: the month and
     * week grids need the whole clinic's blocks in one request, and the
     * consultant filter narrows the same query rather than taking a different
     * path.
     */
    @Query("""
        SELECT cl FROM ConsultantLeave cl
        WHERE cl.status = com.hms.domain.shared.model.EntityStatus.ACTIVE
          AND (:consultantId IS NULL OR cl.consultantId = :consultantId)
          AND cl.endDate >= :from AND cl.startDate <= :to
        ORDER BY cl.startDate, cl.startTime
        """)
    List<ConsultantLeave> findActiveInDateRange(
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
