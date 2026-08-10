import { describe, expect, it } from 'vitest';

import {
  PolicyCoverage,
  admissibleWithoutOverride,
  formatCoPay,
  formatPaise,
  pedWaitingActive,
  roomExceedsCap,
  splitCoPay,
  statusTone,
  utilisationPercent,
  validateManualPolicy,
} from './types';

function coverage(over: Partial<PolicyCoverage> = {}): PolicyCoverage {
  return {
    id: 'c-1',
    policyStatus: 'ACTIVE',
    sumInsuredPaise: 100_000_000, // ₹10,00,000
    utilisedPaise: 15_000_000, // ₹1,50,000
    balancePaise: 85_000_000, // ₹8,50,000
    roomRentCapPaise: 500_000, // ₹5,000/day
    icuCapPaise: 1_000_000,
    deductiblePaise: null,
    roomCategory: 'Single Private AC',
    coPayBasisPoints: 1000,
    pedWaitingMonths: 24,
    pedWaitingSatisfied: true,
    checkedAt: new Date().toISOString(),
    exclusions: [],
    ...over,
  };
}

describe('currency formatting', () => {
  it('renders paise as Indian-grouped rupees', () => {
    expect(formatPaise(100_000_000)).toBe('₹10,00,000');
    expect(formatPaise(500_000)).toBe('₹5,000');
  });

  it('shows both decimal places when the amount is not whole rupees', () => {
    // Money renders as ₹1,500.50, not ₹1,500.5 — a trailing single digit reads
    // as a truncated figure on a document the patient signs.
    expect(formatPaise(150_050)).toBe('₹1,500.50');
    expect(formatPaise(150_055)).toBe('₹1,500.55');
  });

  it('renders null as a dash, never as zero', () => {
    // "No cap stated" must not read as "nothing is covered".
    expect(formatPaise(null)).toBe('—');
    expect(formatPaise(undefined)).toBe('—');
    expect(formatPaise(0)).toBe('₹0');
  });
});

describe('co-pay display', () => {
  it('renders whole percentages without decimals', () => {
    expect(formatCoPay(1000)).toBe('10%');
  });

  it('renders fractional percentages', () => {
    // The value an integer-percent column could not have held.
    expect(formatCoPay(750)).toBe('7.5%');
  });

  it('renders absence as a dash', () => {
    expect(formatCoPay(null)).toBe('—');
  });
});

describe('co-pay split', () => {
  it('splits a bill at 10%', () => {
    const { patientPaise, insurerPaise } = splitCoPay(10_000_000, 1000);
    expect(patientPaise).toBe(1_000_000);
    expect(insurerPaise).toBe(9_000_000);
  });

  it('always sums exactly back to the bill', () => {
    // Rounding both shares independently is how a claim ends up a rupee short.
    for (const bill of [999_999, 1, 7, 123_457, 88_888_888]) {
      for (const bp of [750, 1000, 3333, 1]) {
        const { patientPaise, insurerPaise } = splitCoPay(bill, bp);
        expect(patientPaise + insurerPaise).toBe(bill);
      }
    }
  });

  it('handles a 7.5% co-pay on an odd amount', () => {
    const { patientPaise, insurerPaise } = splitCoPay(999_999, 750);
    expect(patientPaise).toBe(75_000);
    expect(insurerPaise).toBe(924_999);
  });

  it('gives the insurer everything when there is no co-pay', () => {
    expect(splitCoPay(500_000, null)).toEqual({ patientPaise: 0, insurerPaise: 500_000 });
    expect(splitCoPay(500_000, 0)).toEqual({ patientPaise: 0, insurerPaise: 500_000 });
  });
});

