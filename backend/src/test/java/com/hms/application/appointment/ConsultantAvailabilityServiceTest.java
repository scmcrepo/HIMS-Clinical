package com.hms.application.appointment;

import com.hms.api.appointment.request.CreateLeaveRequest;
import com.hms.api.appointment.request.CreateTimeBlockRequest;
import com.hms.domain.appointment.model.Appointment;
import com.hms.domain.appointment.model.AppointmentStatus;
import com.hms.domain.appointment.model.BlockType;
import com.hms.domain.appointment.model.ConsultantLeave;
import com.hms.domain.shared.model.EntityStatus;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.infrastructure.persistence.appointment.AppointmentJpaRepository;
import com.hms.infrastructure.persistence.appointment.ConsultantLeaveJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsultantAvailabilityServiceTest {

    @Mock private ConsultantLeaveJpaRepository leaveRepo;
    @Mock private AppointmentJpaRepository appointmentRepo;

    @InjectMocks private ConsultantAvailabilityService service;

    private static final UUID CONSULTANT = UUID.randomUUID();
    private static final LocalDate DAY = LocalDate.of(2026, 10, 12);

    private static ConsultantLeave fullDay(LocalDate from, LocalDate to) {
        ConsultantLeave leave = new ConsultantLeave();
        leave.setId(UUID.randomUUID());
        leave.setConsultantId(CONSULTANT);
        leave.setStartDate(from);
        leave.setEndDate(to);
        leave.setBlockType(BlockType.FULL_DAY);
        leave.setStatus(EntityStatus.ACTIVE);
        return leave;
    }

    private static ConsultantLeave timeBlock(LocalDate from, LocalDate to, LocalTime start, LocalTime end) {
        ConsultantLeave leave = fullDay(from, to);
        leave.setBlockType(BlockType.TIME_RANGE);
        leave.setStartTime(start);
        leave.setEndTime(end);
        return leave;
    }

    private static Appointment booked(LocalTime at) {
        Appointment appt = new Appointment();
        appt.setId(UUID.randomUUID());
        appt.setProviderId(CONSULTANT);
        appt.setAppointmentDate(DAY);
        appt.setAppointmentTime(at);
        appt.setAppointmentStatus(AppointmentStatus.BOOKED);
        return appt;
    }

    private static CreateTimeBlockRequest blockRequest(CreateTimeBlockRequest.TimeRange... ranges) {
        return new CreateTimeBlockRequest(CONSULTANT, DAY, DAY, "Theatre", List.of(ranges));
    }

    private static CreateTimeBlockRequest.TimeRange range(int fromHour, int toHour) {
        return new CreateTimeBlockRequest.TimeRange(LocalTime.of(fromHour, 0), LocalTime.of(toHour, 0));
    }

    @Test
    void createTimeBlocks_writesOneRowPerRange() {
        when(leaveRepo.findActiveByConsultantAndDateRange(CONSULTANT, DAY, DAY)).thenReturn(List.of());
        when(leaveRepo.save(any(ConsultantLeave.class))).thenAnswer(inv -> inv.getArgument(0));
        when(appointmentRepo.findByProviderAndDateRange(CONSULTANT, DAY, DAY)).thenReturn(List.of());

        var result = service.createTimeBlocks(blockRequest(range(9, 12), range(15, 16)), CONSULTANT);

        assertEquals(2, result.blocks().size());
        assertEquals(0, result.cancelledAppointments());
        assertTrue(result.blocks().stream().allMatch(b -> "TIME_RANGE".equals(b.blockType())));
        verify(leaveRepo, times(2)).save(any(ConsultantLeave.class));
    }

    @Test
    void createTimeBlocks_cancelsOnlyAppointmentsInsideTheWindow() {
        when(leaveRepo.findActiveByConsultantAndDateRange(CONSULTANT, DAY, DAY)).thenReturn(List.of());
        when(leaveRepo.save(any(ConsultantLeave.class))).thenAnswer(inv -> inv.getArgument(0));

        Appointment inside = booked(LocalTime.of(10, 30));
        Appointment onTheBoundary = booked(LocalTime.of(12, 0)); // end is exclusive
        Appointment afternoon = booked(LocalTime.of(16, 0));
        when(appointmentRepo.findByProviderAndDateRange(CONSULTANT, DAY, DAY))
            .thenReturn(List.of(inside, onTheBoundary, afternoon));

        var result = service.createTimeBlocks(blockRequest(range(9, 12)), CONSULTANT);

        assertEquals(1, result.cancelledAppointments());
        assertTrue(inside.isCancelled());
        assertFalse(onTheBoundary.isCancelled());
        assertFalse(afternoon.isCancelled());
        verify(appointmentRepo, times(1)).save(inside);
    }

    @Test
    void createTimeBlocks_rejectsRangeOverlappingAnExistingBlock() {
        when(leaveRepo.findActiveByConsultantAndDateRange(CONSULTANT, DAY, DAY))
            .thenReturn(List.of(timeBlock(DAY, DAY, LocalTime.of(11, 0), LocalTime.of(13, 0))));

        assertThrows(BusinessRuleViolationException.class,
            () -> service.createTimeBlocks(blockRequest(range(9, 12)), CONSULTANT));
        verify(leaveRepo, never()).save(any());
    }

    @Test
    void createTimeBlocks_allowsRangeAbuttingAnExistingBlock() {
        when(leaveRepo.findActiveByConsultantAndDateRange(CONSULTANT, DAY, DAY))
            .thenReturn(List.of(timeBlock(DAY, DAY, LocalTime.of(12, 0), LocalTime.of(13, 0))));
        when(leaveRepo.save(any(ConsultantLeave.class))).thenAnswer(inv -> inv.getArgument(0));
        when(appointmentRepo.findByProviderAndDateRange(CONSULTANT, DAY, DAY)).thenReturn(List.of());

        var result = service.createTimeBlocks(blockRequest(range(9, 12)), CONSULTANT);

        assertEquals(1, result.blocks().size());
    }

    @Test
    void createTimeBlocks_rejectsRangesThatOverlapEachOther() {
        assertThrows(BusinessRuleViolationException.class,
            () -> service.createTimeBlocks(blockRequest(range(9, 12), range(11, 14)), CONSULTANT));
        verify(leaveRepo, never()).save(any());
    }

    @Test
    void createTimeBlocks_rejectsInvertedRange() {
        assertThrows(BusinessRuleViolationException.class,
            () -> service.createTimeBlocks(blockRequest(range(14, 9)), CONSULTANT));
        verify(leaveRepo, never()).save(any());
    }

    @Test
    void createTimeBlocks_rejectedWhenConsultantAlreadyOnFullDayLeave() {
        when(leaveRepo.findActiveByConsultantAndDateRange(CONSULTANT, DAY, DAY))
            .thenReturn(List.of(fullDay(DAY, DAY)));

        assertThrows(BusinessRuleViolationException.class,
            () -> service.createTimeBlocks(blockRequest(range(9, 12)), CONSULTANT));
    }

    @Test
    void createFullDayLeave_rejectedByAnotherFullDayLeave() {
        when(leaveRepo.findActiveByConsultantAndDateRange(CONSULTANT, DAY, DAY))
            .thenReturn(List.of(fullDay(DAY, DAY)));

        assertThrows(BusinessRuleViolationException.class,
            () -> service.createFullDayLeave(new CreateLeaveRequest(DAY, DAY, "Vacation", CONSULTANT), CONSULTANT));
    }

    @Test
    void createFullDayLeave_supersedesAnExistingPartialBlock() {
        when(leaveRepo.findActiveByConsultantAndDateRange(CONSULTANT, DAY, DAY))
            .thenReturn(List.of(timeBlock(DAY, DAY, LocalTime.of(9, 0), LocalTime.of(12, 0))));
        when(leaveRepo.save(any(ConsultantLeave.class))).thenAnswer(inv -> inv.getArgument(0));

        Appointment afternoon = booked(LocalTime.of(16, 0));
        when(appointmentRepo.findByProviderAndDateRange(CONSULTANT, DAY, DAY)).thenReturn(List.of(afternoon));

        var result = service.createFullDayLeave(
            new CreateLeaveRequest(DAY, DAY, "Vacation", CONSULTANT), CONSULTANT);

        assertEquals("FULL_DAY", result.blocks().get(0).blockType());
        // A full-day leave takes the whole day, so the afternoon goes too.
        assertEquals(1, result.cancelledAppointments());
        assertTrue(afternoon.isCancelled());
    }

    @Test
    void createFullDayLeave_rejectsStartAfterEnd() {
        assertThrows(BusinessRuleViolationException.class,
            () -> service.createFullDayLeave(
                new CreateLeaveRequest(DAY.plusDays(2), DAY, "Vacation", CONSULTANT), CONSULTANT));
    }

    @Test
    void createFullDayLeave_rejectsPastDate() {
        assertThrows(BusinessRuleViolationException.class,
            () -> service.createFullDayLeave(
                new CreateLeaveRequest(LocalDate.now().minusDays(1), LocalDate.now().minusDays(1), "Vacation", CONSULTANT), CONSULTANT));
    }

    @Test
    void deleteBlock_refusesAConsultantSomeoneElsesBlock() {
        ConsultantLeave other = fullDay(DAY, DAY);
        other.setConsultantId(UUID.randomUUID());
        when(leaveRepo.findById(other.getId())).thenReturn(java.util.Optional.of(other));

        assertThrows(BusinessRuleViolationException.class,
            () -> service.deleteBlock(other.getId(), CONSULTANT));
        verify(leaveRepo, never()).save(any());
    }
}
