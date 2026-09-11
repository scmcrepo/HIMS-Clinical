package com.hms.application.gst;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hms.domain.gst.model.GstFilingError;
import com.hms.domain.gst.model.GstFilingLineItem;
import com.hms.domain.inventory.model.HsnCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Turns reported supplies into GSTR-1 and GSTR-3B payloads (A2, A3).
 *
 * <p>The tax split is decided here rather than in SQL because it depends on two things
 * that only meet at this point: the place of supply on the line, and the hospital's own
 * state from configuration. Same state means CGST + SGST; different state means IGST.
 * A line with no place of supply recorded is a supply made at the establishment, so it
 * falls back to the hospital's own state and is intra-state.
 *
 * <p>The builder also validates before it emits (A5). A hospital using this to file
 * manually has no GSP to reject a bad return, so the checks that a GSP would apply —
 * unusable HSN codes, unclassified services, a missing GSTIN — are run here and
 * surfaced as errors against the filing record instead.
 */
@Component
@RequiredArgsConstructor
public class GstPayloadBuilder {

    private final ObjectMapper objectMapper;

    private static BigDecimal money(Object value) {
        if (value == null) return BigDecimal.ZERO;
        return new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP);
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    /** One reported supply, already split into its final tax heads. */
    public record Supply(
        String source,
        UUID sourceId,
        String invoiceNumber,
        java.time.LocalDate invoiceDate,
        String recipientGstin,
        String placeOfSupply,
        String hsnSacCode,
        BigDecimal taxRate,
        BigDecimal taxableValue,
        BigDecimal cgst,
        BigDecimal sgst,
        BigDecimal igst,
        boolean reverseCharge,
        String gstTreatment
    ) {
        boolean isB2b()      { return recipientGstin != null && !recipientGstin.isBlank(); }
        boolean isExempt()   { return gstTreatment != null && !"TAXABLE".equals(gstTreatment); }
        BigDecimal totalTax() { return cgst.add(sgst).add(igst); }
        BigDecimal invoiceValue() { return taxableValue.add(totalTax()); }
    }

    /** A registered recipient's identity, resolved from the payor master. */
    public record Recipient(String gstin, String stateCode) {}

