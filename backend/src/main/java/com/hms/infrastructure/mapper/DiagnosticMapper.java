package com.hms.infrastructure.mapper;

import com.hms.api.diagnostic.response.DiagnosticOrderLineResponse;
import com.hms.api.diagnostic.response.DiagnosticOrderResponse;
import com.hms.domain.diagnostic.model.DiagnosticOrder;
import com.hms.domain.diagnostic.model.DiagnosticOrderLine;
import com.hms.infrastructure.sequence.NumberSequenceJpaRepository;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class DiagnosticMapper {

    @Autowired
    protected NumberSequenceJpaRepository numberSequenceRepo;

    @Autowired
    protected com.hms.infrastructure.persistence.specimen.SpecimenJpaRepository specimenRepo;

    @Mapping(target = "billed", expression = "java(order.isBilled())")
    @Mapping(target = "patientName", expression = "java(order.getPatient() != null ? order.getPatient().computeFullName() : null)")
    @Mapping(target = "patientNumber", expression = "java(order.getPatient() != null ? numberSequenceRepo.findById(order.getPatient().getId()).map(com.hms.infrastructure.sequence.NumberSequenceEntity::getValue).orElse(null) : null)")
    @Mapping(target = "patientGender", expression = "java(order.getPatient() != null && order.getPatient().getGender() != null ? order.getPatient().getGender().name() : null)")
    @Mapping(target = "patientAge", expression = "java(order.getPatient() != null ? order.getPatient().computeAge() : null)")
    @Mapping(target = "lines",  source = "lines")
    public abstract DiagnosticOrderResponse toResponse(DiagnosticOrder order);

    @Mapping(target = "hasResult", expression = "java(line.hasResult())")
    @Mapping(target = "specimenName", expression = "java(line.getSpecimenId() != null ? specimenRepo.findById(line.getSpecimenId()).map(com.hms.domain.diagnostic.model.Specimen::getName).orElse(null) : null)")
    public abstract DiagnosticOrderLineResponse toLineResponse(DiagnosticOrderLine line);

    public abstract List<DiagnosticOrderLineResponse> toLineResponses(List<DiagnosticOrderLine> lines);
}
