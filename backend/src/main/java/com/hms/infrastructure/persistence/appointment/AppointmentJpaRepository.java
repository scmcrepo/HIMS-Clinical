package com.hms.infrastructure.persistence.appointment;

import com.hms.domain.appointment.model.Appointment;
import com.hms.domain.appointment.model.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AppointmentJpaRepository extends JpaRepository<Appointment, UUID> {

    @Query("""
        SELECT a FROM Appointment a
        WHERE a.providerId = :pid
          AND a.appointmentDate = :date
        ORDER BY a.appointmentTime ASC
        """)
    List<Appointment> findByProviderAndDate(
        @Param("pid") UUID providerId,
        @Param("date") LocalDate date);

    @Query("""
        SELECT a FROM Appointment a
        WHERE a.appointmentDate = :date
        ORDER BY a.appointmentTime ASC
        """)
    List<Appointment> findByDate(@Param("date") LocalDate date);

    @Query("""
        SELECT a FROM Appointment a
        WHERE a.patientId = :pid
        ORDER BY a.appointmentDate DESC, a.appointmentTime DESC
        """)
    Page<Appointment> findByPatientId(@Param("pid") UUID patientId, Pageable pageable);

    @Query("""
        SELECT a FROM Appointment a
        WHERE a.providerId = :pid
          AND a.appointmentDate BETWEEN :from AND :to
        ORDER BY a.appointmentDate ASC, a.appointmentTime ASC
        """)
    List<Appointment> findByProviderAndDateRange(
        @Param("pid") UUID providerId,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to);

    @Query("""
        SELECT COUNT(a) FROM Appointment a
        WHERE a.slotId = :slotId
          AND a.appointmentDate = :date
          AND a.appointmentStatus != com.hms.domain.appointment.model.AppointmentStatus.CANCELLED
        """)
    long countBookedForSlotAndDate(
        @Param("slotId") UUID slotId,
        @Param("date") LocalDate date);

    @Query("SELECT a FROM Appointment a WHERE a.patientId = :pid ORDER BY a.appointmentDate DESC")
    List<com.hms.domain.appointment.model.Appointment> findByPatientIdOrderByDateDesc(@Param("pid") UUID patientId);
}
