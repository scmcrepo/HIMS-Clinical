package com.hms.infrastructure.mapper;
import com.hms.api.patient.request.*;
import com.hms.api.patient.response.PatientResponse;
import com.hms.domain.patient.model.Patient;
import org.mapstruct.*;
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface PatientMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "modifiedBy", ignore = true)
    @Mapping(target = "modifiedAt", ignore = true)
    Patient fromRegisterRequest(RegisterPatientRequest req);
    @Mapping(target = "age", expression = "java(patient.computeAge())")
    @Mapping(target = "fullName", expression = "java(patient.computeFullName())")
    @Mapping(target = "patientNumber", source = "patientNumber")
    @Mapping(target = "isInpatient", ignore = true)
    @Mapping(target = "activeEncounterId", ignore = true)
    PatientResponse toResponse(Patient patient, String patientNumber);
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void applyUpdateRequest(UpdatePatientRequest req, @MappingTarget Patient patient);
}
