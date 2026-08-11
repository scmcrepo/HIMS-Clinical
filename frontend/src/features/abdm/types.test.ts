import { describe, expect, it } from 'vitest'

import {
  ConsentFormState,
  ConsentRequest,
  ExternalRecord,
  displayState,
  groupByType,
  hiTypeLabels,
  isActionable,
  isConsentLive,
  sortByRecordDate,
  validateConsentForm,
} from './types'

const TODAY = new Date('2026-08-11T10:00:00Z')
const FUTURE = '2026-09-11T10:00:00Z'
const PAST = '2026-07-11T10:00:00Z'

function form(over: Partial<ConsentFormState> = {}): ConsentFormState {
  return {
    purposeCode: 'CAREMGT',
    hiTypes: ['Prescription'],
    dateRangeFrom: '2026-01-01',
    dateRangeTo: '2026-03-31',
    expiresAt: FUTURE,
    ...over,
  }
}

function request(over: Partial<ConsentRequest> = {}): ConsentRequest {
  return {
    id: 'r-1',
    requestState: 'GRANTED',
    purposeCode: 'CAREMGT',
    hiTypes: 'Prescription,DiagnosticReport',
    dateRangeFrom: '2026-01-01',
    dateRangeTo: '2026-03-31',
    expiresAt: FUTURE,
    createdAt: PAST,
    ...over,
  }
}

function record(over: Partial<ExternalRecord> = {}): ExternalRecord {
  return {
    id: 'e-1',
    hiType: 'Prescription',
    recordDate: '2026-02-01T00:00:00Z',
    sourceHipName: 'City Hospital',
    displayTitle: 'Prescription — 1 Feb',
    imported: false,
    ...over,
  }
}

describe('consent form validation', () => {
  it('accepts a well-formed request', () => {
    expect(validateConsentForm(form(), TODAY).valid).toBe(true)
  })

  it('requires a purpose', () => {
    const r = validateConsentForm(form({ purposeCode: '' }), TODAY)
    expect(r.valid).toBe(false)
    expect(r.errors[0]).toContain('why the records are needed')
  })

  it('requires at least one record type', () => {
    expect(validateConsentForm(form({ hiTypes: [] }), TODAY).valid).toBe(false)
  })

  it('rejects a reversed date range', () => {
    const r = validateConsentForm(
      form({ dateRangeFrom: '2026-03-31', dateRangeTo: '2026-01-01' }),
      TODAY,
    )
    expect(r.valid).toBe(false)
    expect(r.errors.join(' ')).toContain('on or before the end date')
  })

  it('rejects a start date in the future', () => {
    // The patient would be asked to approve a request that can return nothing.
    const r = validateConsentForm(
      form({ dateRangeFrom: '2026-12-01', dateRangeTo: '2026-12-31' }),
      TODAY,
    )
    expect(r.valid).toBe(false)
  })

  it('requires an expiry and never defaults one', () => {
    const r = validateConsentForm(form({ expiresAt: '' }), TODAY)
    expect(r.valid).toBe(false)
    expect(r.errors.join(' ')).toContain('expire')
  })

  it('rejects an expiry already in the past', () => {
    expect(validateConsentForm(form({ expiresAt: PAST }), TODAY).valid).toBe(false)
  })
})

describe('consent liveness', () => {
  it('is live when granted and unexpired', () => {
    expect(isConsentLive(request(), TODAY)).toBe(true)
  })

  it('is not live once expired', () => {
    expect(isConsentLive(request({ expiresAt: PAST }), TODAY)).toBe(false)
  })

  it('is not live without an expiry', () => {
    // Mirrors the server: a missing expiry is not perpetual permission.
    expect(isConsentLive(request({ expiresAt: null }), TODAY)).toBe(false)
  })

  it('is not live while awaiting the patient', () => {
    expect(isConsentLive(request({ requestState: 'PENDING_APPROVAL' }), TODAY)).toBe(false)
  })

  it('is not live once withdrawn', () => {
    expect(isConsentLive(request({ requestState: 'REVOKED' }), TODAY)).toBe(false)
  })
})

describe('displayed state', () => {
  it('corrects a stale GRANTED to EXPIRED', () => {
    // Nothing writes to the row when the expiry passes, so showing the raw
    // value would tell a clinician they have access they no longer have.
    expect(displayState(request({ expiresAt: PAST }), TODAY)).toBe('EXPIRED')
  })

  it('leaves a live grant alone', () => {
    expect(displayState(request(), TODAY)).toBe('GRANTED')
  })

  it('does not rewrite a denial', () => {
    expect(displayState(request({ requestState: 'DENIED' }), TODAY)).toBe('DENIED')
  })

  it('drives actionability', () => {
    expect(isActionable(request(), TODAY)).toBe(true)
    expect(isActionable(request({ expiresAt: PAST }), TODAY)).toBe(false)
  })
})

describe('record presentation', () => {
  it('maps stored types to clinician-facing labels', () => {
    expect(hiTypeLabels('Prescription,DiagnosticReport')).toEqual([
      'Prescriptions',
      'Lab & diagnostic reports',
    ])
  })

  it('passes through an unrecognised type rather than dropping it', () => {
    expect(hiTypeLabels('Prescription,SomethingNew')).toContain('SomethingNew')
  })

  it('groups records by type', () => {
    const grouped = groupByType([
      record({ id: 'a', hiType: 'Prescription' }),
      record({ id: 'b', hiType: 'DiagnosticReport' }),
      record({ id: 'c', hiType: 'Prescription' }),
    ])
    expect(grouped['Prescriptions']).toHaveLength(2)
    expect(grouped['Lab & diagnostic reports']).toHaveLength(1)
  })

  it('sorts newest care first', () => {
    const sorted = sortByRecordDate([
      record({ id: 'old', recordDate: '2026-01-01T00:00:00Z' }),
      record({ id: 'new', recordDate: '2026-06-01T00:00:00Z' }),
    ])
    expect(sorted[0].id).toBe('new')
  })

  it('sorts undated records last, not first', () => {
    // Treating an undated record as epoch would float it to the top of a
    // clinical timeline as the oldest event on record.
    const sorted = sortByRecordDate([
      record({ id: 'undated', recordDate: null }),
      record({ id: 'dated', recordDate: '2026-01-01T00:00:00Z' }),
    ])
    expect(sorted[0].id).toBe('dated')
    expect(sorted[1].id).toBe('undated')
  })

  it('does not mutate the input array', () => {
    const input = [record({ id: 'a' }), record({ id: 'b', recordDate: null })]
    const before = input.map((r) => r.id)
    sortByRecordDate(input)
    expect(input.map((r) => r.id)).toEqual(before)
  })
})