describe('utilisation', () => {
  it('computes a percentage of the sum insured', () => {
    expect(utilisationPercent(coverage())).toBe(15);
  });

  it('treats missing utilisation as nothing used', () => {
    expect(utilisationPercent(coverage({ utilisedPaise: null }))).toBe(0);
  });

  it('returns null when there is no sum insured to divide by', () => {
    expect(utilisationPercent(coverage({ sumInsuredPaise: null }))).toBeNull();
    expect(utilisationPercent(coverage({ sumInsuredPaise: 0 }))).toBeNull();
  });

  it('clamps over-utilisation to 100', () => {
    expect(utilisationPercent(coverage({ utilisedPaise: 200_000_000 }))).toBe(100);
  });
});

describe('status banner tone', () => {
  it('is positive only for an active policy', () => {
    expect(statusTone('ACTIVE')).toBe('positive');
  });

  it('treats an unverified policy as a warning, not a refusal', () => {
    // A payer outage is not the same fact as a dead policy.
    expect(statusTone('UNKNOWN')).toBe('warning');
  });

  it('is negative for expired, lapsed and suspended', () => {
    expect(statusTone('EXPIRED')).toBe('negative');
    expect(statusTone('LAPSED')).toBe('negative');
    expect(statusTone('SUSPENDED')).toBe('negative');
  });
});

describe('admission guard', () => {
  it('allows an active policy with balance', () => {
    expect(admissibleWithoutOverride(coverage())).toBe(true);
  });

  it('blocks when the policy is not active', () => {
    expect(admissibleWithoutOverride(coverage({ policyStatus: 'EXPIRED' }))).toBe(false);
    expect(admissibleWithoutOverride(coverage({ policyStatus: 'UNKNOWN' }))).toBe(false);
  });

  it('blocks when the balance is exhausted', () => {
    expect(admissibleWithoutOverride(coverage({ balancePaise: 0 }))).toBe(false);
  });

  it('blocks when no balance was stated', () => {
    // Unknown cover is not permission to admit cashless.
    expect(admissibleWithoutOverride(coverage({ balancePaise: null }))).toBe(false);
  });

  it('blocks when no check has run', () => {
    expect(admissibleWithoutOverride(null)).toBe(false);
  });
});

describe('room cap', () => {
  it('flags a room above the daily cap', () => {
    expect(roomExceedsCap(600_000, coverage())).toBe(true);
  });

  it('permits a room at exactly the cap', () => {
    expect(roomExceedsCap(500_000, coverage())).toBe(false);
  });

  it('does not flag when the payer stated no cap', () => {
    expect(roomExceedsCap(9_999_999, coverage({ roomRentCapPaise: null }))).toBe(false);
  });
});

describe('PED waiting period', () => {
  it('is active when the payer says the wait is unsatisfied', () => {
    expect(pedWaitingActive(coverage({ pedWaitingSatisfied: false }))).toBe(true);
  });

  it('is not active when satisfied or unstated', () => {
    expect(pedWaitingActive(coverage())).toBe(false);
    expect(pedWaitingActive(coverage({ pedWaitingSatisfied: null }))).toBe(false);
  });
});

describe('manual policy registration', () => {
  it('accepts an insurer with a policy number', () => {
    expect(
      validateManualPolicy({
        insurerName: 'Star Health',
        policyNumber: 'P-12345',
        memberId: '',
        tpaName: '',
        policyType: 'INDIVIDUAL',
      }).valid,
    ).toBe(true);
  });

  it('accepts a health card showing only a member id', () => {
    expect(
      validateManualPolicy({
        insurerName: 'Star Health',
        policyNumber: '',
        memberId: 'M-99',
        tpaName: 'Medi Assist',
        policyType: '',
      }).valid,
    ).toBe(true);
  });

  it('requires at least one policy identifier', () => {
    const r = validateManualPolicy({
      insurerName: 'Star Health',
      policyNumber: '',
      memberId: '',
      tpaName: '',
      policyType: '',
    });
    expect(r.valid).toBe(false);
    expect(r.errors[0]).toContain('policy number or a member');
  });

  it('requires an insurer', () => {
    expect(
      validateManualPolicy({
        insurerName: '  ',
        policyNumber: 'P-1',
        memberId: '',
        tpaName: '',
        policyType: '',
      }).valid,
    ).toBe(false);
  });
});
