package com.hms.infrastructure.mapper;

import com.hms.api.encounter.response.EncounterResponse;
import com.hms.api.encounter.response.EncounterSummaryResponse;
import com.hms.domain.billing.model.EncounterType;
import com.hms.domain.encounter.model.ClinicalEncounter;
import com.hms.domain.encounter.model.EncounterStatus;
import com.hms.domain.encounter.model.VisitMode;
import java.time.Instant;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-08-18T10:08:26+0530",
    comments = "version: 1.6.2, compiler: Eclipse JDT (IDE) 3.46.100.v20260624-0231, environment: Java 21.0.11 (Eclipse Adoptium)"
)
@Component
public class EncounterMapperImpl implements EncounterMapper {

    @Override
    public EncounterResponse toResponse(ClinicalEncounter encounter, String patientName, String patientNumber) {
        if ( encounter == null && patientName == null && patientNumber == null ) {
            return null;
        }

        EncounterStatus status = null;
        UUID id = null;
        UUID patientId = null;
        UUID primaryProviderId = null;
        UUID appointmentId = null;
        EncounterType encounterType = null;
        VisitMode visitMode = null;
        Instant startedAt = null;
        LocalTime checkedInAt = null;
        Instant dischargedAt = null;
        String diagnosis = null;
        boolean hasBed = false;
        boolean hasDraftBill = false;
        boolean cancelled = false;
        Instant casesheetRecordedAt = null;
        Map<String, Object> vitalData = null;
        Map<String, Object> consultantShareMap = null;
        if ( encounter != null ) {
            status = encounter.getEncounterStatus();
            id = encounter.getId();
            patientId = encounter.getPatientId();
            primaryProviderId = encounter.getPrimaryProviderId();
            appointmentId = encounter.getAppointmentId();
            encounterType = encounter.getEncounterType();
            visitMode = encounter.getVisitMode();
            startedAt = encounter.getStartedAt();
            checkedInAt = encounter.getCheckedInAt();
            dischargedAt = encounter.getDischargedAt();
            diagnosis = encounter.getDiagnosis();
            hasBed = encounter.isHasBed();
            hasDraftBill = encounter.isHasDraftBill();
            cancelled = encounter.isCancelled();
            casesheetRecordedAt = encounter.getCasesheetRecordedAt();
            Map<String, Object> map = encounter.getVitalData();
            if ( map != null ) {
                vitalData = new LinkedHashMap<String, Object>( map );
            }
            Map<String, Object> map1 = encounter.getConsultantShareMap();
            if ( map1 != null ) {
                consultantShareMap = new LinkedHashMap<String, Object>( map1 );
            }
        }
        String patientName1 = null;
        patientName1 = patientName;
        String patientNumber1 = null;
        patientNumber1 = patientNumber;

        String bedName = null;

        EncounterResponse encounterResponse = new EncounterResponse( id, patientId, patientNumber1, patientName1, primaryProviderId, appointmentId, encounterType, status, visitMode, startedAt, checkedInAt, dischargedAt, diagnosis, hasBed, hasDraftBill, cancelled, casesheetRecordedAt, vitalData, consultantShareMap, bedName );

        return encounterResponse;
    }

    @Override
    public EncounterSummaryResponse toSummaryResponse(ClinicalEncounter encounter, String patientName, String patientNumber, String patientMobileNumber, String patientGender, String patientAge) {
        if ( encounter == null && patientName == null && patientNumber == null && patientMobileNumber == null && patientGender == null && patientAge == null ) {
            return null;
        }

        EncounterStatus status = null;
        boolean hasBed = false;
        boolean hasDraftBill = false;
        UUID id = null;
        UUID patientId = null;
        UUID primaryProviderId = null;
        EncounterType encounterType = null;
        Instant startedAt = null;
        Instant dischargedAt = null;
        String diagnosis = null;
        Map<String, Object> consultantShareMap = null;
        if ( encounter != null ) {
            status = encounter.getEncounterStatus();
            hasBed = encounter.isHasBed();
            hasDraftBill = encounter.isHasDraftBill();
            id = encounter.getId();
            patientId = encounter.getPatientId();
            primaryProviderId = encounter.getPrimaryProviderId();
            encounterType = encounter.getEncounterType();
            startedAt = encounter.getStartedAt();
            dischargedAt = encounter.getDischargedAt();
            diagnosis = encounter.getDiagnosis();
            Map<String, Object> map = encounter.getConsultantShareMap();
            if ( map != null ) {
                consultantShareMap = new LinkedHashMap<String, Object>( map );
            }
        }
        String patientName1 = null;
        patientName1 = patientName;
        String patientNumber1 = null;
        patientNumber1 = patientNumber;
        String patientMobileNumber1 = null;
        patientMobileNumber1 = patientMobileNumber;
        String patientGender1 = null;
        patientGender1 = patientGender;
        String patientAge1 = null;
        patientAge1 = patientAge;

        String providerName = null;
        String bedName = null;

        EncounterSummaryResponse encounterSummaryResponse = new EncounterSummaryResponse( id, patientId, patientNumber1, patientName1, patientMobileNumber1, patientGender1, patientAge1, primaryProviderId, providerName, encounterType, status, startedAt, dischargedAt, diagnosis, hasBed, hasDraftBill, bedName, consultantShareMap );

        return encounterSummaryResponse;
    }
}
