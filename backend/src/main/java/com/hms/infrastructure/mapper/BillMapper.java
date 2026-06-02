package com.hms.infrastructure.mapper;
import com.hms.api.billing.response.*;
import com.hms.domain.billing.model.*;
import org.mapstruct.*;
import java.util.List;
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface BillMapper {
    @Mapping(target = "status", source = "bill.billStatus")
    @Mapping(target = "dueAmount", expression = "java(bill.computeDueAmount())")
    @Mapping(target = "chargeLineItems", source = "bill.chargeLineItems")
    @Mapping(target = "payments", source = "bill.payments")
    @Mapping(target = "billNumber", source = "bill.billNumber")
    @Mapping(target = "discountTotal", source = "bill.discountTotal")
    @Mapping(target = "discountRefundTotal", source = "bill.discountRefundTotal")
    @Mapping(target = "patientName", source = "patientName")
    @Mapping(target = "patientNumber", source = "patientNumber")
    @Mapping(target = "patientGender", source = "patientGender")
    @Mapping(target = "consultantName", source = "consultantName")
    BillResponse toResponse(Bill bill, String patientName, String patientNumber, String patientGender, String consultantName);
    @Mapping(target = "id", source = "bill.id")
    @Mapping(target = "patientId", source = "bill.patientId")
    @Mapping(target = "encounterId", source = "bill.encounterId")
    @Mapping(target = "billAmount", source = "bill.billAmount")
    @Mapping(target = "dueAmount", expression = "java(bill.computeDueAmount())")
    @Mapping(target = "status", source = "bill.billStatus")
    @Mapping(target = "billType", source = "bill.billType")
    @Mapping(target = "encounterType", source = "bill.encounterType")
    @Mapping(target = "billDate", source = "bill.billDate")
    @Mapping(target = "billNumber", source = "bill.billNumber")
    @Mapping(target = "createdAt", source = "bill.createdAt")
    @Mapping(target = "refundTotal", source = "bill.refundTotal")
    @Mapping(target = "discountTotal", source = "bill.discountTotal")
    @Mapping(target = "discountRefundTotal", source = "bill.discountRefundTotal")
    @Mapping(target = "patientName", source = "patientName")
    @Mapping(target = "patientNumber", source = "patientNumber")
    BillSummaryResponse toSummaryResponse(Bill bill, String patientName, String patientNumber);

    @Mapping(target = "id", source = "bill.id")
    @Mapping(target = "patientId", source = "bill.patientId")
    @Mapping(target = "encounterId", source = "bill.encounterId")
    @Mapping(target = "billAmount", source = "bill.billAmount")
    @Mapping(target = "dueAmount", expression = "java(bill.computeDueAmount())")
    @Mapping(target = "status", source = "bill.billStatus")
    @Mapping(target = "billType", source = "bill.billType")
    @Mapping(target = "encounterType", source = "bill.encounterType")
    @Mapping(target = "billDate", source = "bill.billDate")
    @Mapping(target = "billNumber", source = "bill.billNumber")
    @Mapping(target = "createdAt", source = "bill.createdAt")
    @Mapping(target = "refundTotal", source = "bill.refundTotal")
    @Mapping(target = "discountTotal", source = "bill.discountTotal")
    @Mapping(target = "discountRefundTotal", source = "bill.discountRefundTotal")
    @Mapping(target = "patientName", ignore = true)
    @Mapping(target = "patientNumber", ignore = true)
    BillSummaryResponse toSummaryResponse(Bill bill);
    @Mapping(target = "status", source = "lineStatus")
    ChargeLineItemResponse toLineItemResponse(ChargeLineItem item);
    PaymentResponse toPaymentResponse(Payment payment);
    List<ChargeLineItemResponse> toLineItemResponses(List<ChargeLineItem> items);
    List<PaymentResponse> toPaymentResponses(List<Payment> payments);
}
