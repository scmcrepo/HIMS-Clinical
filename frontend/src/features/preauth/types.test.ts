import { describe, expect, it } from 'vitest'

import {
  EstimateLine,
  PreAuthQuery,
  awaitingHospital,
  categoryTotal,
  enhancementDelta,
  estimateTotal,
  exceedsAvailableBalance,
  lineAmount,
  patientLiability,
  pendingQueries,
  roomShortfall,
  validateEnhancement,
  validatePreAuthForm,
} from './types'

function line(over: Partial<EstimateLine> = {}): EstimateLine {
  return { category: 'ROOM', description: 'Private room', quantity: 3, unitAmount: 500_000, ...over }
}

function query(over: Partial<PreAuthQuery> = {}): PreAuthQuery {
  return {
    id: 'q-1',
    roundNumber: 1,
    raisedAt: '2026-08-01T10:00:00Z',
    queryCode: null,
    queryText: 'Send the operative notes',
    respondedAt: null,
    responseText: null,
    answered: false,
    ...over,
  }
}

describe('estimate arithmetic', () => {
  it('extends decimal quantities', () => {
    expect(lineAmount(2, 500_000)).toBe(1_000_000)
    expect(lineAmount(0.5, 500_000)).toBe(250_000)
    expect(lineAmount(1.5, 500_000)).toBe(750_000)
  })

  it('rejects a zero or negative quantity', () => {
    expect(lineAmount(0, 500_000)).toBe(0)
    expect(lineAmount(-1, 500_000)).toBe(0)
  })

  it('totals exactly the sum of the displayed lines', () => {
    // A total that does not match the visible lines is the first thing an
    // insurer queries.
    const lines = [
      line(),
      line({ category: 'CONSUMABLE', quantity: 2.5, unitAmount: 33_333 }),
      line({ category: 'IMPLANT', quantity: 1, unitAmount: 4_000_000 }),
    ]
    const manual = lines.reduce((s, l) => s + lineAmount(l.quantity, l.unitAmount), 0)
    expect(estimateTotal(lines)).toBe(manual)
  })

  it('totals a single category', () => {
    expect(categoryTotal([line(), line({ category: 'OT', unitAmount: 2_500_000, quantity: 1 })], 'ROOM'))
      .toBe(1_500_000)
  })

  it('treats an empty estimate as zero', () => {
    expect(estimateTotal([])).toBe(0)
  })
})

describe('room shortfall', () => {
  it('charges the excess over the daily cap', () => {
    expect(roomShortfall(1_500_000, 400_000, 3)).toBe(300_000)
  })

  it('is zero exactly at the cap', () => {
    expect(roomShortfall(1_500_000, 500_000, 3)).toBe(0)
  })

  it('is zero when the payer stated no cap', () => {
    // Not the whole room charge — that would quote a shortfall that does not exist.
    expect(roomShortfall(1_500_000, null, 3)).toBe(0)
    expect(roomShortfall(1_500_000, undefined, 3)).toBe(0)
  })
})

describe('patient liability', () => {
  it('applies the deductible before the co-pay', () => {
    // 10,000 + 10% of 90,000 = 19,000. Pre-deductible would give 20,000.
    expect(patientLiability(10_000_000, 1000, 1_000_000, 0)).toBe(1_900_000)
  })

  it('never exceeds the estimate when the deductible is larger', () => {
    expect(patientLiability(10_000_000, 1000, 50_000_000, 0)).toBe(10_000_000)
  })

  it('adds the room shortfall on top', () => {
    expect(patientLiability(10_000_000, 1000, 1_000_000, 300_000)).toBe(2_200_000)
  })

  it('is zero with no co-pay and no deductible', () => {
    expect(patientLiability(10_000_000, null, null, 0)).toBe(0)
  })

  it('handles a fractional co-pay', () => {
    expect(patientLiability(10_000_000, 750, null, 0)).toBe(750_000)
  })

  it('matches the server on the same inputs', () => {
    // Same vectors as PreAuthEstimateCalculatorTest; a drift between the quoted
    // figure and the submitted one is exactly what this guards.
    expect(patientLiability(10_000_000, 1000, 1_000_000, 0)).toBe(1_900_000)
    expect(patientLiability(10_000_000, 1000, null, 0)).toBe(1_000_000)
  })
})

describe('balance and enhancement', () => {
  it('does not block on an unknown balance', () => {
    expect(exceedsAvailableBalance(10_000_000, null)).toBe(false)
  })

  it('flags an estimate above the balance', () => {
    expect(exceedsAvailableBalance(10_000_000, 5_000_000)).toBe(true)
    expect(exceedsAvailableBalance(10_000_000, 10_000_000)).toBe(false)
  })

  it('computes the delta, not the revised total', () => {
    expect(enhancementDelta(8_000_000, 10_000_000)).toBe(2_000_000)
  })
})

describe('pre-auth form validation', () => {
  const valid = {
    diagnosisCode: 'I21.9',
    plannedProcedure: 'Coronary angioplasty',
    expectedLosDays: 3,
    lines: [line()],
  }

  it('accepts a complete form', () => {
    expect(validatePreAuthForm(valid).valid).toBe(true)
  })

  it('requires a diagnosis', () => {
    const r = validatePreAuthForm({ ...valid, diagnosisCode: '' })
    expect(r.valid).toBe(false)
    expect(r.errors[0]).toContain('ICD-10')
  })

  it('requires at least one estimate line', () => {
    expect(validatePreAuthForm({ ...valid, lines: [] }).valid).toBe(false)
  })

  it('rejects a line with no description', () => {
    expect(validatePreAuthForm({ ...valid, lines: [line({ description: '  ' })] }).valid).toBe(false)
  })

  it('rejects a zero quantity', () => {
    expect(validatePreAuthForm({ ...valid, lines: [line({ quantity: 0 })] }).valid).toBe(false)
  })

  it('rejects a negative length of stay', () => {
    expect(validatePreAuthForm({ ...valid, expectedLosDays: -1 }).valid).toBe(false)
  })
})

describe('enhancement validation', () => {
  it('accepts an increase with justification', () => {
    expect(validateEnhancement(8_000_000, 10_000_000, 'Implant cost higher than estimated').valid)
      .toBe(true)
  })

  it('rejects asking for less than is already approved', () => {
    const r = validateEnhancement(10_000_000, 8_000_000, 'because')
    expect(r.valid).toBe(false)
    expect(r.errors[0]).toContain('higher than')
  })

  it('rejects an equal amount', () => {
    expect(validateEnhancement(10_000_000, 10_000_000, 'because').valid).toBe(false)
  })

  it('requires a justification', () => {
    expect(validateEnhancement(8_000_000, 10_000_000, '   ').valid).toBe(false)
  })
})

describe('query thread', () => {
  it('lists unanswered rounds oldest first', () => {
    const pending = pendingQueries([
      query({ id: 'b', roundNumber: 2, raisedAt: '2026-08-05T10:00:00Z' }),
      query({ id: 'a', roundNumber: 1, raisedAt: '2026-08-01T10:00:00Z' }),
      query({ id: 'done', answered: true, respondedAt: '2026-08-02T10:00:00Z' }),
    ])
    expect(pending.map((q) => q.id)).toEqual(['a', 'b'])
  })

  it('knows when the insurer is still waiting on us', () => {
    expect(awaitingHospital([query()])).toBe(true)
    expect(awaitingHospital([query({ answered: true })])).toBe(false)
    expect(awaitingHospital([])).toBe(false)
  })
})
