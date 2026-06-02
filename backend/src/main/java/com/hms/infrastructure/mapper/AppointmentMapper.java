package com.hms.infrastructure.mapper;

import com.hms.api.appointment.response.AppointmentResponse;
import com.hms.domain.appointment.model.Appointment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import java.time.LocalTime;

@Mapper(componentModel = "spring")
public interface AppointmentMapper {

    @Mapping(target = "status", source = "appointment.appointmentStatus")
    @Mapping(target = "bookedCount", source = "bookedCount")
    @Mapping(target = "maxPatients", source = "maxPatients")
    @Mapping(target = "patientName", source = "patientName")
    @Mapping(target = "patientNumber", source = "patientNumber")
    @Mapping(target = "providerName", source = "providerName")
    @Mapping(target = "patientPhone", source = "patientPhone")
    @Mapping(target = "appointmentEndTime", source = "appointmentEndTime")
    AppointmentResponse toResponse(Appointment appointment, String patientName, String patientNumber, String patientPhone, String providerName, LocalTime appointmentEndTime, int bookedCount, int maxPatients);
}
