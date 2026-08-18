import { describe, it, expect } from 'vitest'
import {
  AGEING_BRACKETS,
  num,
  sumBy,
  summariseDecisions,
  summariseDisallowance,
  largestDisallowedCharge,
  summariseAgeing,
  summariseDispatch,
  enhancementUplift,
  formatRupees,
} from './insuranceReports'

/**
 * WO-021 / IR-002.
 *
 * The report engine returns raw JDBC rows, so numeric columns arrive as strings
 * or nulls depending on the driver and the aggregate. Most of these tests are
 * about surviving that rather than about arithmetic.
 */

describe('num', () => {
  it('coerces the shapes raw JDBC actually returns', () => {
    expect(num(42)).toBe(42)
    expect(num('42.5')).toBe(42.5)
    expect(num(null)).toBe(0)
    expect(num(undefined)).toBe(0)
    expect(num('')).toBe(0)
  })

  it('never returns NaN', () => {
    // A single NaN poisons every downstream total and blanks the whole card.
    expect(num('not a number')).toBe(0)
    expect(num({})).toBe(0)
    expect(Number.isNaN(num('abc'))).toBe(false)
  })
})

describe('sumBy', () => {
  it('sums string and numeric cells together', () => {
    expect(sumBy([{ a: '10' }, { a: 5 }, { a: null }], 'a')).toBe(15)
  })

  it('is zero for an empty result set', () => {
    expect(sumBy([], 'a')).toBe(0)
  })

  it('is zero for a column that does not exist', () => {
    expect(sumBy([{ a: 1 }], 'missing')).toBe(0)
  })
})

describe('summariseDecisions', () => {
  const rows = [
    { status: 'Approved', approved_amount: '100000' },
    { status: 'Approved', approved_amount: 50000 },
    { status: 'Rejected', approved_amount: 0 },
    { status: 'In process', approved_amount: null },
  ]

  it('counts each outcome', () => {
    const s = summariseDecisions(rows)
    expect(s.approved).toBe(2)
    expect(s.rejected).toBe(1)
    expect(s.pending).toBe(1)
  })

  it('sums the sanctioned amount from approved rows only', () => {
    // Including rejected rows would inflate the figure a finance lead reads as
    // "what the insurers agreed to".
    expect(summariseDecisions(rows).approvedAmount).toBe(150000)
  })

  it('handles an empty report', () => {
    const s = summariseDecisions([])
    expect(s.approved).toBe(0)
    expect(s.approvedAmount).toBe(0)
  })
})

describe('summariseDisallowance', () => {
  it('computes the disallowed percentage to one decimal', () => {
    const s = summariseDisallowance([
      { claims: 2, billed_amount: 100000, disallowed_amount: 12500, received_amount: 87500 },
    ])
    expect(s.disallowedPct).toBe(12.5)
    expect(s.received).toBe(87500)
  })

  it('does not divide by zero when nothing was billed', () => {
    // Would otherwise render "Infinity%" on a finance dashboard.
    const s = summariseDisallowance([{ billed_amount: 0, disallowed_amount: 500 }])
    expect(s.disallowedPct).toBe(0)
    expect(Number.isFinite(s.disallowedPct)).toBe(true)
  })

  it('aggregates across payers', () => {
    const s = summariseDisallowance([
      { claims: 3, billed_amount: 100000, disallowed_amount: 10000 },
      { claims: 2, billed_amount: 100000, disallowed_amount: 30000 },
    ])
    expect(s.claims).toBe(5)
    expect(s.billed).toBe(200000)
    expect(s.disallowed).toBe(40000)
    expect(s.disallowedPct).toBe(20)
  })
})

