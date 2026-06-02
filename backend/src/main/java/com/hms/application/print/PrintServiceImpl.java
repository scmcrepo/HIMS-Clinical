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
            m.put("data.consultant.name", nvl(b.consultantName(), "—"));

            // Latest payment
            if (b.payments() != null && !b.payments().isEmpty()) {
                PaymentResponse p = b.payments().get(b.payments().size() - 1);
                m.put("data.receiptNumber",  nvl(p.sequenceNumber(), "—"));
                m.put("data.amount",         fmtAmt(p.amount()));
                m.put("data.paymentMode",    p.paymentMode() != null ? p.paymentMode().name() : "—");
                m.put("data.paymentDate",    fmtInstant(p.recordedAt()));
                m.put("numberToString",      numberToWords(p.amount() / 100.0));
            }

            // previousPaid = total paid minus last payment
            if (b.payments() != null && b.payments().size() > 1) {
                long latest = b.payments().get(b.payments().size() - 1).amount();
                long prev = b.payments().stream().mapToLong(PaymentResponse::amount).sum() - latest;
                m.put("data.previousPaid", fmtAmt(prev));
            } else {
                m.put("data.previousPaid", "0.00");
            }
            m.put("data.balance", fmtAmt(b.dueAmount()));

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
            Optional<PaymentResponse> refund = b.payments() != null
                    ? b.payments().stream()
                        .filter(p -> p.paymentType() != null &&
                                     p.paymentType().name().contains("REFUND"))
                        .reduce((a, c) -> c) // last one
                    : Optional.empty();

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

            // Provider
            // DiagnosticOrderResponse does not carry consultantName directly; we use providerId
            // as available. The template can also use data.consultantName which may be blank.
            m.put("data.consultantName", "—");

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
            if (p.paymentType() != null && p.paymentType().name().contains("REFUND")) continue;
            sb.append("<tr>")
              .append("<td>").append(fmtInstant(p.recordedAt())).append("</td>")
              .append("<td>").append(p.paymentMode() != null ? p.paymentMode().name() : "—").append("</td>")
              .append("<td class='r'>").append(fmtAmt(p.amount())).append("</td>")
              .append("</tr>");
        }
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
              .append("<td>").append(l.inventoryBatchId()).append("</td>") // itemName not in line — batch ref
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
        StringBuilder sb = new StringBuilder();
        for (DiagnosticOrderLineResponse l : lines) {
            String flag = "";
            if (l.hasResult() && l.resultValue() != null && l.referenceRange() != null) {
                flag = "<span class='flag fn'>N</span>";
            }
            sb.append("<tr>")
              .append("<td class='tname'>").append(esc(l.itemName())).append("</td>")
              .append("<td class='val'>").append(nvl(l.resultValue(), "—")).append("</td>")
              .append("<td class='unit'>").append(nvl(l.resultUnit(), "—")).append("</td>")
              .append("<td class='range'>").append(nvl(l.referenceRange(), "—")).append("</td>")
              .append("<td style='text-align:center'>").append(flag).append("</td>")
              .append("</tr>");
        }
        return sb.toString();
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
