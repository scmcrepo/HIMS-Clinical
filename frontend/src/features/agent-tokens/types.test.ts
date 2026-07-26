import { describe, expect, it } from 'vitest';

import {
  AgentToken,
  MAX_VALIDITY_DAYS,
  STALE_UNUSED_DAYS,
  daysUntilExpiry,
  expiringSoon,
  isWriteCapable,
  unusedTooLong,
  validateIssueRequest,
} from './types';

const DAY = 86_400_000;

function token(over: Partial<AgentToken> = {}): AgentToken {
  return {
    id: 't-1',
    name: 'assistant',
    scopes: ['AGENT_BED_READ'],
    createdAt: new Date().toISOString(),
    expiresAt: new Date(Date.now() + 90 * DAY).toISOString(),
    status: 'ACTIVE',
    ...over,
  };
}

describe('scope classification', () => {
  it('flags a token that can change hospital data', () => {
    // Surfaced separately so granting write access is deliberate.
    expect(isWriteCapable(['AGENT_SCHEDULING_WRITE'])).toBe(true);
  });

  it('does not flag read-only tokens', () => {
    expect(isWriteCapable(['AGENT_BED_READ', 'AGENT_BILLING_READ'])).toBe(false);
  });
});

describe('expiry', () => {
  it('counts days remaining', () => {
    expect(daysUntilExpiry(token({ expiresAt: new Date(Date.now() + 10 * DAY).toISOString() })))
      .toBe(10);
  });

  it('warns when expiry is close', () => {
    expect(expiringSoon(token({ expiresAt: new Date(Date.now() + 3 * DAY).toISOString() })))
      .toBe(true);
  });

  it('does not warn for a token with plenty of life', () => {
    expect(expiringSoon(token())).toBe(false);
  });

  it('does not warn about already-revoked tokens', () => {
    expect(expiringSoon(token({ status: 'REVOKED',
      expiresAt: new Date(Date.now() + DAY).toISOString() }))).toBe(false);
  });
});

describe('dormant credentials', () => {
  it('flags a live token nobody has used', () => {
    // A credential nobody uses is one nobody would notice leaking.
    expect(unusedTooLong(token({
      lastUsedAt: new Date(Date.now() - (STALE_UNUSED_DAYS + 5) * DAY).toISOString(),
    }))).toBe(true);
  });

  it('measures never-used tokens from issuance', () => {
    expect(unusedTooLong(token({
      createdAt: new Date(Date.now() - (STALE_UNUSED_DAYS + 5) * DAY).toISOString(),
      lastUsedAt: null,
    }))).toBe(true);
  });

  it('does not flag a recently used token', () => {
    expect(unusedTooLong(token({ lastUsedAt: new Date().toISOString() }))).toBe(false);
  });
});

describe('issue validation', () => {
  it('requires a name', () => {
    expect(validateIssueRequest({ name: '  ', scopes: ['AGENT_BED_READ'] }))
      .toContain('Name is required');
  });

  it('requires at least one scope', () => {
    expect(validateIssueRequest({ name: 'x', scopes: [] }))
      .toContain('Select at least one scope');
  });

  it('rejects validity beyond the cap', () => {
    // A credential that never rotates is one nobody notices has leaked.
    expect(validateIssueRequest({
      name: 'x', scopes: ['AGENT_BED_READ'], validityDays: MAX_VALIDITY_DAYS + 1,
    })).toHaveLength(1);
  });

  it('accepts a well-formed request', () => {
    expect(validateIssueRequest({
      name: 'assistant', scopes: ['AGENT_BED_READ'], validityDays: 90,
    })).toEqual([]);
  });
});
