package com.hms.infrastructure.mapper;

import com.hms.api.diagnostic.response.DiagnosticOrderLineResponse;
import com.hms.api.diagnostic.response.DiagnosticOrderResponse;
import com.hms.domain.diagnostic.model.DiagnosticOrder;
import com.hms.domain.diagnostic.model.DiagnosticOrderLine;
import com.hms.domain.diagnostic.model.DiagnosticPaymentStatus;
import com.hms.domain.diagnostic.model.DiagnosticTestStatus;
import com.hms.domain.diagnostic.model.DiagnosticType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-08-18T14:39:28+0530",
    comments = "version: 1.6.2, compiler: Eclipse JDT (IDE) 3.46.100.v20260624-0231, environment: Java 21.0.11 (Eclipse Adoptium)"
)
@Component
public class DiagnosticMapperImpl extends DiagnosticMapper {

    @Override
    public DiagnosticOrderResponse toResponse(DiagnosticOrder order) {
        if ( order == null ) {
            return null;
        }

        List<DiagnosticOrderLineResponse> lines = null;
        UUID id = null;
        UUID encounterId = null;
        UUID patientId = null;
        UUID providerId = null;
        DiagnosticType diagnosticType = null;
        String sequenceNumber = null;
        LocalDate orderDate = null;
        DiagnosticPaymentStatus paymentStatus = null;
        DiagnosticTestStatus testStatus = null;

        lines = toLineResponses( order.getLines() );
        id = order.getId();
        encounterId = order.getEncounterId();
        patientId = order.getPatientId();
        providerId = order.getProviderId();
        diagnosticType = order.getDiagnosticType();
        sequenceNumber = order.getSequenceNumber();
        orderDate = order.getOrderDate();
        paymentStatus = order.getPaymentStatus();
        testStatus = order.getTestStatus();

        boolean billed = order.isBilled();
        String patientName = order.getPatient() != null ? order.getPatient().computeFullName() : null;
        String patientNumber = order.getPatient() != null ? numberSequenceRepo.findById(order.getPatient().getId()).map(com.hms.infrastructure.sequence.NumberSequenceEntity::getValue).orElse(null) : null;
        String patientGender = order.getPatient() != null && order.getPatient().getGender() != null ? order.getPatient().getGender().name() : null;
        String patientAge = order.getPatient() != null ? order.getPatient().computeAge() : null;
        String encounterType = null;

        DiagnosticOrderResponse diagnosticOrderResponse = new DiagnosticOrderResponse( id, encounterId, patientId, providerId, diagnosticType, sequenceNumber, orderDate, paymentStatus, testStatus, billed, patientName, patientNumber, patientGender, patientAge, encounterType, lines );

        return diagnosticOrderResponse;
    }

    @Override
    public DiagnosticOrderLineResponse toLineResponse(DiagnosticOrderLine line) {
        if ( line == null ) {
            return null;
        }

        UUID id = null;
        UUID serviceCatalogItemId = null;
        String itemName = null;
        UUID specimenId = null;
        String instruction = null;
        DiagnosticPaymentStatus paymentStatus = null;
        DiagnosticTestStatus testStatus = null;
        String resultValue = null;
        String resultUnit = null;
        String referenceRange = null;
        Instant resultRecordedAt = null;

        id = line.getId();
        serviceCatalogItemId = line.getServiceCatalogItemId();
        itemName = line.getItemName();
        specimenId = line.getSpecimenId();
        instruction = line.getInstruction();
        paymentStatus = line.getPaymentStatus();
        testStatus = line.getTestStatus();
        resultValue = line.getResultValue();
        resultUnit = line.getResultUnit();
        referenceRange = line.getReferenceRange();
        resultRecordedAt = line.getResultRecordedAt();

        boolean hasResult = line.hasResult();
        String specimenName = line.getSpecimenId() != null ? specimenRepo.findById(line.getSpecimenId()).map(com.hms.domain.diagnostic.model.Specimen::getName).orElse(null) : null;

        DiagnosticOrderLineResponse diagnosticOrderLineResponse = new DiagnosticOrderLineResponse( id, serviceCatalogItemId, itemName, specimenId, specimenName, instruction, paymentStatus, testStatus, resultValue, resultUnit, referenceRange, resultRecordedAt, hasResult );

        return diagnosticOrderLineResponse;
    }

    @Override
    public List<DiagnosticOrderLineResponse> toLineResponses(List<DiagnosticOrderLine> lines) {
        if ( lines == null ) {
            return null;
        }

        List<DiagnosticOrderLineResponse> list = new ArrayList<DiagnosticOrderLineResponse>( lines.size() );
        for ( DiagnosticOrderLine diagnosticOrderLine : lines ) {
            list.add( toLineResponse( diagnosticOrderLine ) );
        }

        return list;
    }
}
