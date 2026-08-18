import { describe, it, expect } from 'vitest';
import {
  STAGE_RANK,
  NEXT_STAGES,
  TIMELINE_STEPS,
  WORKFLOW_STAGES,
  STAGE_LABELS,
  hasReached,
  unlockedSteps,
  lockReason,
  isCardExpired,
  daysUntilCardExpiry,
  validateCommunication,
  validateDecision,
  validateDispatch,
  totalChequeAmount,
  summariseChecklist,
  outstandingAgainstLimit,
  formatPaise,
  rupeesToPaise,
  type WorkflowStage,
} from './insuranceDesk';

describe('stage ranking', () => {
  it('ranks the happy path in order', () => {
    expect(STAGE_RANK.PREAUTHORISATION).toBeLessThan(STAGE_RANK.PREAUTHORISATION_APPROVAL);
    expect(STAGE_RANK.PREAUTHORISATION_APPROVAL).toBeLessThan(STAGE_RANK.ENHANCEMENT_REQUEST);
    expect(STAGE_RANK.ENHANCEMENT_REQUEST).toBeLessThan(STAGE_RANK.ENHANCEMENT_APPROVAL);
    expect(STAGE_RANK.ENHANCEMENT_APPROVAL).toBeLessThan(STAGE_RANK.CHECK_LIST_ENTRY);
    expect(STAGE_RANK.CHECK_LIST_ENTRY).toBeLessThan(STAGE_RANK.DISPATCH_ENTRY);
    expect(STAGE_RANK.DISPATCH_ENTRY).toBeLessThan(STAGE_RANK.DISALLOWANCE_ENTRY);
  });

  it('gives alternative outcomes the same rank', () => {
    // Mirrors the server: approval and rejection are both "the TPA answered".
    expect(STAGE_RANK.PREAUTHORISATION_APPROVAL).toBe(STAGE_RANK.PREAUTHORISATION_REJECTED);
    expect(STAGE_RANK.ENHANCEMENT_APPROVAL).toBe(STAGE_RANK.ENHANCEMENT_REJECTED);
  });

  it('has a rank and a label for every stage', () => {
    for (const stage of WORKFLOW_STAGES) {
      expect(STAGE_RANK[stage]).toBeTypeOf('number');
      expect(STAGE_LABELS[stage]).toBeTruthy();
    }
  });

  it('draws seven timeline steps', () => {
    expect(TIMELINE_STEPS).toHaveLength(7);
  });

  it('never lets a next-stage transition move backwards', () => {
    // Mirrors the server's monotonic guarantee. A transition table entry
    // pointing at a lower rank would let the UI offer a step that the server
    // would then refuse to advance to.
    for (const stage of WORKFLOW_STAGES) {
      for (const next of NEXT_STAGES[stage]) {
        expect(STAGE_RANK[next]).toBeGreaterThan(STAGE_RANK[stage]);
      }
    }
  });

  it('makes both terminal stages dead ends', () => {
    expect(NEXT_STAGES.PREAUTHORISATION_REJECTED).toHaveLength(0);
    expect(NEXT_STAGES.DISALLOWANCE_ENTRY).toHaveLength(0);
  });
});

describe('hasReached', () => {
  it('is false for a legacy record with no stage', () => {
    // Null means "created before the desk flow existed", not "at stage zero".
    // Rendering step 1 as complete would be a lie about the audit trail.
    expect(hasReached(null, 'PREAUTHORISATION')).toBe(false);
  });

  it('includes the current stage and everything before it', () => {
    expect(hasReached('DISPATCH_ENTRY', 'PREAUTHORISATION')).toBe(true);
    expect(hasReached('DISPATCH_ENTRY', 'DISPATCH_ENTRY')).toBe(true);
    expect(hasReached('DISPATCH_ENTRY', 'DISALLOWANCE_ENTRY')).toBe(false);
  });
});

