import { describe, expect, it } from 'vitest';

import {
  AbhaLinkage,
  activeLinkage,
  canDownloadCard,
  canStartEnrolment,
  failureMessage,
  isVerified,
  validateOtp,
  validateStartRequest,
} from './types';

function linkage(over: Partial<AbhaLinkage> = {}): AbhaLinkage {
  return {
    id: 'l-1',
    patientId: 'p-1',
    abhaNumberMasked: 'XX-XXXX-XXXX-0123',
    abhaAddress: 'ravi@abdm',
    linkageState: 'LINKED',
    linkedAt: new Date().toISOString(),
    failureCode: null,
    ...over,
  };
}

describe('start-enrolment validation', () => {
  it('accepts a 12-digit Aadhaar', () => {
    const r = validateStartRequest({ patientId: 'p-1', channel: 'AADHAAR', loginId: '123456789012' });
    expect(r.valid).toBe(true);
    expect(r.errors).toHaveLength(0);
  });

  it('accepts a 10-digit mobile', () => {
    expect(
      validateStartRequest({ patientId: 'p-1', channel: 'MOBILE', loginId: '9876543210' }).valid,
    ).toBe(true);
  });

  it('rejects a 10-digit value offered as Aadhaar', () => {
    // The commonest desk error: right digits, wrong channel.
    const r = validateStartRequest({ patientId: 'p-1', channel: 'AADHAAR', loginId: '9876543210' });
    expect(r.valid).toBe(false);
    expect(r.errors[0]).toContain('12 digits');
  });

  it('rejects a 12-digit value offered as mobile', () => {
    const r = validateStartRequest({ patientId: 'p-1', channel: 'MOBILE', loginId: '123456789012' });
    expect(r.valid).toBe(false);
    expect(r.errors[0]).toContain('10 digits');
  });

  it('rejects non-numeric input', () => {
    const r = validateStartRequest({ patientId: 'p-1', channel: 'MOBILE', loginId: '98765abcde' });
    expect(r.valid).toBe(false);
    expect(r.errors).toContain('Only digits are allowed');
  });

  it('requires a patient to be selected', () => {
    const r = validateStartRequest({ patientId: '', channel: 'MOBILE', loginId: '9876543210' });
    expect(r.valid).toBe(false);
    expect(r.errors).toContain('Select a patient first');
  });

  it('tolerates spacing in a typed Aadhaar', () => {
    expect(
      validateStartRequest({ patientId: 'p-1', channel: 'AADHAAR', loginId: '1234 5678 9012' }).valid,
    ).toBe(true);
  });
});

describe('otp validation', () => {
  it('accepts a six-digit otp', () => {
    expect(validateOtp('123456').valid).toBe(true);
  });

  it('rejects an empty otp', () => {
    expect(validateOtp('').valid).toBe(false);
  });

  it('rejects an otp that is too long', () => {
    expect(validateOtp('1234567890').valid).toBe(false);
  });

  it('rejects a non-numeric otp', () => {
    expect(validateOtp('12ab56').valid).toBe(false);
  });
});

describe('verified badge', () => {
  it('shows for a linked identity', () => {
    expect(isVerified(linkage())).toBe(true);
  });

  it('does not show while awaiting otp', () => {
    expect(isVerified(linkage({ linkageState: 'PENDING_OTP', abhaNumberMasked: null }))).toBe(false);
  });

  it('does not show for a failed attempt', () => {
    expect(isVerified(linkage({ linkageState: 'FAILED' }))).toBe(false);
  });

  it('does not show when the server sent no number', () => {
    // Guards against a LINKED row with nothing to display.
    expect(isVerified(linkage({ abhaNumberMasked: null }))).toBe(false);
  });

  it('handles absence', () => {
    expect(isVerified(null)).toBe(false);
    expect(isVerified(undefined)).toBe(false);
  });
});

describe('duplicate-identity guard', () => {
  it('blocks a second enrolment when one is already linked', () => {
    expect(canStartEnrolment([linkage()])).toBe(false);
  });

  it('allows enrolment after only failed attempts', () => {
    expect(canStartEnrolment([linkage({ linkageState: 'FAILED' })])).toBe(true);
  });

  it('allows enrolment with no history', () => {
    expect(canStartEnrolment([])).toBe(true);
  });

  it('picks the linked record out of a mixed history', () => {
    const found = activeLinkage([
      linkage({ id: 'l-0', linkageState: 'FAILED' }),
      linkage({ id: 'l-2' }),
    ]);
    expect(found?.id).toBe('l-2');
  });
});

describe('card download', () => {
  it('is available once verified', () => {
    expect(canDownloadCard(linkage())).toBe(true);
  });

  it('is unavailable before verification', () => {
    expect(canDownloadCard(linkage({ linkageState: 'PENDING_OTP' }))).toBe(false);
  });
});

describe('failure messages', () => {
  it('tells the desk to capture consent', () => {
    expect(failureMessage('ConsentRequiredException')).toContain('consent');
  });

  it('explains a duplicate linkage', () => {
    expect(failureMessage('BusinessRuleViolationException')).toContain('already has a linked ABHA');
  });

  it('falls back for an unknown code', () => {
    expect(failureMessage('SomethingNew')).toBe('Verification failed. Please try again.');
  });

  it('never echoes the raw code to the user', () => {
    // Codes are exception type names; showing them leaks internals to a desk
    // user who cannot act on them.
    expect(failureMessage('GovApiException')).not.toContain('GovApiException');
  });
});
