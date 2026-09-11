package com.hms.api.item;

import com.hms.api.shared.ApiResponse;
import com.hms.domain.inventory.model.HsnCode;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.infrastructure.persistence.item.ItemJpaRepository;
import com.hms.infrastructure.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * HSN data quality — profiling and correction for {@code inventory_items.hsn_code}.
 *
 * <p>GST groups outward supplies by HSN at 4, 6 or 8 digits, so a code of any other
 * shape cannot be summarised and lands in the GST report's "Unclassified" bucket. The
 * item master accumulated malformed codes for as long as the column was unvalidated
 * free text; that intake is now closed, and this is the tooling for the backlog.
 *
 * <p>The division of labour is deliberate. The application finds the bad codes, counts
 * what they affect, and offers a correction where one is mechanically safe. Deciding
 * that a given product really is HSN 9018 is a domain judgement with tax consequences,
 * so nothing here changes a code without someone choosing it.
 *
 * <p>Gated on {@code SETTINGS_ITEM}: the people who maintain the item master are the
 * ones who own its HSN codes.
 */
@RestController
@RequestMapping("/item/hsn-quality")
@RequiredArgsConstructor
@Transactional(readOnly = true)
@PreAuthorize("hasPermission('SETTINGS_ITEM','')")
public class HsnQualityController {

    private final ItemJpaRepository itemRepo;

    /** One distinct code, what it affects, and a correction where one is safe to offer. */
    public record HsnCodeRow(String hsnCode, long itemCount, boolean valid, String suggestion) {}

    /**
     * The profile that says whether this is an afternoon's work or a project (P1.2).
     * Codes repeat heavily across an item master, so the distinct-invalid count is
     * normally far smaller — and far less daunting — than the affected-item count.
     */
    public record HsnProfile(
        long totalItems,
        long itemsWithCode,
        long itemsBlank,
        long itemsValid,
        long itemsInvalid,
        int distinctInvalidCodes,
        int suggestionsAvailable,
        List<HsnCodeRow> invalidCodes
    ) {}

    /** GET /item/hsn-quality */
    @GetMapping
    public ResponseEntity<ApiResponse<HsnProfile>> profile() {
        long total = 0, withCode = 0, blank = 0, valid = 0, invalid = 0;
        int suggestions = 0;
        List<HsnCodeRow> invalidRows = new ArrayList<>();

        for (Object[] row : itemRepo.countItemsByHsnCode()) {
            String code = row[0] == null ? "" : row[0].toString();
            long count = ((Number) row[1]).longValue();
            total += count;

            if (code.isBlank()) {
                blank += count;
                continue;
            }
            withCode += count;

            if (HsnCode.isValid(code)) {
                valid += count;
            } else {
                invalid += count;
                String suggestion = HsnCode.suggest(code);
                if (suggestion != null) suggestions++;
                invalidRows.add(new HsnCodeRow(code, count, false, suggestion));
            }
        }

        // Worst offenders first: fixing the code on the most items removes the most
        // rows from the Unclassified bucket per decision made.
        invalidRows.sort(Comparator.comparingLong(HsnCodeRow::itemCount).reversed());

        return ResponseEntity.ok(ApiResponse.ok("OK", new HsnProfile(
            total, withCode, blank, valid, invalid,
            invalidRows.size(), suggestions, invalidRows)));
    }

    /** GET /item/hsn-quality/items?code= — which items carry one code. */
    @GetMapping("/items")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> itemsForCode(
            @RequestParam(name = "code", defaultValue = "") String code) {
        List<Map<String, Object>> items = itemRepo.findByHsnCode(code).stream()
            .map(i -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id",       i.getId());
                m.put("name",     i.getName());
                m.put("hsnCode",  i.getHsnCode());
                m.put("taxRate",  i.getTaxRate());
                return m;
            })
            .toList();
        return ResponseEntity.ok(ApiResponse.ok("OK", items));
    }

    public record RemapRequest(String from, String to) {}

    /**
     * POST /item/hsn-quality/remap — repoint every item on one code to another.
     *
     * <p>Applied per code rather than per item because a bad code is almost always
     * shared by a group of related products, and correcting them one at a time invites
     * a half-finished job that leaves the summary still wrong.
     */
    @PostMapping("/remap")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> remap(@RequestBody RemapRequest req) {
        if (req == null || req.from() == null || req.from().isBlank()) {
            throw new BusinessRuleViolationException("The code being replaced is required");
        }
        String to = req.to() == null ? "" : req.to().trim();
        if (to.isBlank()) {
            throw new BusinessRuleViolationException("A replacement HSN code is required");
        }
        // The corrected code is held to the same rule as any other write. Correcting one
        // malformed code into another would quietly keep the item unreportable.
        HsnCode.requireValid(to);

        UUID tenantId = TenantContext.require();
        int updated = itemRepo.remapHsnCode(req.from().trim(), to, tenantId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from",         req.from().trim());
        result.put("to",           to);
        result.put("itemsUpdated", updated);
        return ResponseEntity.ok(ApiResponse.ok(
            updated + (updated == 1 ? " item updated" : " items updated"), result));
    }
}