describe('unlockedSteps', () => {
  const desk = (currentStage: WorkflowStage | null, billLinked = true) => ({
    currentStage,
    billLinked,
  });

  it('opens only the first step on a record with no recorded stage', () => {
    // A legacy record has no stage. Nothing downstream is reachable until the
    // desk records stage 1, because there is no transition to reason from.
    const steps = unlockedSteps(desk(null));
    expect(steps.preauth).toBe(true);
    expect(steps.preauthApproval).toBe(false);
    expect(steps.dispatch).toBe(false);
    expect(steps.disallowance).toBe(false);
  });

  it('opens the approval step once the request is recorded', () => {
    const steps = unlockedSteps(desk('PREAUTHORISATION'));
    expect(steps.preauthApproval).toBe(true);
    expect(steps.checkList).toBe(false);
  });

  it('keeps completed steps open so corrections are possible', () => {
    // A clerk fixing a fax number after dispatch is routine.
    const steps = unlockedSteps(desk('DISPATCH_ENTRY'));
    expect(steps.preauth).toBe(true);
    expect(steps.preauthApproval).toBe(true);
    expect(steps.checkList).toBe(true);
  });

  it('locks the enhancement step until a bill is linked', () => {
    expect(unlockedSteps(desk('PREAUTHORISATION_APPROVAL', false)).enhancement).toBe(false);
    expect(unlockedSteps(desk('PREAUTHORISATION_APPROVAL', true)).enhancement).toBe(true);
  });

  it('locks enhancement approval until the request exists', () => {
    expect(unlockedSteps(desk('PREAUTHORISATION_APPROVAL')).enhancementApproval).toBe(false);
    expect(unlockedSteps(desk('ENHANCEMENT_REQUEST')).enhancementApproval).toBe(true);
  });

  it('closes everything once the pre-auth is rejected', () => {
    const steps = unlockedSteps(desk('PREAUTHORISATION_REJECTED'));
    expect(Object.values(steps).every((v) => v === false)).toBe(true);
  });

  it('allows skipping the enhancement stages entirely', () => {
    // Most claims never need an enhancement, so the checklist must be
    // reachable straight from pre-auth approval. This is the case a
    // "current rank + 1" rule silently breaks.
    expect(unlockedSteps(desk('PREAUTHORISATION_APPROVAL')).checkList).toBe(true);
  });

  it('waits for the TPA decision before opening the checklist mid-enhancement', () => {
    // Packing the docket while an enhancement is still unanswered means
    // dispatching a claim for the wrong amount.
    expect(unlockedSteps(desk('ENHANCEMENT_REQUEST')).checkList).toBe(false);
    expect(unlockedSteps(desk('ENHANCEMENT_APPROVAL')).checkList).toBe(true);
    expect(unlockedSteps(desk('ENHANCEMENT_REJECTED')).checkList).toBe(true);
  });
});

describe('lockReason', () => {
  it('is null for an open step', () => {
    expect(lockReason('preauth', { currentStage: null, billLinked: true })).toBeNull();
  });

  it('names the bill as the blocker for enhancement', () => {
    const reason = lockReason('enhancement', {
      currentStage: 'PREAUTHORISATION_APPROVAL',
      billLinked: false,
    });
    expect(reason).toMatch(/credit bill/i);
  });

  it('explains a rejected claim', () => {
    const reason = lockReason('checkList', {
      currentStage: 'PREAUTHORISATION_REJECTED',
      billLinked: true,
    });
    expect(reason).toMatch(/rejected/i);
  });
});

describe('card expiry', () => {
  const today = new Date(2026, 7, 15); // 15 Aug 2026, local

  it('does not treat a card valid through today as expired', () => {
    // Expiry is end-of-day. Flagging it from midnight would warn on cards that
    // are still perfectly valid at the counter.
    expect(isCardExpired('2026-08-15', today)).toBe(false);
  });

  it('treats yesterday as expired', () => {
    expect(isCardExpired('2026-08-14', today)).toBe(true);
  });

  it('treats tomorrow as valid', () => {
    expect(isCardExpired('2026-08-16', today)).toBe(false);
  });

  it('does not flag a card with no recorded expiry', () => {
    // Most walk-in cards have none. Defaulting to expired would put an amber
    // banner on every claim and train the desk to ignore it.
    expect(isCardExpired(null, today)).toBe(false);
    expect(isCardExpired(undefined, today)).toBe(false);
    expect(isCardExpired('', today)).toBe(false);
  });

  it('ignores a time component on the date', () => {
    expect(isCardExpired('2026-08-15T23:59:59Z', today)).toBe(false);
  });

  it('does not flag an unparseable date', () => {
    expect(isCardExpired('not-a-date', today)).toBe(false);
  });

  it('counts days to expiry, negative when lapsed', () => {
    expect(daysUntilCardExpiry('2026-08-20', today)).toBe(5);
    expect(daysUntilCardExpiry('2026-08-15', today)).toBe(0);
    expect(daysUntilCardExpiry('2026-08-10', today)).toBe(-5);
    expect(daysUntilCardExpiry(null, today)).toBeNull();
  });
});

describe('validateCommunication', () => {
  it('requires a mode', () => {
    expect(validateCommunication(null, '044-1', null)).toMatch(/how this was sent/i);
  });

  it('requires a fax number for FAX', () => {
    expect(validateCommunication('FAX', '  ', 'a@b.com')).toMatch(/fax number/i);
    expect(validateCommunication('FAX', '044-12345678', null)).toBeNull();
  });

  it('requires a mail id for MAIL', () => {
    expect(validateCommunication('MAIL', '044-1', null)).toMatch(/mail id/i);
    expect(validateCommunication('MAIL', null, 'claims@tpa.example')).toBeNull();
  });
});

