package com.hms.infrastructure.mapper;

import com.hms.api.encounter.response.EncounterResponse;
import com.hms.api.encounter.response.EncounterSummaryResponse;
import com.hms.domain.encounter.model.ClinicalEncounter;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface EncounterMapper {
    @Mapping(target = "status", source = "encounter.encounterStatus")
    @Mapping(target = "patientName", source = "patientName")
    @Mapping(target = "patientNumber", source = "patientNumber")
    EncounterResponse toResponse(ClinicalEncounter encounter, String patientName, String patientNumber);

    @Mapping(target = "status",       source = "encounter.encounterStatus")
    @Mapping(target = "hasBed",       source = "encounter.hasBed")
    @Mapping(target = "hasDraftBill", source = "encounter.hasDraftBill")
    @Mapping(target = "patientName",  source = "patientName")
    @Mapping(target = "patientNumber", source = "patientNumber")
    @Mapping(target = "patientMobileNumber", source = "patientMobileNumber")
    @Mapping(target = "patientGender", source = "patientGender")
    @Mapping(target = "patientAge", source = "patientAge")
    @Mapping(target = "providerName", ignore = true)
    @Mapping(target = "bedName", ignore = true)
    EncounterSummaryResponse toSummaryResponse(ClinicalEncounter encounter, String patientName, String patientNumber, String patientMobileNumber, String patientGender, String patientAge);
}