    /**
     * Applies the intra/inter-state split to raw filing rows.
     *
     * <p>Two things are resolved per row, both in the same order of precedence: whatever
     * was recorded on the bill itself, then the payor the bill was raised against, then
     * the hospital's own state. Putting the payor in the middle is what makes a GSTIN
     * added to the master today also correct the bills already raised against it.
     *
     * @param homeStateCode the hospital's own state, from its GSTIN
     * @param payors        recipient details by payor id; empty for pharmacy rows
     */
    public List<Supply> toSupplies(List<Map<String, Object>> rows, String homeStateCode,
                                   Map<UUID, Recipient> payors) {
        List<Supply> supplies = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            if (Boolean.TRUE.equals(r.get("__EMPTY_ROW__"))) continue;

            Object payorIdVal = r.get("payor_id");
            Recipient payor = payorIdVal instanceof UUID pid ? payors.get(pid) : null;

            String recipientGstin = str(r.get("recipient_gstin"));
            if (recipientGstin == null || recipientGstin.isBlank()) {
                recipientGstin = payor != null ? payor.gstin() : null;
            }

            String pos = str(r.get("place_of_supply"));
            if (pos == null || pos.isBlank()) pos = payor != null ? payor.stateCode() : null;
            if (pos == null || pos.isBlank()) pos = homeStateCode;

            BigDecimal tax = money(r.get("tax_amount"));
            boolean interState = pos != null && homeStateCode != null && !pos.equals(homeStateCode);

            BigDecimal cgst = BigDecimal.ZERO, sgst = BigDecimal.ZERO, igst = BigDecimal.ZERO;
            if (interState) {
                igst = tax;
            } else {
                // Halve then subtract, so CGST + SGST is exactly the tax reported.
                sgst = tax.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                cgst = tax.subtract(sgst);
            }

            Object dateVal = r.get("invoice_date");
            java.time.LocalDate invoiceDate = null;
            if (dateVal instanceof java.sql.Date d) invoiceDate = d.toLocalDate();
            else if (dateVal instanceof java.time.LocalDate d) invoiceDate = d;

            Object sourceIdVal = r.get("source_id");
            supplies.add(new Supply(
                str(r.get("source")),
                sourceIdVal instanceof UUID u ? u : null,
                str(r.get("invoice_number")),
                invoiceDate,
                recipientGstin,
                pos,
                str(r.get("hsn_sac_code")),
                money(r.get("tax_rate")),
                money(r.get("taxable_value")),
                cgst, sgst, igst,
                Boolean.TRUE.equals(r.get("reverse_charge")),
                str(r.get("gst_treatment"))
            ));
        }
        return supplies;
    }

    /** Persistable snapshot of each supply, frozen against the live tables. */
    public List<GstFilingLineItem> toLineItems(List<Supply> supplies) {
        List<GstFilingLineItem> items = new ArrayList<>(supplies.size());
        for (Supply s : supplies) {
            GstFilingLineItem item = new GstFilingLineItem();
            item.setSource(s.source());
            item.setSourceId(s.sourceId());
            item.setInvoiceNumber(s.invoiceNumber());
            item.setInvoiceDate(s.invoiceDate());
            item.setRecipientGstin(s.recipientGstin());
            item.setPlaceOfSupply(s.placeOfSupply());
            item.setHsnSacCode(s.hsnSacCode());
            item.setTaxRate(s.taxRate());
            item.setTaxableValue(s.taxableValue());
            item.setCgst(s.cgst());
            item.setSgst(s.sgst());
            item.setIgst(s.igst());
            item.setReverseCharge(s.reverseCharge());
            item.setGstTreatment(s.gstTreatment());
            items.add(item);
        }
        return items;
    }

    /**
     * Everything that would stop this return being accepted (A5).
     *
     * <p>Reported as errors on the record rather than thrown, so the hospital gets the
     * whole list at once instead of fixing one problem per attempt.
     */
    public List<GstFilingError> validate(List<Supply> supplies, String gstin, String homeStateCode) {
        List<GstFilingError> errors = new ArrayList<>();

        if (gstin == null || gstin.isBlank()) {
            errors.add(error("GSTIN_MISSING", "gstin", null,
                "No GSTIN is configured for this hospital. A return cannot identify its filer without one."));
        }
        if (homeStateCode == null || homeStateCode.isBlank()) {
            errors.add(error("STATE_UNKNOWN", "place_of_supply", null,
                "The hospital's state could not be derived from its GSTIN, so no supply can be "
                + "attributed to a place of supply."));
        }

        // Unclassified services: reported with no tax and under no heading. Counting them
        // as exempt would be a tax position nobody has taken.
        long unclassified = supplies.stream()
            .filter(s -> "UNCLASSIFIED".equals(s.gstTreatment())).count();
        if (unclassified > 0) {
            errors.add(error("SERVICE_UNCLASSIFIED", "gst_treatment", null,
                unclassified + " service " + (unclassified == 1 ? "line is" : "lines are")
                + " not classified for GST. Set each service's treatment under Settings → Charge."));
        }

        // HSN codes that cannot be summarised at any level GST accepts.
        Set<String> badHsn = new LinkedHashSet<>();
        for (Supply s : supplies) {
            if ("PHARMACY".equals(s.source()) && !HsnCode.isValid(s.hsnSacCode())) {
                badHsn.add(s.hsnSacCode() == null ? "(blank)" : s.hsnSacCode());
            }
        }
        if (!badHsn.isEmpty()) {
            errors.add(error("HSN_INVALID", "hsn_sac_code", null,
                "Unreportable HSN codes: " + String.join(", ", badHsn)
                + ". Fix them under Settings → HSN Data Quality."));
        }

        return errors;
    }

    private GstFilingError error(String code, String field, String invoice, String message) {
        GstFilingError e = new GstFilingError();
        e.setErrorCode(code);
        e.setFieldName(field);
        e.setInvoiceNumber(invoice);
        e.setErrorMessage(message);
        return e;
    }

    /**
     * GSTR-1: outward supplies, split into registered recipients (b2b), unregistered
     * consumers aggregated by state and rate (b2cs), and the HSN summary.
     *
     * <p>Exempt, nil-rated and unclassified supplies carry no tax and belong in GSTR-3B's
     * exempt figures rather than in either taxable section here, so they are left out of
     * b2b and b2cs but still counted in the HSN summary, which reports turnover.
     */
    public String buildGstr1(List<Supply> supplies, String gstin, String returnPeriod) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("gstin", gstin == null ? "" : gstin);
        root.put("fp", returnPeriod);

        List<Supply> taxable = supplies.stream().filter(s -> !s.isExempt()).toList();

        // ── b2b: grouped by recipient, then by invoice ──
        Map<String, Map<String, List<Supply>>> b2bGrouped = new LinkedHashMap<>();
        for (Supply s : taxable) {
            if (!s.isB2b()) continue;
            b2bGrouped
                .computeIfAbsent(s.recipientGstin(), k -> new LinkedHashMap<>())
                .computeIfAbsent(s.invoiceNumber() == null ? "" : s.invoiceNumber(), k -> new ArrayList<>())
                .add(s);
        }
        ArrayNode b2b = root.putArray("b2b");
        b2bGrouped.forEach((ctin, invoices) -> {
            ObjectNode party = b2b.addObject();
            party.put("ctin", ctin);
            ArrayNode inv = party.putArray("inv");
            invoices.forEach((invoiceNo, lines) -> {
                Supply first = lines.get(0);
                ObjectNode node = inv.addObject();
                node.put("inum", invoiceNo);
                node.put("idt", first.invoiceDate() == null ? "" : formatGstDate(first.invoiceDate()));
                node.put("val", sum(lines, Supply::invoiceValue));
                node.put("pos", first.placeOfSupply());
                node.put("rchrg", first.reverseCharge() ? "Y" : "N");
                node.put("inv_typ", "R");
                ArrayNode itms = node.putArray("itms");
                int num = 1;
                for (Supply s : lines) {
                    ObjectNode itm = itms.addObject();
                    itm.put("num", num++);
                    ObjectNode det = itm.putObject("itm_det");
                    det.put("rt", s.taxRate());
                    det.put("txval", s.taxableValue());
                    det.put("camt", s.cgst());
                    det.put("samt", s.sgst());
                    det.put("iamt", s.igst());
                    det.put("csamt", BigDecimal.ZERO);
                }
            });
        });

        // ── b2cs: unregistered recipients, aggregated by state + rate ──
        Map<String, List<Supply>> b2csGrouped = new LinkedHashMap<>();
        for (Supply s : taxable) {
            if (s.isB2b()) continue;
            b2csGrouped.computeIfAbsent(s.placeOfSupply() + "|" + s.taxRate().toPlainString(),
                k -> new ArrayList<>()).add(s);
        }
        ArrayNode b2cs = root.putArray("b2cs");
        b2csGrouped.forEach((key, lines) -> {
            Supply first = lines.get(0);
            boolean inter = first.igst().signum() > 0;
            ObjectNode node = b2cs.addObject();
            node.put("sply_ty", inter ? "INTER" : "INTRA");
            node.put("pos", first.placeOfSupply());
            node.put("typ", "OE");
            node.put("rt", first.taxRate());
            node.put("txval", sum(lines, Supply::taxableValue));
            node.put("camt", sum(lines, Supply::cgst));
            node.put("samt", sum(lines, Supply::sgst));
            node.put("iamt", sum(lines, Supply::igst));
            node.put("csamt", BigDecimal.ZERO);
        });

        // ── HSN summary: all supplies, taxable or not ──
        Map<String, List<Supply>> hsnGrouped = new LinkedHashMap<>();
        for (Supply s : supplies) {
            String code = s.hsnSacCode() == null || s.hsnSacCode().isBlank() ? "" : s.hsnSacCode();
            hsnGrouped.computeIfAbsent(code + "|" + s.taxRate().toPlainString(),
                k -> new ArrayList<>()).add(s);
        }
        ObjectNode hsn = root.putObject("hsn");
        ArrayNode hsnData = hsn.putArray("data");
        int hsnNum = 1;
        for (List<Supply> lines : hsnGrouped.values()) {
            Supply first = lines.get(0);
            ObjectNode node = hsnData.addObject();
            node.put("num", hsnNum++);
            node.put("hsn_sc", first.hsnSacCode() == null ? "" : first.hsnSacCode());
            node.put("rt", first.taxRate());
            node.put("txval", sum(lines, Supply::taxableValue));
            node.put("camt", sum(lines, Supply::cgst));
            node.put("samt", sum(lines, Supply::sgst));
            node.put("iamt", sum(lines, Supply::igst));
            node.put("csamt", BigDecimal.ZERO);
        }

        return write(root);
    }

    /**
     * GSTR-3B: the summary return.
     *
     * <p>Exempt and nil-rated supplies are reported in {@code osup_nil_exmp}, separately
     * from taxable outward supplies — the distinction the {@code GstTreatment} enum
     * exists to preserve.
     */
    public String buildGstr3b(List<Supply> supplies, String gstin, String returnPeriod) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("gstin", gstin == null ? "" : gstin);
        root.put("ret_period", returnPeriod);

        List<Supply> taxable = supplies.stream().filter(s -> !s.isExempt()).toList();
        List<Supply> exempt  = supplies.stream().filter(Supply::isExempt).toList();

        ObjectNode supDetails = root.putObject("sup_details");
        ObjectNode outward = supDetails.putObject("osup_det");
        outward.put("txval", sum(taxable, Supply::taxableValue));
        outward.put("camt", sum(taxable, Supply::cgst));
        outward.put("samt", sum(taxable, Supply::sgst));
        outward.put("iamt", sum(taxable, Supply::igst));
        outward.put("csamt", BigDecimal.ZERO);

        ObjectNode nilExempt = supDetails.putObject("osup_nil_exmp");
        nilExempt.put("txval", sum(exempt, Supply::taxableValue));

        return write(root);
    }

    private BigDecimal sum(List<Supply> supplies, java.util.function.Function<Supply, BigDecimal> field) {
        return supplies.stream()
            .map(field)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    /** GST returns carry dates as dd-MM-yyyy. */
    private String formatGstDate(java.time.LocalDate date) {
        return date.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
    }

    private String write(ObjectNode node) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            throw new com.hms.exception.BusinessRuleViolationException(
                "Could not build the GST payload: " + e.getMessage());
        }
    }
}
