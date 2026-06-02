package com.hms.application.insurance;

import com.hms.api.insurance.request.CreateInsuranceRequest;
import com.hms.api.insurance.request.PreAuthRequest;
import com.hms.api.insurance.response.InsuranceResponse;
import com.hms.domain.insurance.model.Insurance;
import com.hms.domain.insurance.model.InsuranceStatus;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.insurance.InsuranceJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InsuranceService {

    private final InsuranceJpaRepository insuranceRepo;

    @Transactional
    public InsuranceResponse create(CreateInsuranceRequest req) {
        Insurance ins = new Insurance();
        ins.setPatientId(req.patientId());
        ins.setBillId(req.billId());
        ins.setEncounterId(req.encounterId());
        ins.setInsurerName(req.insurerName());
        ins.setPolicyNumber(req.policyNumber());
        ins.setPreAuthType(req.preAuthType());
        ins.setCommunication(req.communication());
        ins.setInsuranceStatus(InsuranceStatus.ACTIVE);
        return toResponse(insuranceRepo.save(ins));
    }

    @Transactional
    public InsuranceResponse receivePreAuth(UUID insuranceId, PreAuthRequest req) {
        Insurance ins = findOrThrow(insuranceId);
        ins.receivePreAuth(req.preAuthNumber(), req.amount(), req.receivedDate());
        return toResponse(insuranceRepo.save(ins));
    }

    @Transactional
    public InsuranceResponse reject(UUID insuranceId, String reason) {
        Insurance ins = findOrThrow(insuranceId);
        ins.reject(reason);
        return toResponse(insuranceRepo.save(ins));
    }

    @Transactional
    public InsuranceResponse settle(UUID insuranceId) {
        Insurance ins = findOrThrow(insuranceId);
        ins.settle();
        return toResponse(insuranceRepo.save(ins));
    }

    @Transactional(readOnly = true)
    public InsuranceResponse getById(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<InsuranceResponse> getByPatient(UUID patientId) {
        return insuranceRepo.findByPatientIdOrderByCreatedAtDesc(patientId)
            .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<InsuranceResponse> getByBill(UUID billId) {
        return insuranceRepo.findByBillIdOrderByCreatedAtDesc(billId)
            .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<InsuranceResponse> getPending() {
        return insuranceRepo.findByStatus(InsuranceStatus.PRE_AUTH_REQUESTED)
            .stream().map(this::toResponse).toList();
    }

    private Insurance findOrThrow(UUID id) {
        return insuranceRepo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Insurance", id));
    }

    private InsuranceResponse toResponse(Insurance i) {
        return new InsuranceResponse(
            i.getId(), i.getPatientId(), i.getBillId(), i.getEncounterId(),
            i.getInsurerName(), i.getPolicyNumber(),
            i.getPreAuthType(), i.getPreAuthNumber(),
            i.getPreAuthAmount(), i.getPreAuthDate(),
            i.getCommunication(), i.getInsuranceStatus(),
            i.getRejectionReason()
        );
    }
}
