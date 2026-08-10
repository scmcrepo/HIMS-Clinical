import { describe, expect, it } from 'vitest';

import {
  ClaimRow,
  PaymentAdvice,
  adviceIsSelfConsistent,
  computeMetrics,
  expectedNet,
  lifecycleIndex,
  needsAttention,
  reconcilesExactly,
  reconciliationGap,
  warrantsDispute,
} from './types';

function advice(over: Partial<PaymentAdvice> = {}): PaymentAdvice {
  return {
    id: 'a-1',
    utrNumber: 'NEFT-N123456789',
    paymentDate: '2026-08-01T10:00:00Z',
    grossAmount: 9_500_000, // ₹95,000
    tdsAmount: 0,
    deductionAmount: 0,
    netDisbursedAmount: 9_500_000,
    reconciled: true,
    bankCreditedAmount: 9_500_000,
    ...over,
  };
}

function claim(over: Partial<ClaimRow> = {}): ClaimRow {
  return {
    id: 'c-1',
    correlationId: 'corr-1',
    payerCode: 'star-health',
    financialState: 'AMOUNT_RECEIVED_IN_BANK',
    claimedAmount: 10_000_000, // ₹1,00,000
    approvedAmount: 9_500_000, // ₹95,000
    disallowedAmount: 500_000, // ₹5,000
    patientCopayAmount: 0,
    advices: [advice()],
    ...over,
  };
}

describe("the flow document's worked example", () => {
  it('claimed 1,00,000 / approved 95,000 / deducted 5,000 flows through the metrics', () => {
    const m = computeMetrics([claim()]);
    expect(m.totalClaimed).toBe(10_000_000);
    expect(m.totalApproved).toBe(9_500_000);
    expect(m.totalDisallowed).toBe(500_000);
    expect(m.totalReceivedInBank).toBe(9_500_000);
    expect(m.totalPendingDisbursal).toBe(0);
  });
});

describe('control tower metrics', () => {
  it('counts only reconciled advices as received', () => {
    // An insurer asserting it paid is not the hospital holding the money.
    const m = computeMetrics([
      claim({ advices: [advice({ reconciled: false, bankCreditedAmount: null })] }),
    ]);
    expect(m.totalReceivedInBank).toBe(0);
    expect(m.totalPendingDisbursal).toBe(9_000_000);
  });

  it('uses the bank figure, not the advised figure, when they differ', () => {
    const m = computeMetrics([
      claim({ advices: [advice({ netDisbursedAmount: 9_500_000, bankCreditedAmount: 9_400_000 })] }),
    ]);
    expect(m.totalReceivedInBank).toBe(9_400_000);
  });

  it('aggregates across many claims', () => {
    const m = computeMetrics([claim(), claim({ id: 'c-2' }), claim({ id: 'c-3' })]);
    expect(m.totalClaimed).toBe(30_000_000);
    expect(m.totalDisallowed).toBe(1_500_000);
  });

  it('treats missing amounts as zero rather than NaN', () => {
    const m = computeMetrics([
      claim({ claimedAmount: null, approvedAmount: null, disallowedAmount: null, advices: [] }),
    ]);
    expect(m.totalClaimed).toBe(0);
    expect(m.totalApproved).toBe(0);
    expect(Number.isNaN(m.totalPendingDisbursal)).toBe(false);
  });

  it('never reports negative pending disbursal', () => {
    // An over-credit is a reconciliation exception, not money owed to us.
    const m = computeMetrics([
      claim({ approvedAmount: 1_000_000, advices: [advice({ bankCreditedAmount: 5_000_000 })] }),
    ]);
    expect(m.totalPendingDisbursal).toBe(0);
  });

  it('returns zeroes for an empty tower', () => {
    expect(computeMetrics([])).toEqual({
      totalClaimed: 0,
      totalApproved: 0,
      totalReceivedInBank: 0,
      totalPendingDisbursal: 0,
      totalDisallowed: 0,
    });
  });
});

describe('reconciliation', () => {
  it('matches when the bank credit equals the advice', () => {
    expect(reconcilesExactly(advice())).toBe(true);
    expect(reconciliationGap(advice())).toBe(0);
  });

  it('does not absorb a one-rupee shortfall', () => {
    const a = advice({ bankCreditedAmount: 9_499_900 });
    expect(reconcilesExactly(a)).toBe(false);
    expect(reconciliationGap(a)).toBe(100);
  });

  it('reports an over-credit as a negative gap', () => {
    expect(reconciliationGap(advice({ bankCreditedAmount: 9_600_000 }))).toBe(-100_000);
  });

  it('has no gap before the bank line is recorded', () => {
    expect(reconciliationGap(advice({ bankCreditedAmount: null }))).toBeNull();
    expect(reconcilesExactly(advice({ bankCreditedAmount: null }))).toBe(false);
  });
});

describe("the payer's own arithmetic", () => {
  it('accepts a consistent advice with TDS', () => {
    const a = advice({
      grossAmount: 9_500_000,
      tdsAmount: 500_000,
      deductionAmount: 0,
      netDisbursedAmount: 9_000_000,
    });
    expect(expectedNet(a)).toBe(9_000_000);
    expect(adviceIsSelfConsistent(a)).toBe(true);
  });

  it('flags an advice whose components do not add up', () => {
    // Payers really do send these; catching it here saves an accountant a day.
    const a = advice({
      grossAmount: 9_500_000,
      tdsAmount: 500_000,
      deductionAmount: 0,
      netDisbursedAmount: 9_500_000,
    });
    expect(adviceIsSelfConsistent(a)).toBe(false);
  });

  it('never computes a negative expected net', () => {
    expect(expectedNet(advice({ grossAmount: 100, tdsAmount: 5_000, deductionAmount: 0 }))).toBe(0);
  });
});

describe('dispute rule', () => {
  it('a disallowance warrants a dispute', () => {
    expect(warrantsDispute(claim({ disallowedAmount: 500_000 }))).toBe(true);
  });

  it('TDS alone does not', () => {
    expect(
      warrantsDispute(
        claim({
          disallowedAmount: 0,
          advices: [advice({ tdsAmount: 500_000, netDisbursedAmount: 9_000_000 })],
        }),
      ),
    ).toBe(false);
  });
});

describe('attention queue', () => {
  it('picks up disputed claims', () => {
    const rows = needsAttention([claim({ financialState: 'CLAIM_DISPUTED' })]);
    expect(rows).toHaveLength(1);
  });

  it('picks up unreconciled advices', () => {
    expect(
      needsAttention([claim({ advices: [advice({ reconciled: false, bankCreditedAmount: null })] })]),
    ).toHaveLength(1);
  });

  it('picks up internally inconsistent advices', () => {
    expect(
      needsAttention([claim({ advices: [advice({ tdsAmount: 500_000 })] })]),
    ).toHaveLength(1);
  });

  it('leaves a cleanly settled claim alone', () => {
    expect(needsAttention([claim()])).toHaveLength(0);
  });
});

describe('lifecycle ordering', () => {
  it('orders the happy path', () => {
    expect(lifecycleIndex('CLAIM_SUBMITTED')).toBe(0);
    expect(lifecycleIndex('CLAIM_APPROVED')).toBe(1);
    expect(lifecycleIndex('PAYMENT_INITIATED')).toBe(2);
    expect(lifecycleIndex('AMOUNT_RECEIVED_IN_BANK')).toBe(3);
  });

  it('places a dispute off the happy path', () => {
    expect(lifecycleIndex('CLAIM_DISPUTED')).toBe(-1);
  });
});
