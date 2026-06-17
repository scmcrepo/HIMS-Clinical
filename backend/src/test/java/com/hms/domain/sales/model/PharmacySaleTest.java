package com.hms.domain.sales.model;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class PharmacySaleTest {

    @Test
    public void testRecalculateRoundsToNearestInteger() {
        PharmacySale sale = new PharmacySale();
        sale.setDiscountAmount(BigDecimal.ZERO);

        PharmacySaleLine line1 = new PharmacySaleLine();
        line1.setAmount(new BigDecimal("121.51"));
        sale.addLine(line1);

        PharmacySaleLine line2 = new PharmacySaleLine();
        line2.setAmount(new BigDecimal("121.51"));
        sale.addLine(line2);

        // Sum = 243.02 -> rounds to 243.00
        sale.recalculate();

        assertEquals(new BigDecimal("243.00"), sale.getTotalAmount());
    }

    @Test
    public void testRecalculateRoundsUpToNearestInteger() {
        PharmacySale sale = new PharmacySale();
        sale.setDiscountAmount(BigDecimal.ZERO);

        PharmacySaleLine line1 = new PharmacySaleLine();
        line1.setAmount(new BigDecimal("121.75"));
        sale.addLine(line1);

        PharmacySaleLine line2 = new PharmacySaleLine();
        line2.setAmount(new BigDecimal("121.75"));
        sale.addLine(line2);

        // Sum = 243.50 -> rounds to 244.00
        sale.recalculate();

        assertEquals(new BigDecimal("244.00"), sale.getTotalAmount());
    }
}
