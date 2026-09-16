package com.hms.application.gst;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class GstPayloadBuilderTest {

    private GstPayloadBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new GstPayloadBuilder(new ObjectMapper());
    }

    @Test
    void toSupplies_WithForwardAndReturnRows_SplitsCorrectly() {
        List<Map<String, Object>> rows = new ArrayList<>();

        // Sale row: ₹105 net total, ₹5 tax (5% GST intra-state)
        Map<String, Object> saleRow = new HashMap<>();
        saleRow.put("source", "PHARMACY");
        saleRow.put("invoice_number", "AVPHSL-0001");
        saleRow.put("invoice_date", LocalDate.of(2026, 9, 16));
        saleRow.put("place_of_supply", "32");
        saleRow.put("hsn_sac_code", "3004");
        saleRow.put("tax_rate", 5.00);
        saleRow.put("taxable_value", 100.00);
        saleRow.put("tax_amount", 5.00);
        rows.add(saleRow);

        // Return row: -₹21 net total, -₹1 tax (5% GST intra-state)
        Map<String, Object> returnRow = new HashMap<>();
        returnRow.put("source", "PHARMACY");
        returnRow.put("invoice_number", "AVPHSL-0002");
        returnRow.put("invoice_date", LocalDate.of(2026, 9, 16));
        returnRow.put("place_of_supply", "32");
        returnRow.put("hsn_sac_code", "3004");
        returnRow.put("tax_rate", 5.00);
        returnRow.put("taxable_value", -20.00);
        returnRow.put("tax_amount", -1.00);
        rows.add(returnRow);

        List<GstPayloadBuilder.Supply> supplies = builder.toSupplies(rows, "32", Map.of());

        assertEquals(2, supplies.size());
        assertEquals(new BigDecimal("2.50"), supplies.get(0).sgst());
        assertEquals(new BigDecimal("2.50"), supplies.get(0).cgst());
        assertEquals(new BigDecimal("-0.50"), supplies.get(1).sgst());
        assertEquals(new BigDecimal("-0.50"), supplies.get(1).cgst());

        // Test GSTR-3B generation
        String gstr3b = builder.buildGstr3b(supplies, "32AAAAA0000A1Z5", "092026");
        assertNotNull(gstr3b);
        assertTrue(gstr3b.contains("80.00")); // Net taxable: 100 - 20 = 80
        assertTrue(gstr3b.contains("2.00"));  // Net CGST: 2.50 - 0.50 = 2.00
    }
}
