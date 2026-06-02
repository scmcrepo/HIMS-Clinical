package com.hms.domain.billing.service;

import com.hms.domain.billing.model.ChargeLineItem;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * Calculates bed charges for inpatient bills.
 *
 * Mirrors legacy BillBO.calculateBedCharge():
 *   - Bed charge lines start with quantity=0 (running/open charge)
 *   - On getBill() and generateBill(): quantity is computed from fromDate to toDate
 *   - If toDate is null (patient still in bed): toDate = now
 *   - quantity = ceil((toDate - fromDate) / 86_400_000ms) days
 *   - amount = quantity × unitRate
 *
 * For HOURLY bed types (BillingCycle.HOURLY): quantity = hours, not days.
 */
@UtilityClass
public class BedChargeCalculator {

    /**
     * Computes quantities and amounts for all open bed charge lines on a bill.
     * Call this on every getBill() for Draft IP bills, and at generateBill() time.
     *
     * @param bedChargeLines charge lines where bedChargeFrom != null (bed charges only)
     * @param dischargeAt    discharge time if known; null uses Instant.now()
     * @param billingCycleDays true=DAILY (ceil days), false=HOURLY (ceil hours)
     * @return total bed charge amount across all lines
     */
    public long computeBedCharges(List<ChargeLineItem> bedChargeLines,
                                   Instant dischargeAt,
                                   boolean billingCycleDays) {
        // Sort by fromDate ASC — same as legacy
        bedChargeLines.sort(Comparator.comparing(ChargeLineItem::getBedChargeFrom,
            Comparator.nullsLast(Comparator.naturalOrder())));

        long total = 0L;

        for (ChargeLineItem line : bedChargeLines) {
            if (line.getBedChargeFrom() == null) continue;

            // Only compute for open (quantity=0) lines
            if (line.getQuantity() != 0) {
                total += line.getAmount();
                continue;
            }

            Instant from = line.getBedChargeFrom();
            Instant to   = (line.getBedChargeTo() != null)
                ? line.getBedChargeTo()
                : (dischargeAt != null ? dischargeAt : Instant.now());

            long quantity;
            if (billingCycleDays) {
                // DAILY: ceil days — minimum 1
                long millis = to.toEpochMilli() - from.toEpochMilli();
                quantity = Math.max(1L, (long) Math.ceil(millis / 86_400_000.0));
            } else {
                // HOURLY: ceil hours — minimum 1
                long hours = ChronoUnit.HOURS.between(from, to);
                quantity = Math.max(1L, hours);
            }

            long amount = quantity * line.getUnitRate();
            line.setQuantity((int) Math.min(quantity, Integer.MAX_VALUE));
            line.setAmount(amount);
            total += amount;
        }

        return total;
    }
}
