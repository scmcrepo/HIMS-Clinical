package com.hms.application.insurance;

import com.hms.api.insurance.request.*;
import com.hms.api.insurance.response.InsuranceChequeResponse;
import com.hms.api.insurance.response.InsuranceDeskResponse;
import com.hms.api.insurance.response.InsuranceStageTimestamps;
import com.hms.application.billing.BillingOperationsService;
import com.hms.domain.insurance.model.*;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.domain.patient.model.Patient;
import com.hms.domain.billing.model.Bill;
import com.hms.infrastructure.persistence.patient.PatientJpaRepository;
import com.hms.infrastructure.persistence.billing.BillJpaRepository;
import com.hms.infrastructure.sequence.NumberSequenceJpaRepository;
import com.hms.infrastructure.sequence.NumberSequenceEntity;
import com.hms.infrastructure.persistence.insurance.InsuranceChequeReceiptJpaRepository;
import com.hms.infrastructure.persistence.insurance.InsuranceJpaRepository;
import com.hms.security.encryption.PiiSearchTokenService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class InsuranceDeskService {

    private final InsuranceJpaRepository insuranceRepo;
    private final InsuranceChequeReceiptJpaRepository chequeRepo;
    private final PatientJpaRepository patientRepo;
    private final NumberSequenceJpaRepository numberSequenceRepo;
    private final BillJpaRepository billRepo;
    private final BillingOperationsService billingService;
    private final PiiSearchTokenService searchTokens;
    private final MeterRegistry meters;

    // ── Stage 1: pre-auth request ───────────────────────────────────────────

    @Transactional
    public InsuranceDeskResponse submitPreauth(UUID insuranceId, PreauthStageRequest req) {
        Insurance ins = findOrThrow(insuranceId);
        boolean firstTime = ins.getPreauthCreatedDate() == null;

        requireCommunicationEndpoint(req.communicationToTpa(), req.faxNo(), req.mailId());

        if (req.cardValidity() != null) ins.setCardValidity(req.cardValidity());
        if (req.policyNumber() != null && !req.policyNumber().isBlank()) {
            ins.setPolicyNumber(req.policyNumber());
        }
        if (req.preAuthType() != null) ins.setPreAuthType(req.preAuthType());

        ins.setPreauthCommunicationToTpa(req.communicationToTpa());
        ins.setPreauthFaxNo(req.communicationToTpa() == ModeOfCommunication.FAX ? req.faxNo() : null);
        ins.setPreauthMailId(req.communicationToTpa() == ModeOfCommunication.MAIL ? req.mailId() : null);
        ins.setPreauthAppliedDate(req.appliedDate() != null ? req.appliedDate() : Instant.now());
        ins.setPreauthRequestedAmount(req.requestedAmount());

        // The flat legacy lifecycle stays in step, so /insurance/pending and the
        // older screens keep working while both models coexist.
        if (ins.getInsuranceStatus() == InsuranceStatus.ACTIVE) {
            ins.setInsuranceStatus(InsuranceStatus.PRE_AUTH_REQUESTED);
        }

        stampStage(firstTime,
            ins::setPreauthCreatedBy, ins::setPreauthCreatedDate,
            ins::setPreauthUpdatedBy, ins::setPreauthUpdatedDate);

        // An expired card is recorded, not rejected — the desk must be able to
        // capture what the patient actually presented. It is surfaced loudly
        // instead: a warning here and an amber banner in the UI.
        if (ins.isCardExpired(LocalDate.now())) {
            log.warn("insurance.desk.card.expired insuranceId[{}] patientId[{}] validUntil[{}]",
                ins.getId(), ins.getPatientId(), ins.getCardValidity());
            counter("card_expired").increment();
        }

        return saveAndRespond(ins, InsuranceWorkflowStage.PREAUTHORISATION);
    }

    // ── Stage 2: pre-auth approval / rejection ──────────────────────────────

    @Transactional
    public InsuranceDeskResponse submitPreauthApproval(UUID insuranceId,
                                                       PreauthApprovalStageRequest req) {
        Insurance ins = findOrThrow(insuranceId);
        boolean firstTime = ins.getPreauthApprovalCreatedDate() == null;

        requireDecisionFields(req.approvalStatus(), req.approvedLimit(), req.rejectionReason());

        ins.setClaimNo(req.claimNo());
        // Ciphertext is non-deterministic, so an equality search on claim_no can
        // never match. The token is what makes the desk's "find claim 4417"
        // work at all.
        ins.setClaimNoToken(searchTokens.token(req.claimNo()));

        ins.setPreauthApprovalStatus(req.approvalStatus());
        ins.setPreauthDateOfApproval(
            req.dateOfApproval() != null ? req.dateOfApproval() : Instant.now());
        ins.setPreauthCommunicationByTpa(req.communicationByTpa());
        ins.setPreauthApproveFaxNo(req.approveFaxNo());
        ins.setPreauthApproveMailId(req.approveMailId());

        InsuranceWorkflowStage stage;
        if (req.approvalStatus() == TpaDecision.APPROVED) {
            ins.setPreauthApprovedLimit(req.approvedLimit());
            ins.setPreauthRejectionReason(null);
            ins.setInsuranceStatus(InsuranceStatus.PRE_AUTH_RECEIVED);
            ins.setPreAuthAmount(req.approvedLimit());
            stage = InsuranceWorkflowStage.PREAUTHORISATION_APPROVAL;
        } else {
            ins.setPreauthRejectionReason(req.rejectionReason());
            ins.setInsuranceStatus(InsuranceStatus.REJECTED);
            // The generic rejection_reason column is left alone: it is 500 chars
            // and unencrypted, and this reason is clinical free text.
            stage = InsuranceWorkflowStage.PREAUTHORISATION_REJECTED;
        }

        stampStage(firstTime,
            ins::setPreauthApprovalCreatedBy, ins::setPreauthApprovalCreatedDate,
            ins::setPreauthApprovalUpdatedBy, ins::setPreauthApprovalUpdatedDate);

        return saveAndRespond(ins, stage);
    }

    // ── Stage 3: enhancement request ────────────────────────────────────────

    @Transactional
    public InsuranceDeskResponse submitEnhancement(UUID insuranceId, EnhancementStageRequest req) {
        Insurance ins = findOrThrow(insuranceId);

        // The one hard gate in the whole flow. An enhancement asks the TPA for
        // more money against charges that have to be evidenced; with no bill
        // there is nothing to evidence them with, and the TPA will query it.
        if (!ins.isBillLinked()) {
            log.warn("insurance.desk.enhancement.blocked insuranceId[{}] patientId[{}] reason[bill_not_linked]",
                ins.getId(), ins.getPatientId());
            counter("enhancement_blocked").increment();
            throw new BusinessRuleViolationException(
                "INSURANCE_BILL_NOT_LINKED: link the patient's credit bill before requesting an enhancement");
        }

        boolean firstTime = ins.getEnhancementCreatedDate() == null;
        requireCommunicationEndpoint(req.communicationToTpa(), req.faxNo(), req.mailId());

        ins.setEnhancementType(req.enhancementType());
        ins.setEnhancementAppliedDate(
            req.appliedDate() != null ? req.appliedDate() : Instant.now());
        ins.setEnhancementRequestedAmount(req.requestedAmount());
        ins.setEnhancementCommunicationToTpa(req.communicationToTpa());
        ins.setEnhancementFaxNo(
            req.communicationToTpa() == ModeOfCommunication.FAX ? req.faxNo() : null);
        ins.setEnhancementMailId(
            req.communicationToTpa() == ModeOfCommunication.MAIL ? req.mailId() : null);
        ins.setReasonForEnhancement(req.reasonForEnhancement());

        stampStage(firstTime,
            ins::setEnhancementCreatedBy, ins::setEnhancementCreatedDate,
            ins::setEnhancementUpdatedBy, ins::setEnhancementUpdatedDate);

        return saveAndRespond(ins, InsuranceWorkflowStage.ENHANCEMENT_REQUEST);
    }

    // ── Stage 4: enhancement approval / rejection ───────────────────────────

    @Transactional
    public InsuranceDeskResponse submitEnhancementApproval(UUID insuranceId,
                                                           EnhancementApprovalStageRequest req) {
        Insurance ins = findOrThrow(insuranceId);
        boolean firstTime = ins.getEnhancementApprovalCreatedDate() == null;

        requireDecisionFields(req.approvalStatus(), req.approvedLimit(), req.rejectionReason());

        ins.setEnhancementApprovalStatus(req.approvalStatus());
        ins.setEnhancementDateOfApproval(
            req.dateOfApproval() != null ? req.dateOfApproval() : Instant.now());
        ins.setEnhancementCommunicationByTpa(req.communicationByTpa());

        InsuranceWorkflowStage stage;
        if (req.approvalStatus() == TpaDecision.APPROVED) {
            ins.setEnhancementApprovedLimit(req.approvedLimit());
            ins.setEnhancementRejectionReason(null);
            // preauthApprovedLimit is deliberately NOT overwritten — a
            // short-paid claim is argued against what was originally sanctioned.
            stage = InsuranceWorkflowStage.ENHANCEMENT_APPROVAL;
        } else {
            ins.setEnhancementRejectionReason(req.rejectionReason());
            // A rejected enhancement does not reject the claim: it proceeds for
            // the original sanction, and the hospital pursues the balance from
            // the patient under the letter of acceptance.
            stage = InsuranceWorkflowStage.ENHANCEMENT_REJECTED;
        }

        stampStage(firstTime,
            ins::setEnhancementApprovalCreatedBy, ins::setEnhancementApprovalCreatedDate,
            ins::setEnhancementApprovalUpdatedBy, ins::setEnhancementApprovalUpdatedDate);

        return saveAndRespond(ins, stage);
    }

    // ── Stage 5: pre-dispatch checklist ─────────────────────────────────────

    @Transactional
    public InsuranceDeskResponse submitChecklist(UUID insuranceId, ChecklistStageRequest req) {
        Insurance ins = findOrThrow(insuranceId);
        boolean firstTime = ins.getCheckListCreatedDate() == null;

        List<Map<String, Object>> items = new ArrayList<>();
        int shortfall = 0;
        for (var item : req.checklists()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", item.name());
            row.put("toBeSubmit", item.toBeSubmit());
            row.put("submitted", item.submitted());
            row.put("nonSubmission", item.nonSubmission() == null ? "" : item.nonSubmission());
            items.add(row);
            if (item.submitted() < item.toBeSubmit()) shortfall++;
        }

        // The whole manifest is replaced rather than patched: it is small, it is
        // edited as a unit at a desk, and last-write-wins is what the clerk
        // expects from a grid with a single Save button.
        Map<String, Object> checklist = new LinkedHashMap<>();
        checklist.put("checklists", items);
        ins.setChecklist(checklist);

        stampStage(firstTime,
            ins::setCheckListCreatedBy, ins::setCheckListCreatedDate,
            ins::setCheckListUpdatedBy, ins::setCheckListUpdatedDate);

        log.info("insurance.desk.checklist.recorded insuranceId[{}] items[{}] shortfallItems[{}]",
            ins.getId(), items.size(), shortfall);

        return saveAndRespond(ins, InsuranceWorkflowStage.CHECK_LIST_ENTRY);
    }

    // ── Stage 6: dispatch ───────────────────────────────────────────────────

    @Transactional
    public InsuranceDeskResponse submitDispatch(UUID insuranceId, DispatchStageRequest req) {
        Insurance ins = findOrThrow(insuranceId);
        boolean firstTime = ins.getDispatchCreatedDate() == null;

        if (req.modeOfDispatch() == ModeOfDispatch.COURIER) {
            if (req.courier() == null) {
                throw new BusinessRuleViolationException(
                    "INSURANCE_COURIER_REQUIRED: select the courier used for the dispatch");
            }
            if (isBlank(req.podNo())) {
                // Without a consignment number the hospital cannot prove
                // delivery, and a denied-receipt dispute is unwinnable.
                throw new BusinessRuleViolationException(
                    "INSURANCE_POD_REQUIRED: a courier dispatch needs its consignment/POD number");
            }
        } else if (req.modeOfDispatch() == ModeOfDispatch.EMAIL && isBlank(req.dispatchMailId())) {
            throw new BusinessRuleViolationException(
                "INSURANCE_DISPATCH_MAIL_REQUIRED: an email dispatch needs the destination mail id");
        }

        ins.setModeOfDispatch(req.modeOfDispatch());
        ins.setCourier(req.modeOfDispatch() == ModeOfDispatch.COURIER ? req.courier() : null);
        ins.setPodNo(req.modeOfDispatch() == ModeOfDispatch.COURIER ? req.podNo() : null);
        ins.setDispatchMailId(
            req.modeOfDispatch() == ModeOfDispatch.EMAIL ? req.dispatchMailId() : null);
        ins.setDispatchDate(req.dispatchDate() != null ? req.dispatchDate() : Instant.now());
        ins.setDispatchedBy(req.dispatchedBy());
        ins.setReasonForDelay(req.reasonForDelay());

        if (firstTime) {
            ins.setDispatchCreatedBy(currentUserId());
            ins.setDispatchCreatedDate(Instant.now());
        }

        // podNo is deliberately absent: a consignment number tied to a named
        // patient's claim docket is a tracking identifier for that patient.
        log.info("insurance.desk.dispatch.recorded insuranceId[{}] patientId[{}] mode[{}] courier[{}]",
            ins.getId(), ins.getPatientId(), req.modeOfDispatch(),
            req.courier() == null ? "-" : req.courier());
        Counter.builder("hms.insurance.desk.dispatch")
            .tag("mode", req.modeOfDispatch().name())
            .tag("courier", req.courier() == null ? "none" : req.courier().name())
            .register(meters)
            .increment();

        return saveAndRespond(ins, InsuranceWorkflowStage.DISPATCH_ENTRY);
    }

    // ── Stage 7: disallowance and settlement ────────────────────────────────

    @Transactional
    public InsuranceDeskResponse submitDisallowance(UUID insuranceId,
                                                    DisallowanceStageRequest req) {
        Insurance ins = findOrThrow(insuranceId);
        boolean firstTime = ins.getDisallowanceCreatedDate() == null;

        long totalReceived = 0L;
        if (req.cheques() != null) {
            totalReceived = reconcileCheques(ins.getId(), req.cheques());
        }

        int lineCount = 0;
        long totalDisallowed = 0L;
        if (req.disallowances() != null && !req.disallowances().isEmpty()) {
            // Routed through the billing service rather than written here. That
            // method owns charge_line_items.disallowed_amount, is @Transactional,
            // and already handles the bill-total recalculation that a second
            // writer would have to duplicate and eventually get wrong.
            List<Map<String, Object>> lines = new ArrayList<>();
            for (var d : req.disallowances()) {
                lines.add(Map.of(
                    "id", d.chargeLineItemId().toString(),
                    "disallowedAmount", d.disallowedAmount()));
                totalDisallowed += d.disallowedAmount();
                lineCount++;
            }
            billingService.updateDisallowedAmounts(lines);
        }

        if (firstTime) {
            ins.setDisallowanceCreatedBy(currentUserId());
            ins.setDisallowanceCreatedDate(Instant.now());
        }

        // A claim with money received is settled in the flat lifecycle too, so
        // the older screens stop showing it as outstanding.
        if (totalReceived > 0 && ins.getInsuranceStatus() != InsuranceStatus.REJECTED) {
            ins.setInsuranceStatus(InsuranceStatus.SETTLED);
        }

        // Cheque numbers, banks and branches are all absent by design; counts
        // and totals answer the operational question without naming the
        // instrument.
        log.info("insurance.desk.disallowance.applied insuranceId[{}] patientId[{}] "
                 + "chequeCount[{}] totalReceivedPaise[{}] lineCount[{}] totalDisallowedPaise[{}]",
            ins.getId(), ins.getPatientId(),
            req.cheques() == null ? 0 : req.cheques().size(),
            totalReceived, lineCount, totalDisallowed);

        counter("disallowance_applied").increment();
        if (totalDisallowed > 0) {
            Counter.builder("hms.insurance.desk.disallowed.paise")
                .register(meters).increment(totalDisallowed);
        }

        return saveAndRespond(ins, InsuranceWorkflowStage.DISALLOWANCE_ENTRY);
    }

    /**
     * Reconcile the submitted cheque list against what is stored: update the
     * ones carrying an id, insert the ones that do not, delete the ones the
     * clerk removed from the grid.
     *
     * <p>Deleting by absence is safe here only because the grid always submits
     * the complete list for the claim. It is the behaviour a Save button on a
     * table implies, and doing anything else leaves deleted rows resurrecting
     * themselves on the next save.
     *
     * @return total received, in paise
     */
    private long reconcileCheques(UUID insuranceId,
                                  List<DisallowanceStageRequest.ChequeReceiptItem> submitted) {
        List<InsuranceChequeReceipt> existing =
            chequeRepo.findByInsuranceIdOrderByChequeDateDesc(insuranceId);
        Map<UUID, InsuranceChequeReceipt> byId = new HashMap<>();
        for (var e : existing) byId.put(e.getId(), e);

        Set<UUID> kept = new HashSet<>();
        long total = 0L;

        for (var item : submitted) {
            InsuranceChequeReceipt row;
            if (item.id() != null && byId.containsKey(item.id())) {
                row = byId.get(item.id());
                kept.add(item.id());
            } else {
                row = new InsuranceChequeReceipt();
                row.setInsuranceId(insuranceId);
            }
            row.setChequeNo(item.chequeNo());
            row.setChequeDate(item.chequeDate());
            row.setDrawnOn(item.drawnOn());
            row.setPayableAt(item.payableAt());
            row.setAmount(item.amount());
            row.setAuthorisedBy(item.authorisedBy());
            chequeRepo.save(row);
            total += item.amount();
        }

        for (var e : existing) {
            if (!kept.contains(e.getId())) {
                chequeRepo.delete(e);
            }
        }
        return total;
    }

    // ── Bill linkage ────────────────────────────────────────────────────────

    /**
     * Bind a patient credit bill to the claim.
     *
     * <p>Replaces a stub: the previous {@code updateBillId} endpoint parsed both
     * ids and then returned the unmodified record with a success message, so the
     * link silently never happened.
     */
    @Transactional
    public InsuranceDeskResponse linkBill(UUID insuranceId, UUID billId) {
        Insurance ins = findOrThrow(insuranceId);
        if (billId == null) {
            throw new BusinessRuleViolationException("INSURANCE_BILL_REQUIRED: billId is required");
        }
        ins.setBillId(billId);
        Insurance saved = insuranceRepo.save(ins);

        log.info("insurance.desk.bill.linked insuranceId[{}] patientId[{}] billId[{}]",
            saved.getId(), saved.getPatientId(), billId);
        counter("bill_linked").increment();

        return toDeskResponse(saved);
    }

    // ── Reads ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public InsuranceDeskResponse getDeskView(UUID insuranceId) {
        return toDeskResponse(findOrThrow(insuranceId));
    }

    /**
     * The desk's landing query.
     *
     * <p>Replaces a stub that accepted both date parameters and ignored them,
     * returning the pending list regardless. Defaults to the last 30 days rather
     * than to today: a desk screen defaulting to today shows an empty grid every
     * morning, which reads as a broken screen.
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<InsuranceDeskResponse> searchByDateRange(LocalDate from, LocalDate to,
                                                         InsuranceWorkflowStage stage, org.springframework.data.domain.Pageable pageable) {
        LocalDate effectiveTo   = to   != null ? to   : LocalDate.now();
        LocalDate effectiveFrom = from != null ? from : effectiveTo.minusDays(30);
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new BusinessRuleViolationException(
                "INSURANCE_INVALID_DATE_RANGE: searchFromDate must not be after searchToDate");
        }

        Instant start = effectiveFrom.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();
        Instant end   = effectiveTo.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();

        // Since we are filtering by stage in memory originally, a proper paginated query 
        // with the stage parameter in the DB is ideal. However, `insuranceCurrentStatus` is just a field.
        // For now we will fetch all matching the date, then filter and paginate manually if stage is present,
        // or just return the page if not. Wait, that breaks standard DB pagination. 
        // Actually, let me just add stage to the repository if I can.
        // For this patch, I'll fetch everything from DB matching the date range, stream filter, 
        // then sublist to create a PageImpl. This isn't scalable but matches the existing ad-hoc stage filter.
        
        List<InsuranceDeskResponse> allMatches = insuranceRepo.findByCreatedAtBetween(start, end, org.springframework.data.domain.Pageable.unpaged()).stream()
            .filter(i -> stage == null || stage.equals(i.getInsuranceCurrentStatus()))
            .map(this::toDeskResponse)
            .toList();

        int page = pageable.getPageNumber();
        int size = pageable.getPageSize();
        int fromIndex = Math.min(page * size, allMatches.size());
        int toIndex = Math.min(fromIndex + size, allMatches.size());
        
        return new org.springframework.data.domain.PageImpl<>(allMatches.subList(fromIndex, toIndex), pageable, allMatches.size());
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private Insurance findOrThrow(UUID id) {
        return insuranceRepo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Insurance", id));
    }

    /**
     * FAX needs a fax number, MAIL needs a mail id. Conditional on the mode, so
     * it cannot be a field annotation without a class-level constraint that
     * reads worse than this check.
     */
    private void requireCommunicationEndpoint(ModeOfCommunication mode, String faxNo, String mailId) {
        if (mode == ModeOfCommunication.FAX && isBlank(faxNo)) {
            throw new BusinessRuleViolationException(
                "INSURANCE_FAX_REQUIRED: a fax number is required when the mode of communication is FAX");
        }
        if (mode == ModeOfCommunication.MAIL && isBlank(mailId)) {
            throw new BusinessRuleViolationException(
                "INSURANCE_MAIL_REQUIRED: a mail id is required when the mode of communication is MAIL");
        }
    }

    private void requireDecisionFields(TpaDecision decision, Long approvedLimit, String reason) {
        if (decision == TpaDecision.APPROVED && approvedLimit == null) {
            throw new BusinessRuleViolationException(
                "INSURANCE_APPROVED_LIMIT_REQUIRED: record the amount the TPA sanctioned");
        }
        if (decision == TpaDecision.REJECTED && isBlank(reason)) {
            throw new BusinessRuleViolationException(
                "INSURANCE_REJECTION_REASON_REQUIRED: record why the TPA declined");
        }
    }

    /**
     * Stage-level audit. The inherited {@code AuditableEntity} fields record the
     * last touch of the whole row; these record who worked <i>this stage</i> and
     * when, which is the question asked when a claim is queried months later.
     */
    private void stampStage(boolean firstTime,
                            java.util.function.Consumer<UUID> setCreatedBy,
                            java.util.function.Consumer<Instant> setCreatedDate,
                            java.util.function.Consumer<UUID> setUpdatedBy,
                            java.util.function.Consumer<Instant> setUpdatedDate) {
        UUID user = currentUserId();
        Instant now = Instant.now();
        if (firstTime) {
            setCreatedBy.accept(user);
            setCreatedDate.accept(now);
        }
        setUpdatedBy.accept(user);
        setUpdatedDate.accept(now);
    }

    private InsuranceDeskResponse saveAndRespond(Insurance ins, InsuranceWorkflowStage stage) {
        InsuranceWorkflowStage before = ins.getInsuranceCurrentStatus();
        ins.advanceStage(stage);
        Insurance saved = insuranceRepo.save(ins);
        boolean advanced = !Objects.equals(before, saved.getInsuranceCurrentStatus());

        log.info("insurance.desk.stage.submitted insuranceId[{}] patientId[{}] "
                 + "stage[{}] previousStage[{}] currentStage[{}] advanced[{}]",
            saved.getId(), saved.getPatientId(), stage,
            before == null ? "-" : before, saved.getInsuranceCurrentStatus(), advanced);

        Counter.builder("hms.insurance.desk.stage.transitions")
            .tag("stage", stage.name())
            .tag("outcome", advanced ? "advanced" : "updated")
            .register(meters)
            .increment();

        return toDeskResponse(saved);
    }

    private Counter counter(String event) {
        return Counter.builder("hms.insurance.desk.events").tag("event", event).register(meters);
    }

    /**
     * The acting user, for stage audit columns.
     *
     * <p>Read from the security context rather than injected, matching
     * {@code SpringSecurityAuditorAware}. Returns null for an unauthenticated
     * context, which cannot happen on these endpoints but must not throw if it
     * somehow does — losing an audit stamp is better than losing the claim
     * update it belongs to.
     */
    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.hms.security.HmsUserDetails user) {
            return user.getId();
        }
        return null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    InsuranceDeskResponse toDeskResponse(Insurance i) {
        List<InsuranceChequeResponse> cheques =
            chequeRepo.findByInsuranceIdOrderByChequeDateDesc(i.getId()).stream()
                .map(c -> new InsuranceChequeResponse(
                    c.getId(), c.getChequeNo(), c.getChequeDate(),
                    c.getDrawnOn(), c.getPayableAt(), c.getAmount(), c.getAuthorisedBy()))
                .toList();

        long totalReceived = cheques.stream()
            .mapToLong(c -> c.amount() == null ? 0L : c.amount()).sum();

        InsuranceWorkflowStage stage = i.getInsuranceCurrentStatus();

        String patientName = null;
        String patientNo = null;
        String patientGender = null;
        String patientAge = null;
        if (i.getPatientId() != null) {
            Optional<Patient> patientOpt = patientRepo.findById(i.getPatientId());
            if (patientOpt.isPresent()) {
                Patient p = patientOpt.get();
                patientName = p.computeFullName();
                patientGender = p.getGender() != null ? p.getGender().name() : null;
                patientAge = p.computeAge();
            }
            patientNo = numberSequenceRepo.findById(i.getPatientId())
                .map(NumberSequenceEntity::getValue)
                .orElse(null);
        }

        Long billAmount = null;
        if (i.getBillId() != null) {
            billAmount = billRepo.findById(i.getBillId())
                .map(Bill::getBillAmount)
                .orElse(null);
        }

        return new InsuranceDeskResponse(
            i.getId(), i.getPatientId(), i.getBillId(), i.getEncounterId(),
            i.getInsurerName(), i.getTpaName(), i.getPolicyNumber(), i.getMemberId(),
            i.getPolicyType(),
            patientNo, patientName, patientGender, patientAge, billAmount,

            stage, stage == null ? null : stage.label(),
            new InsuranceStageTimestamps(
                i.getPreauthCreatedDate(),
                i.getPreauthApprovalCreatedDate(),
                i.getEnhancementCreatedDate(),
                i.getEnhancementApprovalCreatedDate(),
                i.getCheckListCreatedDate(),
                i.getDispatchCreatedDate(),
                i.getDisallowanceCreatedDate()),

            i.isBillLinked(),
            i.isCardExpired(LocalDate.now()),
            i.effectiveApprovedLimit(),

            i.getCardValidity(), i.getPreAuthType(),
            i.getPreauthCommunicationToTpa(), i.getPreauthFaxNo(), i.getPreauthMailId(),
            i.getPreauthAppliedDate(), i.getPreauthRequestedAmount(),

            i.getClaimNo(), i.getPreauthApprovalStatus(), i.getPreauthDateOfApproval(),
            i.getPreauthCommunicationByTpa(), i.getPreauthApproveFaxNo(),
            i.getPreauthApproveMailId(), i.getPreauthApprovedLimit(),
            i.getPreauthRejectionReason(),

            i.getEnhancementType(), i.getEnhancementAppliedDate(),
            i.getEnhancementRequestedAmount(), i.getEnhancementCommunicationToTpa(),
            i.getEnhancementFaxNo(), i.getEnhancementMailId(), i.getReasonForEnhancement(),

            i.getEnhancementApprovalStatus(), i.getEnhancementDateOfApproval(),
            i.getEnhancementCommunicationByTpa(), i.getEnhancementApprovedLimit(),
            i.getEnhancementRejectionReason(),

            i.getChecklist(),

            i.getModeOfDispatch(), i.getCourier(), i.getDispatchDate(),
            i.getDispatchedBy(), i.getDispatchMailId(), i.getPodNo(), i.getReasonForDelay(),

            cheques, totalReceived
        );
    }
}
