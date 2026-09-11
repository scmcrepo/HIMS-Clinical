package com.hms.application.report.modules;

import com.hms.application.report.util.ReportScope;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Raw data for the GST reports: outward pharmacy sales, inward purchases, and the
 * summaries built on top of them.
 *
 * <p>The two sides of the ledger treat tax differently, and both conventions are
 * fixed by how the application already bills:
 *
 * <ul>
 *   <li><b>Sales are tax-INCLUSIVE.</b> The pharmacy billing engine computes
 *       {@code GST = net * rate / (100 + rate)}, so the price charged already
 *       contains the tax and it is stripped back out here.</li>
 *   <li><b>Purchases are tax-EXCLUSIVE.</b> {@code purchase_receipt_lines.purchase_rate}
 *       holds the pre-tax price and tax is added on top — see V109.</li>
 * </ul>
 *
 * <p>Neither {@code pharmacy_sale_lines} nor {@code purchase_receipt_lines} stores a
 * tax split, so CGST/SGST are derived as half of GST each, matching the default
 * split the billing screen applies when a tax has no configured components.
 *
 * <p>The sales detail report and the two summaries are all built on the same
 * {@link #SALES_LINES_HEAD}/{@link #SALES_TAXED_TAIL} chain rather than on queries of
 * their own. A summary that does not foot to its own detail is worse than no summary,
 * and separate SQL is how that drift starts.
 */
@Service
@RequiredArgsConstructor
public class GstReportDataService {

    private final JdbcTemplate jdbcTemplate;
    private final ReportScope scope;

    // ── Shared sales chain ──────────────────────────────────────────────────────
    // Takes two positional args (from date, to date). Callers append any further
    // filters, then the tenant/branch predicate, then SALES_TAXED_TAIL.

    private static final String SALES_LINES_HEAD = """
        WITH sale_lines AS (
            SELECT
                ps.id                                        AS sale_id,
                ps.sale_date                                 AS sale_date,
                ps.created_at                                AS entered_at,
                ps.sequence_number                           AS bill_no,
                COALESCE(ps.discount_amount, 0)              AS bill_discount,
                COALESCE(pat.first_name || ' ' || pat.last_name, ps.customer_name) AS patient_name,
                sn_pat.value                                 AS patient_id,
                ii.id                                        AS item_id,
                ii.name                                      AS item_name,
                ii.hsn_code                                  AS hsn_code,
                COALESCE(ii.tax_rate, 0)                     AS tax_rate,
                psl.quantity                                 AS units,
                psl.unit_rate                                AS mrp,
                COALESCE(ib.purchase_rate, 0)                AS purchase_rate,
                ps.place_of_supply                           AS place_of_supply,
                ps.recipient_gstin                           AS recipient_gstin,
                ps.reverse_charge                            AS reverse_charge,
                ps.id                                        AS source_id,
                COALESCE(psl.discount_amount, 0)             AS line_discount,
                (psl.amount - COALESCE(psl.discount_amount, 0)) AS line_net,
                SUM(psl.amount - COALESCE(psl.discount_amount, 0))
                    OVER (PARTITION BY ps.id)                AS sale_subtotal
            FROM pharmacy_sales ps
            JOIN pharmacy_sale_lines psl   ON ps.id = psl.sale_id
            JOIN inventory_batches ib      ON psl.inventory_batch_id = ib.id
            JOIN inventory_items ii        ON ib.item_id = ii.id
            LEFT JOIN patients pat         ON ps.patient_id = pat.id
            LEFT JOIN number_sequences sn_pat ON pat.id = sn_pat.id
            WHERE ps.sale_date BETWEEN ?::DATE AND ?::DATE
              AND ps.status <> 2
              AND ps.sale_status <> 0
        """;

    private static final String SALES_TAXED_TAIL = """
        ),
        apportioned AS (
            SELECT
                sl.*,
                -- Bill-level discount spread proportionally, as the billing screen does.
                -- COALESCE guards a fully-discounted bill, where the subtotal is zero
                -- and there is nothing to apportion against.
                ROUND(
                    sl.line_net - COALESCE(sl.bill_discount * sl.line_net / NULLIF(sl.sale_subtotal, 0), 0)
                , 2) AS total_sales_value
            FROM sale_lines sl
        ),
        taxed AS (
            SELECT
                a.*,
                -- Tax is inclusive: strip it back out rather than adding it on.
                ROUND(a.total_sales_value * a.tax_rate / (100 + a.tax_rate), 2) AS gst
            FROM apportioned a
        )
        """;

    /**
     * A code that is not 4, 6 or 8 digits cannot be reported at any HSN level GST
     * accepts, so it is bucketed rather than silently mixed in with real codes.
     */
    private static final String HSN_BUCKET =
        "CASE WHEN t.hsn_code ~ '^([0-9]{4}|[0-9]{6}|[0-9]{8})$' THEN t.hsn_code ELSE 'Unclassified' END";

    /**
     * Outward supplies — one row per line item per pharmacy bill.
     *
     * <p>Line values mirror {@code PharmacySale.recalculate()}:
     * {@code total = Σ(line.amount − line.discount) − bill.discount}. The bill-level
     * discount is spread across lines in proportion to each line's net, which is what
     * the billing screen does before computing tax.
     *
     * <p>Drafts ({@code sale_status = 0}) and soft-deleted rows are excluded — neither
     * is a supply for GST purposes.
     */
    public List<Map<String, Object>> getPharmacySalesGstDetailed(String fromDate, String toDate, String hsnCode) {
        StringBuilder sql = new StringBuilder(SALES_LINES_HEAD);
        List<Object> args = new ArrayList<>(List.of(fromDate, toDate));
        if (hsnCode != null && !hsnCode.isBlank()) {
            sql.append(" AND ii.hsn_code = ? ");
            args.add(hsnCode.trim());
        }
        sql.append(scope.predicate("ps")); args.addAll(scope.args());
        sql.append(SALES_TAXED_TAIL);
        sql.append("""
            SELECT
                to_char(t.sale_date + t.entered_at::time, 'DD-MM-YYYY HH24:MI:SS') AS bill_date,
                t.patient_name,
                t.patient_id,
                t.bill_no,
                t.item_name,
                t.hsn_code,
                t.units,
                ROUND(t.mrp, 2)                                        AS mrp,
                ROUND(t.line_discount
                      + COALESCE(t.bill_discount * t.line_net / NULLIF(t.sale_subtotal, 0), 0), 2) AS discount,
                t.total_sales_value,
                ROUND(t.purchase_rate * t.units, 2)                    AS total_purchase_value,
                t.tax_rate,
                -- Derived by subtraction from the rounded GST, not rounded independently,
                -- so the row adds up exactly: excl + GST = total, and SGST + CGST = GST.
                -- Independent rounding drifts by a paisa and a tax return that does not
                -- foot is worse than one that is a paisa off the true fraction.
                (t.total_sales_value - t.gst)                          AS sale_excluding_tax,
                t.gst,
                ROUND(t.gst / 2, 2)                                    AS sgst,
                (t.gst - ROUND(t.gst / 2, 2))                          AS cgst
            FROM taxed t
            ORDER BY t.sale_date, t.entered_at, t.bill_no, t.item_name
            """);
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql.toString(), args.toArray());
    }

    /**
     * HSN-wise tax summary — outward supplies grouped by HSN code and rate.
     *
     * <p>Codes outside GST's 4/6/8-digit levels collapse into a single "Unclassified"
     * row (P1.5). Dropping them would understate the period's tax; folding them into a
     * neighbouring HSN would misreport it. A visible bucket does neither, and tells the
     * pharmacy team exactly how much turnover is waiting on the HSN cleanup.
     */
    public List<Map<String, Object>> getHsnTaxSummary(String fromDate, String toDate) {
        StringBuilder sql = new StringBuilder(SALES_LINES_HEAD);
        List<Object> args = new ArrayList<>(List.of(fromDate, toDate));
        sql.append(scope.predicate("ps")); args.addAll(scope.args());
        sql.append(SALES_TAXED_TAIL);
        sql.append("""
            SELECT
                %s                                          AS hsn_code,
                t.tax_rate,
                COUNT(DISTINCT t.item_id)                   AS items,
                SUM(t.units)                                AS quantity,
                SUM(t.total_sales_value - t.gst)            AS taxable_value,
                SUM(t.gst)                                  AS gst,
                SUM(ROUND(t.gst / 2, 2))                    AS sgst,
                (SUM(t.gst) - SUM(ROUND(t.gst / 2, 2)))     AS cgst,
                SUM(t.total_sales_value)                    AS total_value
            FROM taxed t
            GROUP BY %s, t.tax_rate
            ORDER BY SUM(t.total_sales_value) DESC
            """.formatted(HSN_BUCKET, HSN_BUCKET));
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql.toString(), args.toArray());
    }

    /**
     * Tax liability — output tax on sales against input tax on purchases, by rate.
     *
     * <p>A full outer join rather than an inner one: a rate can appear on only one side
     * of the ledger in a period, and dropping those rows would silently understate
     * either the liability or the credit available against it.
     *
     * <p>This is an indicative working figure, not a return. It covers pharmacy sales
     * and goods received only — OP/IP services are not yet classified for GST — so it
     * cannot be filed as-is.
     */
    public List<Map<String, Object>> getGstTaxLiability(String fromDate, String toDate) {
        StringBuilder sql = new StringBuilder(SALES_LINES_HEAD);
        List<Object> args = new ArrayList<>(List.of(fromDate, toDate));
        sql.append(scope.predicate("ps")); args.addAll(scope.args());
        sql.append(SALES_TAXED_TAIL);
        sql.append("""
            , service_lines AS (
            """);
        sql.append(SERVICE_LINES_HEAD);
        args.add(fromDate); args.add(toDate);
        sql.append(scope.predicate("b")); args.addAll(scope.args());
        sql.append("""
            ),
            output_by_rate AS (
                -- Pharmacy and taxable services together. Services contribute nothing
                -- until they are classified TAXABLE, so this figure is correct today
                -- and stays correct the moment the hospital classifies them — without
                -- needing this query changed again.
                SELECT tax_rate, SUM(taxable) AS taxable, SUM(tax) AS tax FROM (
                    SELECT
                        t.tax_rate                          AS tax_rate,
                        SUM(t.total_sales_value - t.gst)    AS taxable,
                        SUM(t.gst)                          AS tax
                    FROM taxed t
                    GROUP BY t.tax_rate
                    UNION ALL
                    SELECT
                        sl.tax_rate                                                        AS tax_rate,
                        SUM(sl.total_value - ROUND(sl.total_value * sl.tax_rate / (100 + sl.tax_rate), 2)) AS taxable,
                        SUM(ROUND(sl.total_value * sl.tax_rate / (100 + sl.tax_rate), 2))  AS tax
                    FROM service_lines sl
                    WHERE sl.tax_rate > 0
                    GROUP BY sl.tax_rate
                ) combined_output
                GROUP BY tax_rate
            ),
            input_by_rate AS (
                SELECT
                    COALESCE(prl.tax_rate, 0)                                   AS tax_rate,
                    SUM(prl.quantity * prl.purchase_rate)                       AS taxable,
                    SUM(prl.quantity * prl.purchase_rate
                        * COALESCE(prl.tax_rate, 0) / 100.0)                    AS tax
                FROM purchase_receipts pr
                JOIN purchase_receipt_lines prl ON pr.id = prl.receipt_id
                WHERE pr.receipt_date BETWEEN ?::DATE AND ?::DATE
                  AND pr.status <> 2
            """);
        args.add(fromDate); args.add(toDate);
        sql.append(scope.predicate("pr")); args.addAll(scope.args());
        sql.append("""
                GROUP BY COALESCE(prl.tax_rate, 0)
            )
            SELECT
                COALESCE(o.tax_rate, i.tax_rate)            AS tax_rate,
                ROUND(COALESCE(o.taxable, 0), 2)            AS output_taxable,
                ROUND(COALESCE(o.tax, 0), 2)                AS output_tax,
                ROUND(COALESCE(i.taxable, 0), 2)            AS input_taxable,
                ROUND(COALESCE(i.tax, 0), 2)                AS input_tax,
                ROUND(COALESCE(o.tax, 0) - COALESCE(i.tax, 0), 2) AS net_liability
            FROM output_by_rate o
            FULL OUTER JOIN input_by_rate i ON o.tax_rate = i.tax_rate
            ORDER BY COALESCE(o.tax_rate, i.tax_rate)
            """);
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql.toString(), args.toArray());
    }

    // ── OP/IP services (B-3) ────────────────────────────────────────────────────

    /**
     * Service lines on OP and IP bills, excluding anything already counted elsewhere.
     *
     * <p>Two exclusions matter more than they look:
     * <ul>
     *   <li><b>{@code pharmacy_sale_id IS NULL}</b> — a pharmacy sale settled through
     *       "Add to Bill" is posted onto the patient's IP bill as a consolidated charge
     *       line. Those rupees are already reported from {@code pharmacy_sales} by the
     *       Pharmacy Sales GST report, so counting them here too would double them in
     *       both the sheets and the liability figure.</li>
     *   <li><b>Cancelled and draft bills</b> — neither is a supply.</li>
     * </ul>
     */
    private static final String SERVICE_LINES_HEAD = """
        SELECT
            b.bill_date                                  AS bill_date_raw,
            b.encounter_type                             AS encounter_type_raw,
            COALESCE(pat.first_name || ' ' || pat.last_name, '') AS patient_name,
            sn_pat.value                                 AS patient_id,
            sn_b.value                                   AS bill_no,
            sci.name                                     AS service_name,
            sci.sac_code                                 AS sac_code,
            sci.gst_treatment                            AS gst_treatment,
            b.place_of_supply                            AS place_of_supply,
            b.recipient_gstin                            AS recipient_gstin,
            b.payor_id                                   AS payor_id,
            b.reverse_charge                             AS reverse_charge,
            b.id                                         AS source_id,
            cli.quantity                                 AS units,
            (cli.unit_rate / 100.0)                      AS rate,
            (cli.discount_amount / 100.0)                AS discount,
            ROUND((cli.amount - cli.discount_amount) / 100.0, 2) AS total_value,
            -- A rate only applies to a supply actually ruled taxable. An UNCLASSIFIED
            -- service carries no tax here even if a rate was typed against it.
            CASE WHEN sci.gst_treatment = 'TAXABLE'
                 THEN COALESCE(sci.tax_rate, 0) ELSE 0 END AS tax_rate
        FROM bills b
        JOIN charge_line_items cli     ON cli.bill_id = b.id
        JOIN service_catalog_items sci ON cli.service_catalog_item_id = sci.id
        LEFT JOIN patients pat         ON b.patient_id = pat.id
        LEFT JOIN number_sequences sn_pat ON pat.id = sn_pat.id
        LEFT JOIN number_sequences sn_b   ON b.id = sn_b.id
        WHERE b.bill_date BETWEEN ?::DATE AND ?::DATE
          AND b.status <> 2
          AND b.bill_status NOT IN (0, 4)
          AND cli.status <> 2
          AND (cli.line_status IS NULL OR cli.line_status <> 1)
          AND cli.pharmacy_sale_id IS NULL
          AND cli.pharmacy_return_id IS NULL
        """;

    /**
     * Outward supplies — one row per service line on an OP or IP bill (B-3).
     *
     * <p><b>Prices are treated as tax-inclusive</b>, matching pharmacy and the invoice
     * printer. Services carried no tax at all before this, so nothing in the data settles
     * the question — this follows the house convention, and it is the assumption to
     * confirm with the hospital's tax advisor alongside P2.3.
     *
     * <p>Exempt, nil-rated and unclassified lines report their full value with zero tax,
     * under their own treatment heading rather than as taxable value.
     */
    public List<Map<String, Object>> getServiceGstDetailed(String fromDate, String toDate, String encounterType) {
        StringBuilder sql = new StringBuilder("WITH service_lines AS (\n");
        sql.append(SERVICE_LINES_HEAD);
        List<Object> args = new ArrayList<>(List.of(fromDate, toDate));
        if ("OP".equalsIgnoreCase(encounterType)) {
            sql.append(" AND b.encounter_type = 0 ");
        } else if ("IP".equalsIgnoreCase(encounterType)) {
            sql.append(" AND b.encounter_type = 1 ");
        }
        sql.append(scope.predicate("b")); args.addAll(scope.args());
        sql.append("""
            ),
            taxed AS (
                SELECT
                    sl.*,
                    ROUND(sl.total_value * sl.tax_rate / (100 + sl.tax_rate), 2) AS gst
                FROM service_lines sl
            )
            SELECT
                to_char(t.bill_date_raw, 'DD-MM-YYYY')  AS bill_date,
                CASE t.encounter_type_raw WHEN 0 THEN 'OP' WHEN 1 THEN 'IP'
                     ELSE t.encounter_type_raw::text END AS encounter_type,
                t.patient_name,
                t.patient_id,
                t.bill_no,
                t.service_name,
                t.sac_code,
                t.gst_treatment,
                t.units,
                ROUND(t.rate, 2)                        AS rate,
                ROUND(t.discount, 2)                    AS discount,
                t.total_value,
                t.tax_rate,
                (t.total_value - t.gst)                 AS value_excluding_tax,
                t.gst,
                ROUND(t.gst / 2, 2)                     AS sgst,
                (t.gst - ROUND(t.gst / 2, 2))           AS cgst
            FROM taxed t
            ORDER BY t.bill_date_raw, t.bill_no, t.service_name
            """);
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql.toString(), args.toArray());
    }

    // ── Filing (Module A) ───────────────────────────────────────────────────────

    /**
     * Every outward supply in a period, in the shape a return needs (A2).
     *
     * <p>Built on the same two chains the reports use, deliberately. A return that does
     * not foot to the reports the hospital has already looked at is a return nobody can
     * defend, and the only way to guarantee it foots is to derive both from one query.
     *
     * <p>Returns the total tax per line rather than a CGST/SGST split. Whether a supply
     * is intra-state (CGST + SGST) or inter-state (IGST) depends on the place of supply
     * against the hospital's own state, and the hospital's state lives in configuration,
     * not in the database rows — so the split is applied in the payload builder where
     * both are known.
     */
    public List<Map<String, Object>> getFilingLines(String fromDate, String toDate) {
        StringBuilder sql = new StringBuilder(SALES_LINES_HEAD);
        List<Object> args = new ArrayList<>(List.of(fromDate, toDate));
        sql.append(scope.predicate("ps")); args.addAll(scope.args());
        sql.append(SALES_TAXED_TAIL);
        sql.append("""
            , service_lines AS (
            """);
        sql.append(SERVICE_LINES_HEAD);
        args.add(fromDate); args.add(toDate);
        sql.append(scope.predicate("b")); args.addAll(scope.args());
        sql.append("""
            )
            SELECT
                'PHARMACY'                              AS source,
                t.source_id                             AS source_id,
                t.bill_no                               AS invoice_number,
                t.sale_date                             AS invoice_date,
                t.recipient_gstin                       AS recipient_gstin,
                CAST(NULL AS UUID)                      AS payor_id,
                t.place_of_supply                       AS place_of_supply,
                t.hsn_code                              AS hsn_sac_code,
                t.tax_rate                              AS tax_rate,
                SUM(t.total_sales_value - t.gst)        AS taxable_value,
                SUM(t.gst)                              AS tax_amount,
                COALESCE(t.reverse_charge, FALSE)       AS reverse_charge,
                NULL                                    AS gst_treatment
            FROM taxed t
            GROUP BY t.source_id, t.bill_no, t.sale_date, t.recipient_gstin,
                     t.place_of_supply, t.hsn_code, t.tax_rate, t.reverse_charge

            UNION ALL

            SELECT
                'SERVICE'                               AS source,
                sl.source_id                            AS source_id,
                sl.bill_no                              AS invoice_number,
                sl.bill_date_raw                        AS invoice_date,
                sl.recipient_gstin                      AS recipient_gstin,
                sl.payor_id                             AS payor_id,
                sl.place_of_supply                      AS place_of_supply,
                sl.sac_code                             AS hsn_sac_code,
                sl.tax_rate                             AS tax_rate,
                SUM(sl.total_value
                    - ROUND(sl.total_value * sl.tax_rate / (100 + sl.tax_rate), 2)) AS taxable_value,
                SUM(ROUND(sl.total_value * sl.tax_rate / (100 + sl.tax_rate), 2))   AS tax_amount,
                COALESCE(sl.reverse_charge, FALSE)      AS reverse_charge,
                sl.gst_treatment                        AS gst_treatment
            FROM service_lines sl
            GROUP BY sl.source_id, sl.bill_no, sl.bill_date_raw, sl.recipient_gstin,
                     sl.payor_id, sl.place_of_supply, sl.sac_code, sl.tax_rate,
                     sl.reverse_charge, sl.gst_treatment
            -- Named, not positional: inserting payor_id shifted the old "ORDER BY 4, 3, 7"
            -- onto a different column without any error.
            ORDER BY invoice_date, invoice_number, hsn_sac_code
            """);
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql.toString(), args.toArray());
    }

    /**
     * Inward supplies — one row per GRN <em>per tax rate</em>.
     *
     * <p>The reference template carries a single rate per GRN, which only holds when every
     * line on that GRN is taxed alike. Grouping by rate keeps the rate column meaningful:
     * a single-rate GRN still yields exactly one row, and a mixed-rate GRN yields one row
     * per rate instead of a blended figure that reconciles to nothing.
     */
    public List<Map<String, Object>> getPurchaseGstDetails(String fromDate, String toDate, String supplierId) {
        StringBuilder sql = new StringBuilder("""
            SELECT
                s.name                                      AS supplier_name,
                to_char(pr.receipt_date, 'DD-MM-YYYY')      AS grn_date,
                pr.sequence_number                          AS grn_no,
                pr.invoice_number                           AS invoice_no,
                to_char(pr.invoice_date, 'DD-MM-YYYY')      AS invoice_date,
                COALESCE(prl.tax_rate, 0)                   AS tax_rate,
                -- purchase_rate is pre-tax (V109), so this is the taxable value.
                ROUND(SUM(prl.quantity * prl.purchase_rate), 2) AS purchase_value,
                ROUND(SUM(prl.quantity * prl.purchase_rate * COALESCE(prl.tax_rate, 0) / 100.0), 2) AS purchase_tax,
                -- Sum of the two rounded components rather than a separately rounded
                -- gross, so PURCHASE VALUE + PURCHASE TAX = NET VALUE holds on every row.
                ROUND(SUM(prl.quantity * prl.purchase_rate), 2)
                    + ROUND(SUM(prl.quantity * prl.purchase_rate * COALESCE(prl.tax_rate, 0) / 100.0), 2) AS net_value
            FROM purchase_receipts pr
            JOIN purchase_receipt_lines prl ON pr.id = prl.receipt_id
            LEFT JOIN suppliers s           ON pr.supplier_id = s.id
            WHERE pr.receipt_date BETWEEN ?::DATE AND ?::DATE
              AND pr.status <> 2
            """);
        List<Object> args = new ArrayList<>(List.of(fromDate, toDate));
        if (supplierId != null && !supplierId.isBlank()) {
            sql.append(" AND pr.supplier_id = ?::UUID ");
            args.add(supplierId.trim());
        }
        sql.append(scope.predicate("pr")); args.addAll(scope.args());
        sql.append("""
             GROUP BY pr.id, pr.sequence_number, pr.receipt_date, pr.invoice_number,
                      pr.invoice_date, s.name, COALESCE(prl.tax_rate, 0)
             ORDER BY pr.receipt_date DESC, pr.sequence_number
            """);
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql.toString(), args.toArray());
    }
}
