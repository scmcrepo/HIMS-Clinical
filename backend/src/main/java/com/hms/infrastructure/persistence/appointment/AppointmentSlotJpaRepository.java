package com.hms.infrastructure.persistence.appointment;

import com.hms.domain.appointment.model.AppointmentSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AppointmentSlotJpaRepository extends JpaRepository<AppointmentSlot, UUID> {

    @Query("""
        SELECT s FROM AppointmentSlot s
        WHERE s.consultantId = :pid AND s.status = com.hms.domain.shared.model.EntityStatus.ACTIVE
        ORDER BY s.dayOfWeek ASC, s.fromTime ASC
        """)
    List<AppointmentSlot> findActiveByProviderId(@Param("pid") UUID providerId);

    @Query("""
        SELECT s FROM AppointmentSlot s
        WHERE s.consultantId = :pid
          AND s.dayOfWeek = :dow
          AND s.status = com.hms.domain.shared.model.EntityStatus.ACTIVE
        ORDER BY s.fromTime ASC
        """)
    List<AppointmentSlot> findActiveByProviderAndDay(
        @Param("pid") UUID providerId,
        @Param("dow") com.hms.domain.appointment.model.DayOfWeekEnum dayOfWeek);

    @Query("SELECT s FROM AppointmentSlot s WHERE s.consultantId = :pid AND s.dayOfWeek = :dow AND s.concatTime = :concat")
    java.util.Optional<AppointmentSlot> findExisting(@Param("pid") UUID consultantId, @Param("dow") com.hms.domain.appointment.model.DayOfWeekEnum dayOfWeek, @Param("concat") String concat);

    @Query("SELECT s FROM AppointmentSlot s WHERE s.consultantId = :pid AND s.status = com.hms.domain.shared.model.EntityStatus.ACTIVE")
    List<AppointmentSlot> findByConsultant(@Param("pid") UUID consultantId);

    @Query("SELECT s FROM AppointmentSlot s WHERE s.consultantId = :pid AND s.dayOfWeek = :dow AND s.status = com.hms.domain.shared.model.EntityStatus.ACTIVE")
    List<AppointmentSlot> findByConsultantAndDay(@Param("pid") UUID consultantId, @Param("dow") com.hms.domain.appointment.model.DayOfWeekEnum dayOfWeek);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM Appointment a WHERE a.slotId = :slotId")
    boolean hasAppointments(@Param("slotId") UUID slotId);

    @Query("""
        SELECT s FROM AppointmentSlot s
        WHERE s.consultantId = :pid
          AND s.specificDate = :date
          AND s.status = com.hms.domain.shared.model.EntityStatus.ACTIVE
        ORDER BY s.fromTime ASC
        """)
    List<AppointmentSlot> findSpecificDateSlots(
        @Param("pid") UUID providerId,
        @Param("date") java.time.LocalDate date);

    @Query("""
        SELECT s FROM AppointmentSlot s
        WHERE s.consultantId = :pid
          AND s.specificDate IS NOT NULL
          AND s.status = com.hms.domain.shared.model.EntityStatus.ACTIVE
        ORDER BY s.specificDate ASC, s.fromTime ASC
        """)
    List<AppointmentSlot> findAllDateSpecificSlots(@Param("pid") UUID providerId);

    @Query("""
        SELECT s FROM AppointmentSlot s
        WHERE s.consultantId = :pid
          AND s.dayOfWeek = :dow
          AND s.specificDate IS NULL
          AND (s.effectiveFrom IS NULL OR s.effectiveFrom <= :date)
          AND (s.effectiveTo IS NULL OR s.effectiveTo >= :date)
          AND s.status = com.hms.domain.shared.model.EntityStatus.ACTIVE
        ORDER BY s.fromTime ASC
        """)
    List<AppointmentSlot> findActiveRecurringSlots(
        @Param("pid") UUID providerId,
        @Param("dow") com.hms.domain.appointment.model.DayOfWeekEnum dayOfWeek,
        @Param("date") java.time.LocalDate date);

    /**
     * Every consultant's date-specific slots for one date.
     *
     * The day board needs the whole clinic in one round trip. Asking per consultant works
     * for a two-doctor practice and collapses at fifty, and the per-consultant queries this
     * mirrors ({@code findSpecificDateSlots}, {@code findActiveRecurringSlots}) stay in
     * place for the single-doctor screens.
     */
    @Query("""
        SELECT s FROM AppointmentSlot s
        WHERE s.specificDate = :date
          AND s.status = com.hms.domain.shared.model.EntityStatus.ACTIVE
        ORDER BY s.consultantId ASC, s.fromTime ASC
        """)
    List<AppointmentSlot> findAllSpecificDateSlots(@Param("date") java.time.LocalDate date);

    /** Every consultant's recurring slots that are in force on one date. */
    @Query("""
        SELECT s FROM AppointmentSlot s
        WHERE s.dayOfWeek = :dow
          AND s.specificDate IS NULL
          AND (s.effectiveFrom IS NULL OR s.effectiveFrom <= :date)
          AND (s.effectiveTo IS NULL OR s.effectiveTo >= :date)
          AND s.status = com.hms.domain.shared.model.EntityStatus.ACTIVE
        ORDER BY s.consultantId ASC, s.fromTime ASC
        """)
    List<AppointmentSlot> findAllActiveRecurringSlots(
        @Param("dow") com.hms.domain.appointment.model.DayOfWeekEnum dayOfWeek,
        @Param("date") java.time.LocalDate date);

    @Query("SELECT s FROM AppointmentSlot s WHERE s.consultantId = :pid AND s.specificDate = :date AND s.concatTime = :concat")
    java.util.Optional<AppointmentSlot> findExistingDateSlot(
        @Param("pid") UUID consultantId,
        @Param("date") java.time.LocalDate date,
        @Param("concat") String concat);
}
