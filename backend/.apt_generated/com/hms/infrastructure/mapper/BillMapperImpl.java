package com.hms.infrastructure.mapper;

import com.hms.api.billing.response.BillResponse;
import com.hms.api.billing.response.BillSummaryResponse;
import com.hms.api.billing.response.ChargeLineItemResponse;
import com.hms.api.billing.response.PaymentResponse;
import com.hms.domain.billing.model.Bill;
import com.hms.domain.billing.model.BillStatus;
import com.hms.domain.billing.model.BillType;
import com.hms.domain.billing.model.ChargeLineItem;
import com.hms.domain.billing.model.ChargeLineStatus;
import com.hms.domain.billing.model.EncounterType;
import com.hms.domain.billing.model.Payment;
import com.hms.domain.billing.model.PaymentMode;
import com.hms.domain.billing.model.PaymentType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-08-18T14:39:29+0530",
    comments = "version: 1.6.2, compiler: Eclipse JDT (IDE) 3.46.100.v20260624-0231, environment: Java 21.0.11 (Eclipse Adoptium)"
)
@Component
public class BillMapperImpl implements BillMapper {

    @Override
    public BillResponse toResponse(Bill bill, String patientName, String patientNumber, String patientGender, String consultantName) {
        if ( bill == null && patientName == null && patientNumber == null && patientGender == null && consultantName == null ) {
            return null;
        }

        BillStatus status = null;
        List<ChargeLineItemResponse> chargeLineItems = null;
        List<PaymentResponse> payments = null;
        String billNumber = null;
        long discountTotal = 0L;
        long discountRefundTotal = 0L;
        UUID id = null;
        UUID patientId = null;
        UUID encounterId = null;
        UUID primaryProviderId = null;
        UUID payorId = null;
        UUID referralId = null;
        long billAmount = 0L;
        long paymentTotal = 0L;
        long serviceRefundTotal = 0L;
        long refundTotal = 0L;
        BillType billType = null;
        EncounterType encounterType = null;
        LocalDate billDate = null;
        Instant admissionAt = null;
        Instant dischargeAt = null;
        String bedNumber = null;
        Instant cancelledAt = null;
        Instant createdAt = null;
        if ( bill != null ) {
            status = bill.getBillStatus();
            chargeLineItems = toLineItemResponses( bill.getChargeLineItems() );
            payments = toPaymentResponses( bill.getPayments() );
            billNumber = bill.getBillNumber();
            discountTotal = bill.getDiscountTotal();
            discountRefundTotal = bill.getDiscountRefundTotal();
            id = bill.getId();
            patientId = bill.getPatientId();
            encounterId = bill.getEncounterId();
            primaryProviderId = bill.getPrimaryProviderId();
            payorId = bill.getPayorId();
            referralId = bill.getReferralId();
            billAmount = bill.getBillAmount();
            paymentTotal = bill.getPaymentTotal();
            serviceRefundTotal = bill.getServiceRefundTotal();
            refundTotal = bill.getRefundTotal();
            billType = bill.getBillType();
            encounterType = bill.getEncounterType();
            billDate = bill.getBillDate();
            admissionAt = bill.getAdmissionAt();
            dischargeAt = bill.getDischargeAt();
            bedNumber = bill.getBedNumber();
            cancelledAt = bill.getCancelledAt();
            createdAt = bill.getCreatedAt();
        }
        String patientName1 = null;
        patientName1 = patientName;
        String patientNumber1 = null;
        patientNumber1 = patientNumber;
        String patientGender1 = null;
        patientGender1 = patientGender;
        String consultantName1 = null;
        consultantName1 = consultantName;

        long dueAmount = bill.computeDueAmount();

        BillResponse billResponse = new BillResponse( id, patientId, encounterId, primaryProviderId, payorId, referralId, patientName1, patientNumber1, patientGender1, consultantName1, billAmount, discountTotal, discountRefundTotal, paymentTotal, serviceRefundTotal, refundTotal, dueAmount, status, billType, encounterType, billDate, admissionAt, dischargeAt, bedNumber, billNumber, cancelledAt, createdAt, chargeLineItems, payments );

        return billResponse;
    }

    @Override
    public BillSummaryResponse toSummaryResponse(Bill bill, String patientName, String patientNumber) {
        if ( bill == null && patientName == null && patientNumber == null ) {
            return null;
        }

        UUID id = null;
        UUID patientId = null;
        UUID encounterId = null;
        long billAmount = 0L;
        BillStatus status = null;
        BillType billType = null;
        EncounterType encounterType = null;
        LocalDate billDate = null;
        String billNumber = null;
        Instant createdAt = null;
        long refundTotal = 0L;
        long discountTotal = 0L;
        long discountRefundTotal = 0L;
        if ( bill != null ) {
            id = bill.getId();
            patientId = bill.getPatientId();
            encounterId = bill.getEncounterId();
            billAmount = bill.getBillAmount();
            status = bill.getBillStatus();
            billType = bill.getBillType();
            encounterType = bill.getEncounterType();
            billDate = bill.getBillDate();
            billNumber = bill.getBillNumber();
            createdAt = bill.getCreatedAt();
            refundTotal = bill.getRefundTotal();
            discountTotal = bill.getDiscountTotal();
            discountRefundTotal = bill.getDiscountRefundTotal();
        }
        String patientName1 = null;
        patientName1 = patientName;
        String patientNumber1 = null;
        patientNumber1 = patientNumber;

        long dueAmount = bill.computeDueAmount();

        BillSummaryResponse billSummaryResponse = new BillSummaryResponse( id, patientId, patientName1, patientNumber1, encounterId, billAmount, dueAmount, discountTotal, discountRefundTotal, status, billType, encounterType, billDate, billNumber, createdAt, refundTotal );

        return billSummaryResponse;
    }

