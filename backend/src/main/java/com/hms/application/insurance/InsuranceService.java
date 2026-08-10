package com.hms.application.insurance;

import com.hms.api.insurance.request.CreateInsuranceRequest;
import com.hms.api.insurance.request.PreAuthRequest;
import com.hms.api.insurance.response.InsuranceResponse;
import com.hms.domain.insurance.model.Insurance;
import com.hms.domain.insurance.model.InsuranceStatus;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.insurance.InsuranceJpaRepository;
import com.hms.security.encryption.PiiSearchTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InsuranceService {

    private final InsuranceJpaRepository insuranceRepo;
    private final PiiSearchTokenService searchTokens;

    @Transactional
    public InsuranceResponse create(CreateInsuranceRequest req) {
        Insurance ins = new Insurance();
        ins.setPatientId(req.patientId());
        ins.setBillId(req.billId());
        ins.setEncounterId(req.encounterId());
        ins.setInsurerName(req.insurerName());
        ins.setPolicyNumber(req.policyNumber());

        // Screen 1.3 requires either a policy number or a member/card id, and
        // that rule is enforced in the Screen 1.3 form (validateManualPolicy).
        // It is deliberately NOT enforced here: this endpoint already backs the
        // existing insurance screen, which legitimately creates a record with an
        // insurer name alone while the paperwork is still being chased.
        // Tightening it server-side would break that screen as a side effect of
        // adding a new one.
        boolean hasMemberId = req.memberId() != null && !req.memberId().isBlank();

        ins.setMemberId(req.memberId());
        // Encrypted at rest, so an equality search would never match. The token
        // is what makes the member id findable.
        ins.setMemberIdToken(hasMemberId ? searchTokens.token(req.memberId()) : null);
        ins.setTpaName(req.tpaName());
        ins.setPolicyType(req.policyType());
        ins.setPreAuthType(req.preAuthType());
        ins.setCommunication(req.communication());
        if (req.preAuthType() != null) {
            ins.setInsuranceStatus(InsuranceStatus.PRE_AUTH_REQUESTED);
        } else {
            ins.setInsuranceStatus(InsuranceStatus.ACTIVE);
        }
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
