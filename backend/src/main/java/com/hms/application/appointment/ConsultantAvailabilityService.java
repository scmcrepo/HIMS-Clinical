package com.hms.application.appointment;

import com.hms.api.appointment.request.CreateLeaveRequest;
import com.hms.api.appointment.request.CreateTimeBlockRequest;
import com.hms.api.appointment.response.LeaveResponse;
import com.hms.domain.appointment.model.Appointment;
import com.hms.domain.appointment.model.BlockType;
import com.hms.domain.appointment.model.ConsultantLeave;
import com.hms.domain.shared.model.EntityStatus;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.appointment.AppointmentJpaRepository;
import com.hms.infrastructure.persistence.appointment.ConsultantLeaveJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Creating and removing the windows in which a consultant cannot be booked —
 * whole days ({@link BlockType#FULL_DAY}) and parts of days
 * ({@link BlockType#TIME_RANGE}).
 *
 * <p>Reading availability stays in {@link AppointmentSchedulingService}, which
 * owns slots and bookings; this service only writes the blocks and cancels
 * whatever they displace.
 */
@Service
@RequiredArgsConstructor
public class ConsultantAvailabilityService {

    private final ConsultantLeaveJpaRepository leaveRepo;
    private final AppointmentJpaRepository appointmentRepo;

    /** What a block request did: the rows written and the bookings it displaced. */
    public record BlockResult(List<LeaveResponse> blocks, int cancelledAppointments) {}

    @Transactional
    public BlockResult createFullDayLeave(CreateLeaveRequest req, UUID consultantId) {
        validateDates(req.startDate(), req.endDate());

        // A full-day leave collides only with another full-day leave. It is
        // allowed to swallow an existing partial block on the same dates: a
        // doctor who blocked their Tuesday morning and then decides to take the
        // whole of Tuesday off should not have to unblock the morning first.
        List<ConsultantLeave> existing =
            leaveRepo.findActiveByConsultantAndDateRange(consultantId, req.startDate(), req.endDate());
        existing.stream()
            .filter(ConsultantLeave::isFullDay)
            .findFirst()
            .ifPresent(clash -> {
                throw new BusinessRuleViolationException(
                    "This leave range overlaps with an existing leave from "
                        + clash.getStartDate() + " to " + clash.getEndDate());
            });

        // Auto-remove any partial (TIME_RANGE) blocks that fall within this
        // full-day range — they are now redundant since the entire day is off.
        existing.stream()
            .filter(block -> !block.isFullDay())
            .forEach(block -> {
                block.softDelete();
                leaveRepo.save(block);
            });

        ConsultantLeave leave = newBlock(consultantId, req.startDate(), req.endDate(), req.reason());
        leave.setBlockType(BlockType.FULL_DAY);
        ConsultantLeave saved = leaveRepo.save(leave);

        int cancelled = cancelDisplacedAppointments(
            consultantId, req.startDate(), req.endDate(), null, null,
            "Cancelled due to doctor leave from " + req.startDate() + " to " + req.endDate());

        return new BlockResult(List.of(LeaveResponse.from(saved)), cancelled);
    }

    @Transactional
    public BlockResult createTimeBlocks(CreateTimeBlockRequest req, UUID consultantId) {
        validateDates(req.startDate(), req.endDate());

        List<CreateTimeBlockRequest.TimeRange> ranges = req.timeRanges().stream()
            .sorted(Comparator.comparing(CreateTimeBlockRequest.TimeRange::startTime))
            .toList();

        for (CreateTimeBlockRequest.TimeRange range : ranges) {
            if (!range.startTime().isBefore(range.endTime())) {
                throw new BusinessRuleViolationException(
                    "Block end time must be after start time (" + range.startTime()
                        + " – " + range.endTime() + ")");
            }
        }

        // Ranges within one request must not overlap each other. Sorted above,
        // so it is enough to compare each with its predecessor.
        for (int i = 1; i < ranges.size(); i++) {
            if (ranges.get(i - 1).overlaps(ranges.get(i))) {
                throw new BusinessRuleViolationException(
                    "Time ranges overlap each other: " + ranges.get(i - 1).startTime()
                        + " – " + ranges.get(i - 1).endTime() + " and "
                        + ranges.get(i).startTime() + " – " + ranges.get(i).endTime());
            }
        }

        List<ConsultantLeave> existing =
            leaveRepo.findActiveByConsultantAndDateRange(consultantId, req.startDate(), req.endDate());

        existing.stream()
            .filter(ConsultantLeave::isFullDay)
            .findFirst()
            .ifPresent(clash -> {
                throw new BusinessRuleViolationException(
                    "Consultant is already on full-day leave from " + clash.getStartDate()
                        + " to " + clash.getEndDate() + ". Remove that leave before blocking hours.");
            });

        for (CreateTimeBlockRequest.TimeRange range : ranges) {
            existing.stream()
                .filter(block -> !block.isFullDay())
                .filter(block -> block.blocks(range.startTime(), range.endTime()))
                .findFirst()
                .ifPresent(clash -> {
                    throw new BusinessRuleViolationException(
                        "A block already covers " + clash.getStartTime() + " – " + clash.getEndTime()
                            + " on " + clash.getStartDate() + " to " + clash.getEndDate());
                });
        }

        List<LeaveResponse> saved = new ArrayList<>();
        int cancelled = 0;
        for (CreateTimeBlockRequest.TimeRange range : ranges) {
            ConsultantLeave block = newBlock(consultantId, req.startDate(), req.endDate(), req.reason());
            block.setBlockType(BlockType.TIME_RANGE);
            block.setStartTime(range.startTime());
            block.setEndTime(range.endTime());
            saved.add(LeaveResponse.from(leaveRepo.save(block)));

            cancelled += cancelDisplacedAppointments(
                consultantId, req.startDate(), req.endDate(), range.startTime(), range.endTime(),
                "Cancelled: consultant unavailable " + range.startTime() + " – " + range.endTime()
                    + " from " + req.startDate() + " to " + req.endDate());
        }

        return new BlockResult(saved, cancelled);
    }

    /**
     * Appointments a prospective block would cancel, without writing anything.
     *
     * <p>The time-block modal shows this count before the user commits, so the
     * preview and the cancellation must agree — both go through
     * {@link #displacedAppointments}.
     */
    @Transactional(readOnly = true)
    public int previewDisplacedAppointments(UUID consultantId, LocalDate from, LocalDate to,
                                            LocalTime blockStart, LocalTime blockEnd) {
        return displacedAppointments(consultantId, from, to, blockStart, blockEnd).size();
    }

    @Transactional
    public void deleteBlock(UUID leaveId, UUID callerConsultantId) {
        ConsultantLeave leave = leaveRepo.findById(leaveId)
            .orElseThrow(() -> new ResourceNotFoundException("ConsultantLeave", leaveId));

        if (callerConsultantId != null && !leave.getConsultantId().equals(callerConsultantId)) {
            throw new BusinessRuleViolationException("Leave record does not belong to this consultant");
        }

        leave.softDelete();
        leaveRepo.save(leave);
    }

    // ── internals ─────────────────────────────────────────────────────────

    private void validateDates(LocalDate start, LocalDate end) {
        if (start.isAfter(end)) {
            throw new BusinessRuleViolationException("Start date cannot be after end date");
        }
    }

    private ConsultantLeave newBlock(UUID consultantId, LocalDate start, LocalDate end, String reason) {
        ConsultantLeave leave = new ConsultantLeave();
        leave.setConsultantId(consultantId);
        leave.setStartDate(start);
        leave.setEndDate(end);
        leave.setReason(reason);
        leave.setStatus(EntityStatus.ACTIVE);
        return leave;
    }

    private int cancelDisplacedAppointments(UUID consultantId, LocalDate from, LocalDate to,
                                            LocalTime blockStart, LocalTime blockEnd, String note) {
        List<Appointment> displaced = displacedAppointments(consultantId, from, to, blockStart, blockEnd);
        for (Appointment appt : displaced) {
            appt.cancel();
            appt.setNotes((appt.getNotes() != null ? appt.getNotes() + "\n" : "") + note);
            appointmentRepo.save(appt);
        }
        return displaced.size();
    }

    /**
     * The booked appointments a block takes out.
     *
     * <p>A null {@code blockStart} means the whole day, which is what a
     * full-day leave passes. For a time range the appointment's start time
     * decides: an appointment beginning inside the window goes, one beginning
     * at or after {@code blockEnd} stays. Appointment rows carry no duration of
     * their own — the end time is a property of the slot — so start time is the
     * only thing that can be compared without loading every slot.
     */
    private List<Appointment> displacedAppointments(UUID consultantId, LocalDate from, LocalDate to,
                                                    LocalTime blockStart, LocalTime blockEnd) {
        return appointmentRepo.findByProviderAndDateRange(consultantId, from, to).stream()
            .filter(Appointment::isBooked)
            .filter(appt -> blockStart == null
                || (appt.getAppointmentTime() != null
                    && !appt.getAppointmentTime().isBefore(blockStart)
                    && appt.getAppointmentTime().isBefore(blockEnd)))
            .toList();
    }
}