    @Override
    public BillSummaryResponse toSummaryResponse(Bill bill) {
        if ( bill == null ) {
            return null;
        }

        UUID id = null;
        UUID patientId = null;
        UUID encounterId = null;
        long billAmount = 0L;
        BillStatus status = null;
        BillType billType = null;
        EncounterType encounterType = null;
        LocalDate billDate = null;
        String billNumber = null;
        Instant createdAt = null;
        long refundTotal = 0L;
        long discountTotal = 0L;
        long discountRefundTotal = 0L;

        id = bill.getId();
        patientId = bill.getPatientId();
        encounterId = bill.getEncounterId();
        billAmount = bill.getBillAmount();
        status = bill.getBillStatus();
        billType = bill.getBillType();
        encounterType = bill.getEncounterType();
        billDate = bill.getBillDate();
        billNumber = bill.getBillNumber();
        createdAt = bill.getCreatedAt();
        refundTotal = bill.getRefundTotal();
        discountTotal = bill.getDiscountTotal();
        discountRefundTotal = bill.getDiscountRefundTotal();

        long dueAmount = bill.computeDueAmount();
        String patientName = null;
        String patientNumber = null;

        BillSummaryResponse billSummaryResponse = new BillSummaryResponse( id, patientId, patientName, patientNumber, encounterId, billAmount, dueAmount, discountTotal, discountRefundTotal, status, billType, encounterType, billDate, billNumber, createdAt, refundTotal );

        return billSummaryResponse;
    }

    @Override
    public ChargeLineItemResponse toLineItemResponse(ChargeLineItem item) {
        if ( item == null ) {
            return null;
        }

        ChargeLineStatus status = null;
        UUID serviceCatalogItemId = null;
        String itemName = null;
        UUID pricingTierId = null;
        long amount = 0L;
        long unitRate = 0L;
        int quantity = 0;
        boolean quantitative = false;
        long discountAmount = 0L;
        long disallowedAmount = 0L;
        Instant bedChargeFrom = null;
        Instant bedChargeTo = null;
        String cancelReason = null;
        Instant createdAt = null;

        status = item.getLineStatus();
        serviceCatalogItemId = item.getServiceCatalogItemId();
        itemName = item.getItemName();
        pricingTierId = item.getPricingTierId();
        amount = item.getAmount();
        unitRate = item.getUnitRate();
        quantity = item.getQuantity();
        quantitative = item.isQuantitative();
        discountAmount = item.getDiscountAmount();
        disallowedAmount = item.getDisallowedAmount();
        bedChargeFrom = item.getBedChargeFrom();
        bedChargeTo = item.getBedChargeTo();
        cancelReason = item.getCancelReason();
        createdAt = item.getCreatedAt();

        UUID id = item.getId() != null ? item.getId() : (item.getDiagnosticOrderLineId() != null ? item.getDiagnosticOrderLineId() : item.getPharmacySaleId());

        ChargeLineItemResponse chargeLineItemResponse = new ChargeLineItemResponse( id, serviceCatalogItemId, itemName, pricingTierId, amount, unitRate, quantity, quantitative, discountAmount, disallowedAmount, status, bedChargeFrom, bedChargeTo, cancelReason, createdAt );

        return chargeLineItemResponse;
    }

    @Override
    public PaymentResponse toPaymentResponse(Payment payment) {
        if ( payment == null ) {
            return null;
        }

        UUID id = null;
        long amount = 0L;
        PaymentMode paymentMode = null;
        PaymentType paymentType = null;
        Instant recordedAt = null;
        String sequenceNumber = null;
        String notes = null;

        id = payment.getId();
        amount = payment.getAmount();
        paymentMode = payment.getPaymentMode();
        paymentType = payment.getPaymentType();
        recordedAt = payment.getRecordedAt();
        sequenceNumber = payment.getSequenceNumber();
        notes = payment.getNotes();

        PaymentResponse paymentResponse = new PaymentResponse( id, amount, paymentMode, paymentType, recordedAt, sequenceNumber, notes );

        return paymentResponse;
    }

    @Override
    public List<ChargeLineItemResponse> toLineItemResponses(List<ChargeLineItem> items) {
        if ( items == null ) {
            return null;
        }

        List<ChargeLineItemResponse> list = new ArrayList<ChargeLineItemResponse>( items.size() );
        for ( ChargeLineItem chargeLineItem : items ) {
            list.add( toLineItemResponse( chargeLineItem ) );
        }

        return list;
    }

    @Override
    public List<PaymentResponse> toPaymentResponses(List<Payment> payments) {
        if ( payments == null ) {
            return null;
        }

        List<PaymentResponse> list = new ArrayList<PaymentResponse>( payments.size() );
        for ( Payment payment : payments ) {
            list.add( toPaymentResponse( payment ) );
        }

        return list;
    }
}
