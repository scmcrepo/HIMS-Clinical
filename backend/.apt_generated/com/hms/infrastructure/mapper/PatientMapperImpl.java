package com.hms.infrastructure.mapper;

import com.hms.api.patient.request.RegisterPatientRequest;
import com.hms.api.patient.request.UpdatePatientRequest;
import com.hms.api.patient.response.PatientResponse;
import com.hms.domain.patient.model.Gender;
import com.hms.domain.patient.model.Patient;
import com.hms.domain.shared.model.EntityStatus;
import java.time.LocalDate;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-08-18T14:39:27+0530",
    comments = "version: 1.6.2, compiler: Eclipse JDT (IDE) 3.46.100.v20260624-0231, environment: Java 21.0.11 (Eclipse Adoptium)"
)
@Component
public class PatientMapperImpl implements PatientMapper {

    @Override
    public Patient fromRegisterRequest(RegisterPatientRequest req) {
        if ( req == null ) {
            return null;
        }

        Patient patient = new Patient();

        patient.setAddress( req.address() );
        patient.setBloodGroup( req.bloodGroup() );
        patient.setCategoryId( req.categoryId() );
        patient.setContactNumber( req.contactNumber() );
        patient.setDateOfBirth( req.dateOfBirth() );
        patient.setEmail( req.email() );
        patient.setEstimatedDateOfBirth( req.estimatedDateOfBirth() );
        patient.setFirstName( req.firstName() );
        patient.setGender( req.gender() );
        patient.setLastName( req.lastName() );
        patient.setPrimaryProviderId( req.primaryProviderId() );
        patient.setSalutation( req.salutation() );

        return patient;
    }

    @Override
    public PatientResponse toResponse(Patient patient, String patientNumber) {
        if ( patient == null && patientNumber == null ) {
            return null;
        }

        UUID id = null;
        String salutation = null;
        String firstName = null;
        String lastName = null;
        Gender gender = null;
        LocalDate dateOfBirth = null;
        LocalDate estimatedDateOfBirth = null;
        String contactNumber = null;
        String email = null;
        String bloodGroup = null;
        String address = null;
        UUID primaryProviderId = null;
        UUID categoryId = null;
        EntityStatus status = null;
        if ( patient != null ) {
            id = patient.getId();
            salutation = patient.getSalutation();
            firstName = patient.getFirstName();
            lastName = patient.getLastName();
            gender = patient.getGender();
            dateOfBirth = patient.getDateOfBirth();
            estimatedDateOfBirth = patient.getEstimatedDateOfBirth();
            contactNumber = patient.getContactNumber();
            email = patient.getEmail();
            bloodGroup = patient.getBloodGroup();
            address = patient.getAddress();
            primaryProviderId = patient.getPrimaryProviderId();
            categoryId = patient.getCategoryId();
            status = patient.getStatus();
        }
        String patientNumber1 = null;
        patientNumber1 = patientNumber;

        String age = patient.computeAge();
        String fullName = patient.computeFullName();
        boolean isInpatient = false;
        UUID activeEncounterId = null;
        UUID areaId = null;
        boolean isClinicalTrial = false;

        PatientResponse patientResponse = new PatientResponse( id, patientNumber1, salutation, firstName, lastName, fullName, gender, dateOfBirth, estimatedDateOfBirth, age, contactNumber, email, bloodGroup, address, primaryProviderId, areaId, categoryId, isClinicalTrial, status, isInpatient, activeEncounterId );

        return patientResponse;
    }

    @Override
    public void applyUpdateRequest(UpdatePatientRequest req, Patient patient) {
        if ( req == null ) {
            return;
        }

        if ( req.address() != null ) {
            patient.setAddress( req.address() );
        }
        if ( req.bloodGroup() != null ) {
            patient.setBloodGroup( req.bloodGroup() );
        }
        if ( req.contactNumber() != null ) {
            patient.setContactNumber( req.contactNumber() );
        }
        if ( req.dateOfBirth() != null ) {
            patient.setDateOfBirth( req.dateOfBirth() );
        }
        if ( req.email() != null ) {
            patient.setEmail( req.email() );
        }
        if ( req.estimatedDateOfBirth() != null ) {
            patient.setEstimatedDateOfBirth( req.estimatedDateOfBirth() );
        }
        if ( req.firstName() != null ) {
            patient.setFirstName( req.firstName() );
        }
        if ( req.gender() != null ) {
            patient.setGender( req.gender() );
        }
        if ( req.lastName() != null ) {
            patient.setLastName( req.lastName() );
        }
        if ( req.primaryProviderId() != null ) {
            patient.setPrimaryProviderId( req.primaryProviderId() );
        }
        if ( req.salutation() != null ) {
            patient.setSalutation( req.salutation() );
        }
    }
}