describe('largestDisallowedCharge', () => {
  it('finds the charge with the biggest total, not the biggest single line', () => {
    // Two small deductions on one charge can outweigh one large deduction
    // elsewhere, and it is the total the desk argues about.
    const worst = largestDisallowedCharge([
      { charge: 'Room Rent', disallowed_amount: 3000 },
      { charge: 'Consumables', disallowed_amount: 2500 },
      { charge: 'Consumables', disallowed_amount: 2500 },
    ])
    expect(worst).toEqual({ charge: 'Consumables', amount: 5000 })
  })

  it('buckets unnamed charges under Other', () => {
    expect(largestDisallowedCharge([{ charge: null, disallowed_amount: 100 }])).toEqual({
      charge: 'Other',
      amount: 100,
    })
  })

  it('returns null for an empty report', () => {
    expect(largestDisallowedCharge([])).toBeNull()
  })
})

describe('summariseAgeing', () => {
  const rows = [
    { ageing_bracket: 'Less than 31 days', outstanding: 10000 },
    { ageing_bracket: '31 to 60 days', outstanding: 20000 },
    { ageing_bracket: '91 to 120 days', outstanding: 30000 },
    { ageing_bracket: 'More than 150 days', outstanding: 40000 },
  ]

  it('always emits all six brackets in order', () => {
    // A ladder that changes length between months destroys the month-on-month
    // comparison the report exists for.
    const s = summariseAgeing(rows)
    expect(s.buckets.map(b => b.bracket)).toEqual([...AGEING_BRACKETS])
  })

  it('reports zero for brackets with no rows', () => {
    const s = summariseAgeing(rows)
    expect(s.buckets.find(b => b.bracket === '61 to 90 days')!.amount).toBe(0)
  })

  it('sums the over-90 balance from the last three brackets', () => {
    expect(summariseAgeing(rows).over90).toBe(70000)
  })

  it('computes each bracket share of the total', () => {
    const s = summariseAgeing(rows)
    expect(s.total).toBe(100000)
    expect(s.buckets.find(b => b.bracket === '31 to 60 days')!.share).toBe(20)
  })

  it('gives every bracket a zero share when nothing is outstanding', () => {
    const s = summariseAgeing([])
    expect(s.total).toBe(0)
    expect(s.buckets.every(b => b.share === 0)).toBe(true)
  })
})

describe('summariseDispatch', () => {
  it('averages only the rows that have a sanction date to measure from', () => {
    const s = summariseDispatch([
      { days_to_dispatch: 4, mode: 'COURIER', pod_no: 'A1' },
      { days_to_dispatch: 8, mode: 'COURIER', pod_no: 'A2' },
      { days_to_dispatch: null, mode: 'EMAIL' },
    ])
    expect(s.dispatched).toBe(3)
    expect(s.avgDaysToDispatch).toBe(6)
  })

  it('returns null rather than zero when nothing can be measured', () => {
    // Zero days would read as instant dispatch, which is the opposite of "we
    // cannot tell".
    expect(summariseDispatch([{ days_to_dispatch: null }]).avgDaysToDispatch).toBeNull()
  })

  it('counts couriered dockets with no consignment number', () => {
    const s = summariseDispatch([
      { mode: 'COURIER', pod_no: '-' },
      { mode: 'COURIER', pod_no: '' },
      { mode: 'COURIER', pod_no: 'BD-1' },
      { mode: 'EMAIL', pod_no: '-' },
    ])
    // Email dispatches have no POD by design and must not be counted.
    expect(s.courieredWithoutPod).toBe(2)
  })
})

describe('enhancementUplift', () => {
  it('is the difference between revised and original', () => {
    expect(
      enhancementUplift([
        { requested_amount: 150000, original_limit: 100000 },
        { requested_amount: 80000, original_limit: 50000 },
      ]),
    ).toBe(80000)
  })

  it('handles a missing original limit as zero', () => {
    expect(enhancementUplift([{ requested_amount: 5000, original_limit: null }])).toBe(5000)
  })
})

describe('formatRupees', () => {
  it('uses the Indian digit grouping', () => {
    expect(formatRupees(100000)).toContain('1,00,000')
  })

  it('rounds to whole rupees', () => {
    expect(formatRupees(1234.6)).toBe('₹ 1,235')
  })
})
