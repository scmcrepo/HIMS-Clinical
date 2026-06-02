package com.hms.application.appointment;

import com.hms.api.appointment.request.CreateSlotRequest;
import com.hms.domain.appointment.model.AppointmentSlot;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.appointment.AppointmentSlotJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SlotManagementService {

    private final AppointmentSlotJpaRepository slotRepo;

    @Transactional
    public AppointmentSlot createSlot(CreateSlotRequest req) {
        if (req.fromTime().isAfter(req.toTime()) || req.fromTime().equals(req.toTime())) {
            throw new BusinessRuleViolationException("fromTime must be before toTime");
        }
        // 1. Check for exact match (including inactive)
        String concat = req.fromTime().toString() + req.toTime().toString();
        com.hms.domain.appointment.model.DayOfWeekEnum dow = com.hms.domain.appointment.model.DayOfWeekEnum.values()[req.dayOfWeek()];
        
        var exactMatch = slotRepo.findExisting(req.providerId(), dow, concat);
        if (exactMatch.isPresent()) {
            var slot = exactMatch.get();
            slot.setMaxPatients(req.maxPatients() > 0 ? req.maxPatients() : 10);
            slot.activate();
            return slotRepo.save(slot);
        }

        // 2. Check for overlapping slots on the same provider + day
        List<AppointmentSlot> existing = slotRepo.findActiveByProviderAndDay(req.providerId(), dow);

        AppointmentSlot newSlot = new AppointmentSlot();
        newSlot.setConsultantId(req.providerId());
        newSlot.setDayOfWeek(dow);
        newSlot.setFromTime(req.fromTime().toString());
        newSlot.setToTime(req.toTime().toString());
        newSlot.setMaxPatients(req.maxPatients() > 0 ? req.maxPatients() : 10);
        newSlot.buildConcatTime();

        boolean overlaps = existing.stream().anyMatch(s -> s.overlaps(newSlot));
        if (overlaps) {
            throw new BusinessRuleViolationException(
                "New slot overlaps with an existing active slot for this provider on the same day");
        }

        return slotRepo.save(newSlot);
    }

    @Transactional
    public void deleteSlot(UUID slotId) {
        AppointmentSlot slot = slotRepo.findById(slotId)
            .orElseThrow(() -> new ResourceNotFoundException("AppointmentSlot", slotId));
        slot.softDelete();
        slotRepo.save(slot);
    }
}
