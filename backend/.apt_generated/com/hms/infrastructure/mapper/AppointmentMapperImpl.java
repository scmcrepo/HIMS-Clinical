package com.hms.infrastructure.mapper;

import com.hms.api.appointment.response.AppointmentResponse;
import com.hms.domain.appointment.model.Appointment;
import com.hms.domain.appointment.model.AppointmentStatus;
import com.hms.domain.encounter.model.VisitMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-08-18T14:39:28+0530",
    comments = "version: 1.6.2, compiler: Eclipse JDT (IDE) 3.46.100.v20260624-0231, environment: Java 21.0.11 (Eclipse Adoptium)"
)
@Component
public class AppointmentMapperImpl implements AppointmentMapper {

    @Override
    public AppointmentResponse toResponse(Appointment appointment, String patientName, String patientNumber, String patientPhone, String providerName, LocalTime appointmentEndTime, int bookedCount, int maxPatients) {
        if ( appointment == null && patientName == null && patientNumber == null && patientPhone == null && providerName == null && appointmentEndTime == null ) {
            return null;
        }

        AppointmentStatus status = null;
        UUID id = null;
        UUID patientId = null;
        UUID providerId = null;
        UUID slotId = null;
        LocalDate appointmentDate = null;
        LocalTime appointmentTime = null;
        VisitMode visitMode = null;
        String notes = null;
        String tempPatientName = null;
        String tempPatientSalutation = null;
        String tempPatientGender = null;
        String tempPatientPhone = null;
        Integer tempPatientAge = null;
        if ( appointment != null ) {
            status = appointment.getAppointmentStatus();
            id = appointment.getId();
            patientId = appointment.getPatientId();
            providerId = appointment.getProviderId();
            slotId = appointment.getSlotId();
            appointmentDate = appointment.getAppointmentDate();
            appointmentTime = appointment.getAppointmentTime();
            visitMode = appointment.getVisitMode();
            notes = appointment.getNotes();
            tempPatientName = appointment.getTempPatientName();
            tempPatientSalutation = appointment.getTempPatientSalutation();
            tempPatientGender = appointment.getTempPatientGender();
            tempPatientPhone = appointment.getTempPatientPhone();
            tempPatientAge = appointment.getTempPatientAge();
        }
        String patientName1 = null;
        patientName1 = patientName;
        String patientNumber1 = null;
        patientNumber1 = patientNumber;
        String patientPhone1 = null;
        patientPhone1 = patientPhone;
        String providerName1 = null;
        providerName1 = providerName;
        LocalTime appointmentEndTime1 = null;
        appointmentEndTime1 = appointmentEndTime;
        int bookedCount1 = 0;
        bookedCount1 = bookedCount;
        int maxPatients1 = 0;
        maxPatients1 = maxPatients;

        AppointmentResponse appointmentResponse = new AppointmentResponse( id, patientId, patientNumber1, patientName1, providerId, providerName1, slotId, status, appointmentDate, appointmentTime, visitMode, notes, tempPatientName, tempPatientSalutation, tempPatientGender, tempPatientPhone, tempPatientAge, patientPhone1, appointmentEndTime1, bookedCount1, maxPatients1 );

        return appointmentResponse;
    }
}