describe('validateDecision', () => {
  it('requires an amount when approved', () => {
    expect(validateDecision('APPROVED', null, null)).toMatch(/sanctioned/i);
    expect(validateDecision('APPROVED', 0, null)).toMatch(/sanctioned/i);
    expect(validateDecision('APPROVED', 10_000_000, null)).toBeNull();
  });

  it('requires a reason when rejected', () => {
    expect(validateDecision('REJECTED', null, '   ')).toMatch(/declined/i);
    expect(validateDecision('REJECTED', null, 'Policy lapsed')).toBeNull();
  });

  it('requires a decision at all', () => {
    expect(validateDecision(null, 100, 'x')).toBeTruthy();
  });
});

describe('validateDispatch', () => {
  it('requires a POD number for a courier dispatch', () => {
    const err = validateDispatch({
      modeOfDispatch: 'COURIER',
      courier: 'DTDC',
      podNo: '',
    });
    expect(err).toMatch(/POD/i);
  });

  it('requires a vendor for a courier dispatch', () => {
    expect(
      validateDispatch({ modeOfDispatch: 'COURIER', courier: null, podNo: 'POD1' }),
    ).toMatch(/courier/i);
  });

  it('requires a destination for an email dispatch', () => {
    expect(validateDispatch({ modeOfDispatch: 'EMAIL', dispatchMailId: '  ' })).toMatch(
      /mail id/i,
    );
  });

  it('accepts a complete courier dispatch', () => {
    expect(
      validateDispatch({ modeOfDispatch: 'COURIER', courier: 'BLUE_DART', podNo: 'BD-99812' }),
    ).toBeNull();
  });
});

describe('cheque totals', () => {
  it('sums paise amounts', () => {
    expect(
      totalChequeAmount([
        { chequeNo: 'A', amount: 8_500_000 },
        { chequeNo: 'B', amount: 1_500_000 },
      ]),
    ).toBe(10_000_000);
  });

  it('treats a malformed amount as zero rather than producing NaN', () => {
    // NaN propagating into the settlement total would render the whole
    // summary blank, hiding the cheques that are valid.
    expect(
      totalChequeAmount([
        { chequeNo: 'A', amount: 100 },
        { chequeNo: 'B', amount: Number.NaN },
      ]),
    ).toBe(100);
  });

  it('is zero for an empty grid', () => {
    expect(totalChequeAmount([])).toBe(0);
  });
});

describe('summariseChecklist', () => {
  it('counts shortfalls and names the pending documents', () => {
    const s = summariseChecklist([
      { name: 'Discharge Summary', toBeSubmit: 1, submitted: 1 },
      { name: 'Pharmacy Receipts', toBeSubmit: 5, submitted: 4 },
      { name: 'Implant Sticker', toBeSubmit: 2, submitted: 0 },
    ]);
    expect(s.total).toBe(3);
    expect(s.complete).toBe(1);
    expect(s.shortfallItems).toBe(2);
    expect(s.pending).toEqual(['Pharmacy Receipts', 'Implant Sticker']);
  });

  it('treats over-submission as complete', () => {
    const s = summariseChecklist([{ name: 'X', toBeSubmit: 1, submitted: 3 }]);
    expect(s.shortfallItems).toBe(0);
  });

  it('handles an empty checklist', () => {
    expect(summariseChecklist([]).shortfallItems).toBe(0);
  });
});

describe('outstandingAgainstLimit', () => {
  it('is null when nothing is sanctioned yet', () => {
    // Not zero: zero renders as "fully recovered" on a claim the TPA has not
    // even answered.
    expect(
      outstandingAgainstLimit({ effectiveApprovedLimit: null, totalReceived: 0 }),
    ).toBeNull();
  });

  it('subtracts what has been received', () => {
    expect(
      outstandingAgainstLimit({ effectiveApprovedLimit: 10_000_000, totalReceived: 8_500_000 }),
    ).toBe(1_500_000);
  });

  it('can go negative when the insurer overpays', () => {
    expect(
      outstandingAgainstLimit({ effectiveApprovedLimit: 10_000_000, totalReceived: 11_000_000 }),
    ).toBe(-1_000_000);
  });
});

describe('money formatting', () => {
  it('renders paise as rupees', () => {
    expect(formatPaise(10_000_000)).toContain('1,00,000');
  });

  it('renders an em dash for absent amounts', () => {
    expect(formatPaise(null)).toBe('\u2014');
    expect(formatPaise(undefined)).toBe('\u2014');
    expect(formatPaise(Number.NaN)).toBe('\u2014');
  });

  it('converts clerk-entered rupees to paise', () => {
    expect(rupeesToPaise('1000')).toBe(100_000);
    expect(rupeesToPaise('1,00,000')).toBe(10_000_000);
    expect(rupeesToPaise(1234.56)).toBe(123_456);
  });

  it('rounds rather than truncating fractional paise', () => {
    expect(rupeesToPaise('0.005')).toBe(1);
  });

  it('returns null for blank or junk input', () => {
    expect(rupeesToPaise('')).toBeNull();
    expect(rupeesToPaise(null)).toBeNull();
    expect(rupeesToPaise('abc')).toBeNull();
  });
});
