package com.hms.application.print;

import com.hms.api.billing.response.BillResponse;
import com.hms.api.billing.response.ChargeLineItemResponse;
import com.hms.api.billing.response.PaymentResponse;
import com.hms.api.diagnostic.response.DiagnosticOrderLineResponse;
import com.hms.api.diagnostic.response.DiagnosticOrderResponse;
import com.hms.api.printtemplate.response.PrintOutputResponse;
import com.hms.api.sales.response.PharmacySaleResponse;
import com.hms.application.billing.BillingOperationsService;
import com.hms.application.diagnostic.DiagnosticOrderingService;
import com.hms.application.sales.PharmacySaleService;
import com.hms.domain.shared.model.PrintTemplate;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.printtemplate.PrintTemplateJpaRepository;
import com.hms.infrastructure.persistence.diagtemplate.DiagnosticTemplateJpaRepository;
import com.hms.infrastructure.persistence.diagnostic.DiagnosticReportJpaRepository;
import com.hms.domain.diagnostic.model.DiagnosticTemplate;
import com.hms.domain.diagnostic.model.LabTemplateDetail;
import com.hms.domain.diagnostic.model.DiagnosticReport;
import com.hms.infrastructure.persistence.consultant.ConsultantJpaRepository;
import com.hms.infrastructure.persistence.encounter.ClinicalEncounterJpaRepository;
import com.hms.infrastructure.settings.SettingsRegistryImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PrintServiceImpl — complete rewrite.
 *
 * Design:
 *  1. Load PrintTemplate from DB (by documentType).
 *  2. Build a flat model Map by calling EXISTING service beans (BillingOperationsService,
 *     PharmacySaleService, DiagnosticOrderingService) — NO raw SQL, NO DataApiService.
 *  3. Resolve every #{path} placeholder in the HTML template by walking the model map.
 *  4. Return the filled HTML (or ESC/POS pages for DOT_MATRIX).
 *
 * Placeholder syntax:  #{key}  or  #{nested.key}  or  #{deeply.nested.key}
 * All values are pre-flattened into the model with dot-path keys so resolution is O(1).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PrintServiceImpl implements PrintService {

    private final PrintTemplateJpaRepository  printTemplateRepo;
    private final SettingsRegistryImpl        settings;
    private final BillingOperationsService    billingService;
    private final PharmacySaleService         saleService;
    private final DiagnosticOrderingService   diagnosticService;
    private final DiagnosticTemplateJpaRepository templateRepo;
    private final DiagnosticReportJpaRepository   reportRepo;
    private final ConsultantJpaRepository         consultantRepo;
    private final ClinicalEncounterJpaRepository  encounterRepo;

    private static final Pattern PLACEHOLDER = Pattern.compile("#\\{([^}]+)}");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    // ── Entry point ────────────────────────────────────────────────────────────

    @Override
    public PrintOutputResponse print(String templateType, Map<String, String> printParams) {

        PrintTemplate template = printTemplateRepo
                .findDefaultByDocumentType(templateType)
                .orElseGet(() -> printTemplateRepo
                        .findByDocumentType(templateType)
                        .stream().findFirst()
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "No print template configured for type: " + templateType)));

        // Build the flat model — all values are strings keyed by dot-path
        Map<String, String> model = buildModel(templateType, printParams);

        // Resolve all #{...} placeholders in the template HTML
        String html = resolvePlaceholders(
                template.getContent() != null ? template.getContent() : "", model);

        PrintOutputResponse.PrintOutputResponseBuilder b = PrintOutputResponse.builder()
                .printMode(nvl(template.getPrintMode(), "HTML"))
                .width(nvl(template.getWidth(), "210mm"))
                .height(nvl(template.getHeight(), "297mm"))
                .marginTop(nvl(template.getMarginTop(), "10mm"))
                .marginBottom(nvl(template.getMarginBottom(), "10mm"))
                .marginLeft(nvl(template.getMarginLeft(), "10mm"))
                .marginRight(nvl(template.getMarginRight(), "10mm"))
                .defaultPrinter(template.getDefaultPrinter());

        if ("DOT_MATRIX".equals(template.getPrintMode())) {
            b.rawPages(List.of(html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim()));
        } else {
            b.printData(html);
        }
        return b.build();
    }

    // ── Model builders — one per document type ─────────────────────────────────

    private Map<String, String> buildModel(String templateType, Map<String, String> params) {
        Map<String, String> m = new LinkedHashMap<>();

        // Always-present context
        putProfile(m);
        m.put("date", new SimpleDateFormat("dd MMM yyyy").format(new Date()));
        m.put("dateTime", new SimpleDateFormat("dd MMM yyyy HH:mm").format(new Date()));

        String id = params.get("id");

        switch (templateType.toUpperCase()) {
            case "BILL"                   -> putBillModel(m, id);
            case "OP_RECEIPT", "IP_RECEIPT", "PAYMENT" -> putReceiptModel(m, id, params);
            case "IP_BILL_CONSOLIDATED", "IP_BILL_DETAIL", "PROVISIONAL_BILL" -> putBillModel(m, id);
            case "SALES"                  -> putSaleModel(m, id);
            case "LAB"                    -> putDiagnosticModel(m, id, "LAB");
            case "RADIOLOGY"              -> putDiagnosticModel(m, id, "RADIOLOGY");
            case "DIAGNOSTIC_ORDER"       -> putDiagnosticModel(m, id, "ORDER");
            case "REFUND_RECEIPT", "ADVANCE_REFUND_RECEIPT" -> putRefundModel(m, id, params);
            default                       -> log.warn("PrintService: no model builder for templateType={}", templateType);
        }

        return m;
    }

    // ── BILL model ─────────────────────────────────────────────────────────────

    private void putBillModel(Map<String, String> m, String billId) {
        if (billId == null) return;
        try {
            BillResponse b = billingService.getBillById(UUID.fromString(billId));

            m.put("data.billNumber",        nvl(b.billNumber(), "—"));
            m.put("data.billDate",          fmt(b.billDate()));
            m.put("data.billType",          b.billType()      != null ? b.billType().name()      : "—");
            m.put("data.encounterType",     b.encounterType() != null ? b.encounterType().name()  : "—");
            m.put("data.status",            b.status()        != null ? b.status().name()         : "—");

            // Amounts: stored in paise → convert to rupees string
            m.put("data.billAmount",    fmtAmt(b.billAmount()));
            m.put("data.discountTotal", fmtAmt(b.discountTotal()));
            m.put("data.paymentTotal",  fmtAmt(b.paymentTotal()));
            m.put("data.dueAmount",     fmtAmt(b.dueAmount()));

            // Patient
            m.put("data.patient.fullName",       nvl(b.patientName(),  "—"));
            m.put("data.patient.patientNumber",  nvl(b.patientNumber(), "—"));
            m.put("data.patient.gender",         nvl(b.patientGender(), "—"));

            // Consultant
            m.put("data.consultant.name", nvl(b.consultantName(), "—"));

            // Admission/Discharge (IP)
            m.put("data.admissionDate", fmtInstant(b.admissionAt()));
            m.put("data.dischargeDate", fmtInstant(b.dischargeAt()));
            m.put("data.bed",           nvl(b.bedNumber(), "—"));

            // Charge line items as a table
            m.put("data.chargeLines", buildChargeLinesHtml(b.chargeLineItems()));

            // Payments
            m.put("data.payments", buildPaymentsHtml(b.payments()));
            m.put("data.paymentsTable", buildPaymentsTableHtml(b.payments()));

            // Amount in words
            if (b.billAmount() > 0)
                m.put("numberToString", numberToWords(b.billAmount() / 100.0));

        } catch (Exception e) {
            log.error("PrintService: failed to load bill {}: {}", billId, e.getMessage(), e);
        }
    }

    // ── RECEIPT model ──────────────────────────────────────────────────────────

    private void putReceiptModel(Map<String, String> m, String billId, Map<String, String> params) {
        // Receipt prints are triggered with the billId; we load the bill and
        // pick the latest payment.
        if (billId == null) return;
        try {
            BillResponse b = billingService.getBillById(UUID.fromString(billId));

            m.put("data.billNumber",      nvl(b.billNumber(), "—"));
            m.put("data.billDate",        fmt(b.billDate()));
            m.put("data.billAmount",      fmtAmt(b.billAmount()));
            m.put("data.discountTotal",   fmtAmt(b.discountTotal()));
            m.put("data.paymentTotal",    fmtAmt(b.paymentTotal()));
            m.put("data.dueAmount",       fmtAmt(b.dueAmount()));
            m.put("data.patient.fullName",      nvl(b.patientName(),   "—"));
            m.put("data.patient.patientNumber", nvl(b.patientNumber(), "—"));
            m.put("data.patient.gender",        nvl(b.patientGender(), "—"));
            m.put("data.consultant.name",       nvl(b.consultantName(), "—"));
            m.put("data.chargeLines",           buildChargeLinesHtml(b.chargeLineItems()));

            // Resolve specific payment if requested via paymentId parameter
            PaymentResponse p = null;
            if (b.payments() != null && !b.payments().isEmpty()) {
                String targetPaymentId = params.get("paymentId");
                if (targetPaymentId != null && !targetPaymentId.isBlank()) {
                    try {
                        UUID pid = UUID.fromString(targetPaymentId);
                        p = b.payments().stream()
                                .filter(pm -> pm.id().equals(pid))
                                .findFirst().orElse(null);
                    } catch (Exception ignored) {}
                }
                if (p == null) {
                    p = b.payments().get(b.payments().size() - 1);
                }
            }

            if (p != null) {
                m.put("data.receiptNumber",  nvl(p.sequenceNumber(), "—"));
                m.put("data.amount",         fmtAmt(p.amount()));
                m.put("data.paymentMode",    p.paymentMode() != null ? p.paymentMode().name() : "—");
                m.put("data.paymentDate",    fmtInstant(p.recordedAt()));
                m.put("numberToString",      numberToWords(p.amount() / 100.0));

                // previousPaid = sum of all payments/refunds made before this transaction index
                int index = b.payments().indexOf(p);
                long prev = 0;
                for (int i = 0; i < index; i++) {
                    PaymentResponse pm = b.payments().get(i);
                    if (pm.paymentType() != null && pm.paymentType().name().contains("REFUND")) {
                        prev -= pm.amount();
                    } else {
                        prev += pm.amount();
                    }
                }
                m.put("data.previousPaid", fmtAmt(prev));

                // Current transaction amount (negative if refund)
                long currentAmt = p.paymentType() != null && p.paymentType().name().contains("REFUND") 
                        ? -p.amount() 
                        : p.amount();
                long balance = b.billAmount() - prev - currentAmt;
                m.put("data.balance", fmtAmt(balance));
            } else {
                m.put("data.previousPaid", "0.00");
                m.put("data.balance", fmtAmt(b.dueAmount()));
            }

        } catch (Exception e) {
            log.error("PrintService: receipt load failed for bill {}: {}", billId, e.getMessage(), e);
        }
    }

    // ── REFUND model ───────────────────────────────────────────────────────────

    private void putRefundModel(Map<String, String> m, String billId, Map<String, String> params) {
        if (billId == null) return;
        try {
            BillResponse b = billingService.getBillById(UUID.fromString(billId));
            m.put("data.billNumber",            nvl(b.billNumber(),    "—"));
            m.put("data.patient.fullName",       nvl(b.patientName(),   "—"));
            m.put("data.patient.patientNumber",  nvl(b.patientNumber(), "—"));

            // Find refund payment
            Optional<PaymentResponse> refund = Optional.empty();
            if (b.payments() != null) {
                String targetPaymentId = params.get("paymentId");
                if (targetPaymentId != null && !targetPaymentId.isBlank()) {
                    try {
                        UUID pid = UUID.fromString(targetPaymentId);
                        refund = b.payments().stream()
                                .filter(pm -> pm.id().equals(pid))
                                .findFirst();
                    } catch (Exception ignored) {}
                }
                if (refund.isEmpty()) {
                    refund = b.payments().stream()
                            .filter(pm -> pm.paymentType() != null &&
                                         pm.paymentType().name().contains("REFUND"))
                            .reduce((a, c) -> c); // last one
                }
            }

            refund.ifPresent(p -> {
                m.put("data.refundNumber", nvl(p.sequenceNumber(), "—"));
                m.put("data.amount",       fmtAmt(p.amount()));
                m.put("data.paymentMode",  p.paymentMode() != null ? p.paymentMode().name() : "—");
                m.put("data.reason",       nvl(p.notes(), "Patient request"));
                m.put("numberToString",    numberToWords(p.amount() / 100.0));
            });

        } catch (Exception e) {
            log.error("PrintService: refund load failed for bill {}: {}", billId, e.getMessage(), e);
        }
    }

    // ── SALES model ────────────────────────────────────────────────────────────

    private void putSaleModel(Map<String, String> m, String saleId) {
        if (saleId == null) return;
        try {
            PharmacySaleResponse s = saleService.getById(UUID.fromString(saleId));

            m.put("data.sequenceNumber", nvl(s.sequenceNumber(), "—"));
            m.put("data.saleDate",       fmt(s.saleDate()));
            m.put("data.totalAmount",    s.totalAmount()    != null ? s.totalAmount().toPlainString()    : "0.00");
            m.put("data.discountAmount", s.discountAmount() != null ? s.discountAmount().toPlainString() : "0.00");
            m.put("data.paidAmount",     s.paidAmount()     != null ? s.paidAmount().toPlainString()     : "0.00");
            m.put("data.dueAmount",      s.dueAmount()      != null ? s.dueAmount().toPlainString()      : "0.00");
            m.put("data.patientName",    nvl(s.patientName(),   nvl(s.customerName(), "Walk-in")));
            m.put("data.consultantName", nvl(s.consultantName(), "—"));
            m.put("data.patientNumber",  nvl(s.patientNumber(), "—"));
            m.put("data.paymentMode",    nvl(s.paymentMode(), "Cash"));
            m.put("data.status",         s.status() != null ? s.status().name() : "—");

            // Sale lines as HTML table rows
            m.put("data.saleLines", buildSaleLinesHtml(s.lines()));

            if (s.totalAmount() != null)
                m.put("numberToString", numberToWords(s.totalAmount().doubleValue()));

        } catch (Exception e) {
            log.error("PrintService: sale load failed for {}: {}", saleId, e.getMessage(), e);
        }
    }

    // ── DIAGNOSTIC (LAB / RADIOLOGY / ORDER) model ────────────────────────────

    private void putDiagnosticModel(Map<String, String> m, String orderId, String mode) {
        if (orderId == null) return;
        try {
            DiagnosticOrderResponse d = diagnosticService.getById(UUID.fromString(orderId));

            m.put("data.sequenceNumber",  nvl(d.sequenceNumber(), "—"));
            m.put("data.orderDate",       fmt(d.orderDate()));
            m.put("data.patientName",     nvl(d.patientName(),   "—"));
            m.put("data.patientNumber",   nvl(d.patientNumber(), "—"));
            m.put("data.patientAge",      nvl(d.patientAge(),    "—"));
            m.put("data.patientGender",   nvl(d.patientGender(), "—"));
            m.put("data.sampleDate",      fmt(d.orderDate()));
            m.put("data.department",      d.diagnosticType() != null ? d.diagnosticType().name() : "—");

            // Provider - Check order's providerId, fallback to encounter's primaryProviderId
            UUID providerId = d.providerId();
            if (providerId == null && d.encounterId() != null) {
                providerId = encounterRepo.findById(d.encounterId())
                        .map(com.hms.domain.encounter.model.ClinicalEncounter::getPrimaryProviderId)
                        .orElse(null);
            }

            if (providerId != null) {
                m.put("data.consultantName", consultantRepo.findById(providerId)
                        .map(com.hms.domain.consultant.model.Consultant::getFullName)
                        .orElse("—"));
            } else {
                m.put("data.consultantName", "—");
            }

            if (d.lines() != null && !d.lines().isEmpty()) {
                DiagnosticOrderLineResponse first = d.lines().get(0);
                m.put("data.testName",    nvl(first.itemName(),    "—"));
                m.put("data.specimen",    nvl(first.specimenName(), "Blood"));
                m.put("data.resultValue", nvl(first.resultValue(),  "—"));
                m.put("data.resultUnit",  nvl(first.resultUnit(),   "—"));
                m.put("data.referenceRange", nvl(first.referenceRange(), "—"));
            }

            // Build results table for LAB / RADIOLOGY
            if (!"ORDER".equals(mode)) {
                m.put("data.resultLines", buildResultLinesHtml(d.lines()));
            }

            // Order lines list for ORDER mode
            if ("ORDER".equals(mode)) {
                m.put("data.orderLines", buildOrderLinesHtml(d.lines()));
            }

        } catch (Exception e) {
            log.error("PrintService: diagnostic load failed for {}: {}", orderId, e.getMessage(), e);
        }
    }

    // ── Hospital profile ───────────────────────────────────────────────────────

    private void putProfile(Map<String, String> m) {
        m.put("profile.name",      settings.get("HOSPITAL_PARAM", "hospital.name.param")
                                           .orElse("City Hospital"));
        m.put("profile.address",   settings.get("HOSPITAL_PARAM", "hospital.address.param")
                                           .orElse(""));
        m.put("profile.contactNo", settings.get("HOSPITAL_PARAM", "hospital.contactNo.param")
                                           .orElse(""));
    }

    // ── Placeholder resolver ───────────────────────────────────────────────────
    // All model keys are already flat dot-path strings ("data.patient.fullName"),
    // so resolution is a direct map lookup — no recursion needed.

    private String resolvePlaceholders(String source, Map<String, String> model) {
        Matcher m  = PLACEHOLDER.matcher(source);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String path  = m.group(1);                        // e.g. "data.patient.fullName"
            String value = model.getOrDefault(path, "");      // direct O(1) lookup
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // ── HTML table builders ────────────────────────────────────────────────────

    private String buildChargeLinesHtml(List<ChargeLineItemResponse> lines) {
        if (lines == null || lines.isEmpty())
            return "<tr><td colspan='6' style='text-align:center;color:#999'>No charges</td></tr>";

        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (ChargeLineItemResponse l : lines) {
            if (l.status() != null && l.status().name().equals("CANCELLED")) continue;
            sb.append("<tr>")
              .append("<td class='muted'>").append(i++).append("</td>")
              .append("<td>").append(esc(l.itemName())).append("</td>")
              .append("<td style='text-align:center'>").append(l.quantity()).append("</td>")
              .append("<td class='r'>").append(fmtAmt(l.unitRate())).append("</td>")
              .append("<td class='r'>").append(fmtAmt(l.discountAmount())).append("</td>")
              .append("<td class='r'>").append(fmtAmt(l.amount())).append("</td>")
              .append("</tr>");
        }
        return sb.toString();
    }

    private String buildPaymentsHtml(List<PaymentResponse> payments) {
        if (payments == null || payments.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (PaymentResponse p : payments) {
            boolean isRefund = p.paymentType() != null && p.paymentType().name().contains("REFUND");
            String prefix = isRefund ? "-" : "";
            sb.append("<tr>")
              .append("<td class='muted'>").append(fmtInstant(p.recordedAt())).append("</td>")
              .append("<td>").append(nvl(p.sequenceNumber(), "—")).append("</td>")
              .append("<td>").append(p.paymentMode() != null ? p.paymentMode().name() : "—").append("</td>")
              .append("<td class='r'>").append(prefix).append(fmtAmt(p.amount())).append("</td>")
              .append("</tr>");
        }
        return sb.toString();
    }

    private String buildPaymentsTableHtml(List<PaymentResponse> payments) {
        if (payments == null || payments.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("<div class='sh' style='margin-top:12px'>Payments / Receipts</div>");
        sb.append("<table>");
        sb.append("<thead><tr><th>Receipt Date</th><th>Receipt No</th><th>Mode of Pay</th><th class='r' style='width:90px'>Amount (&#8377;)</th></tr></thead>");
        sb.append("<tbody>");
        sb.append(buildPaymentsHtml(payments));
        sb.append("</tbody>");
        sb.append("</table>");
        return sb.toString();
    }

    private String buildSaleLinesHtml(List<PharmacySaleResponse.SaleLineResponse> lines) {
        if (lines == null || lines.isEmpty())
            return "<tr><td colspan='5' style='text-align:center;color:#999'>No items</td></tr>";
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (PharmacySaleResponse.SaleLineResponse l : lines) {
            sb.append("<tr>")
              .append("<td>").append(i++).append("</td>")
              .append("<td>").append(esc(l.itemName())).append("</td>")
              .append("<td style='text-align:center'>").append(l.quantity()).append("</td>")
              .append("<td class='r'>").append(l.unitRate() != null ? l.unitRate().toPlainString() : "—").append("</td>")
              .append("<td class='r'>").append(l.amount() != null ? l.amount().toPlainString() : "—").append("</td>")
              .append("</tr>");
        }
        return sb.toString();
    }

    private String buildResultLinesHtml(List<DiagnosticOrderLineResponse> lines) {
        if (lines == null || lines.isEmpty())
            return "<tr><td colspan='5' style='text-align:center;color:#999'>Awaiting results</td></tr>";
        
        record LineWithTemplate(DiagnosticOrderLineResponse line, DiagnosticTemplate template) {}
        
        Map<String, String> displayNames = new HashMap<>();
        Map<String, List<LineWithTemplate>> grouped = new LinkedHashMap<>();
        
        for (DiagnosticOrderLineResponse l : lines) {
            // Try to load the template
            List<DiagnosticTemplate> templates = l.serviceCatalogItemId() != null
                    ? templateRepo.findByChargeId(l.serviceCatalogItemId())
                    : Collections.emptyList();
            DiagnosticTemplate template = templates.isEmpty() ? null : templates.get(0);
            
            String deptName = null;
            if (template != null && template.getDepartment() != null) {
                deptName = template.getDepartment().getName();
            }
            if (deptName == null || deptName.isBlank()) {
                deptName = "GENERAL";
            }
            
            String normKey = deptName.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
            if (!displayNames.containsKey(normKey)) {
                displayNames.put(normKey, deptName.trim());
            }
            
            grouped.computeIfAbsent(normKey, k -> new ArrayList<>())
                   .add(new LineWithTemplate(l, template));
        }

        StringBuilder sb = new StringBuilder();
        
        for (Map.Entry<String, List<LineWithTemplate>> entry : grouped.entrySet()) {
            String normKey = entry.getKey();
            String deptDisplayName = displayNames.get(normKey);
            List<LineWithTemplate> items = entry.getValue();
            
            // Print Department Header row
            sb.append("<tr>")
              .append("<td colspan='5' style='font-weight:bold;color:#1e3a8a;text-decoration:underline;padding:12px 8px 4px;font-size:11px;text-transform:uppercase;letter-spacing:0.5px;'>")
              .append(esc(deptDisplayName))
              .append("</td>")
              .append("</tr>");
            
            for (LineWithTemplate item : items) {
                DiagnosticOrderLineResponse l = item.line();
                DiagnosticTemplate template = item.template();
                
                if (template != null && template.getLabTemplateDetails() != null && !template.getLabTemplateDetails().isEmpty()) {
                    // Fetch saved report values for this order line
                    List<DiagnosticReport> reports = reportRepo.findByDiagnosticOrderLineId(l.id());
                    Map<UUID, String> reportMap = new HashMap<>();
                    for (DiagnosticReport r : reports) {
                        if (r.getLabTemplateDetailId() != null && r.getValue() != null) {
                            reportMap.put(r.getLabTemplateDetailId(), r.getValue());
                        }
                    }

                    List<LabTemplateDetail> details = new ArrayList<>(template.getLabTemplateDetails());
                    details.sort(Comparator.comparing(LabTemplateDetail::getOrderNumber, Comparator.nullsLast(Integer::compareTo)));

                    boolean showHeader = details.size() > 1;
                    if (showHeader) {
                        String headerName = nvl(template.getHeader(), l.itemName());
                        sb.append("<tr>")
                          .append("<td colspan='5' style='font-weight:bold;color:#1e3a8a;background-color:#eff6ff;padding:5px 8px;font-size:10px;'>")
                          .append(esc(headerName.toUpperCase()))
                          .append("</td>")
                          .append("</tr>");
                    }

                    for (LabTemplateDetail ltd : details) {
                        if ("HEADER".equals(ltd.getLabType())) {
                            sb.append("<tr>")
                              .append("<td colspan='5' style='font-weight:bold;color:#1e3a8a;background-color:#f8fafc;padding:4px 8px 4px 14px;font-size:9.5px;'>")
                              .append(esc(ltd.getResultName().toUpperCase()))
                              .append("</td>")
                              .append("</tr>");
                            continue;
                        }

                        String val = reportMap.getOrDefault(ltd.getId(), "");
                        String range = ltd.getNormalRange() != null ? ltd.getNormalRange() : "";
                        String flagVal = evaluateResult(val, range);
                        String flagHtml = getFlagHtml(flagVal);

                        // If showHeader is true, we indent the parameter name
                        String nameStyle = showHeader ? "padding-left: 20px;" : "font-weight:600;";

                        sb.append("<tr>")
                          .append("<td class='tname' style='").append(nameStyle).append("'>").append(esc(ltd.getResultName())).append("</td>")
                          .append("<td class='val'>").append(val.isEmpty() ? "—" : esc(val)).append("</td>")
                          .append("<td class='unit'>").append(nvl(ltd.getUnit(), "—")).append("</td>")
                          .append("<td class='range'>").append(range.isEmpty() ? "—" : esc(range)).append("</td>")
                          .append("<td style='text-align:center'>").append(flagHtml).append("</td>")
                          .append("</tr>");
                    }
                } else {
                    // Fallback to order line itself (single parameter / direct entry)
                    String val = l.resultValue();
                    String range = l.referenceRange() != null ? l.referenceRange() : "";
                    String flagVal = evaluateResult(val, range);
                    String flagHtml = getFlagHtml(flagVal);

                    sb.append("<tr>")
                      .append("<td class='tname'>").append(esc(l.itemName())).append("</td>")
                      .append("<td class='val'>").append(val == null || val.isEmpty() ? "—" : esc(val)).append("</td>")
                      .append("<td class='unit'>").append(nvl(l.resultUnit(), "—")).append("</td>")
                      .append("<td class='range'>").append(range.isEmpty() ? "—" : esc(range)).append("</td>")
                      .append("<td style='text-align:center'>").append(flagHtml).append("</td>")
                      .append("</tr>");
                }
            }
        }
        return sb.toString();
    }

    private String evaluateResult(String value, String range) {
        if (value == null || range == null || value.isBlank() || range.isBlank()) return "";
        try {
            double num = Double.parseDouble(value.trim());
            Pattern pattern = Pattern.compile("([\\d.]+)\\s*[-–]\\s*([\\d.]+)");
            Matcher m = pattern.matcher(range);
            if (m.find()) {
                double low = Double.parseDouble(m.group(1));
                double high = Double.parseDouble(m.group(2));
                if (num < low) return "L";
                if (num > high) return "H";
                return "N";
            }
        } catch (NumberFormatException ignored) {}
        
        String vLower = value.toLowerCase().trim();
        if (vLower.equals("normal") || vLower.equals("nil") || vLower.equals("negative")) return "N";
        if (vLower.equals("high") || vLower.equals("positive")) return "H";
        if (vLower.equals("low")) return "L";
        
        return "";
    }

    private String getFlagHtml(String flagValue) {
        if ("N".equals(flagValue)) {
            return "<span class='flag fn'>N</span>";
        } else if ("H".equals(flagValue)) {
            return "<span class='flag fh'>H</span>";
        } else if ("L".equals(flagValue)) {
            return "<span class='flag fl'>L</span>";
        }
        return "";
    }

    private String buildOrderLinesHtml(List<DiagnosticOrderLineResponse> lines) {
        if (lines == null || lines.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (DiagnosticOrderLineResponse l : lines) {
            sb.append("<div class='test-item'>")
              .append("<div class='tno'>").append(i++).append("</div>")
              .append("<div><div class='tname'>").append(esc(l.itemName())).append("</div>")
              .append("<div class='tdept'>").append(nvl(l.specimenName(), "")).append("</div></div>")
              .append("<span class='spec-tag'>").append(nvl(l.specimenName(), "Sample")).append("</span>")
              .append("</div>");
        }
        return sb.toString();
    }

    // ── Formatters ─────────────────────────────────────────────────────────────

    /** Convert paise (long) → "1,650.00" */
    private String fmtAmt(long paise) {
        return String.format("%.2f", paise / 100.0);
    }

    private String fmt(LocalDate d) {
        return d != null ? d.format(DATE_FMT) : "—";
    }

    private String fmtInstant(Instant i) {
        if (i == null) return "—";
        return i.atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_FMT);
    }

    private String nvl(String s, String fallback) {
        return (s != null && !s.isBlank()) ? s : (fallback != null ? fallback : "");
    }


    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ── Amount in words (Indian numbering system) ──────────────────────────────

    private static final String[] ONES = {
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
        "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
        "Seventeen", "Eighteen", "Nineteen"
    };
    private static final String[] TENS = {
        "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    };

    private String numberToWords(double amount) {
        long rupees = (long) amount;
        long paise  = Math.round((amount - rupees) * 100);
        if (rupees == 0 && paise == 0) return "Zero Rupees";
        String result = convertInt(rupees) + "Rupees";
        if (paise > 0) result += " and " + convertInt(paise) + "Paise";
        return result.trim();
    }

    private String convertInt(long n) {
        if (n <= 0) return "";
        String w = "";
        if (n / 10_000_000 > 0) { w += convertInt(n / 10_000_000) + "Crore "; n %= 10_000_000; }
        if (n / 100_000   > 0) { w += convertInt(n / 100_000)    + "Lakh ";  n %= 100_000;    }
        if (n / 1_000     > 0) { w += convertInt(n / 1_000)      + "Thousand "; n %= 1_000;   }
        if (n / 100       > 0) { w += ONES[(int)(n / 100)] + " Hundred "; n %= 100; }
        if (n >= 20)           { w += TENS[(int)(n / 10)] + " " + ONES[(int)(n % 10)] + " "; }
        else if (n > 0)        { w += ONES[(int) n] + " "; }
        return w;
    }

}
