/** Agent token management (WO-001 / T-010). */

export const AGENT_SCOPES = [
  'AGENT_SCHEDULING_READ',
  'AGENT_SCHEDULING_WRITE',
  'AGENT_BILLING_READ',
  'AGENT_BED_READ',
  'AGENT_TOOLS_READ',
] as const;

export type AgentScope = (typeof AGENT_SCOPES)[number];

export const SCOPE_LABELS: Record<AgentScope, string> = {
  AGENT_SCHEDULING_READ: 'Read appointment availability',
  AGENT_SCHEDULING_WRITE: 'Book and modify appointments',
  AGENT_BILLING_READ: 'Read patient billing ledger',
  AGENT_BED_READ: 'Read bed occupancy',
  AGENT_TOOLS_READ: 'Read tool catalogue',
};

/**
 * Scopes that let the agent change hospital data. Surfaced separately in the UI
 * so an admin granting write access does so deliberately rather than by
 * ticking down a uniform list.
 */
export const WRITE_SCOPES: AgentScope[] = ['AGENT_SCHEDULING_WRITE'];

export type TokenStatus = 'ACTIVE' | 'EXPIRED' | 'REVOKED';

export interface AgentToken {
  id: string;
  name: string;
  scopes: AgentScope[];
  branchId?: string | null;
  createdAt: string;
  expiresAt: string;
  lastUsedAt?: string | null;
  revokedAt?: string | null;
  status: TokenStatus;
  /** Present only in the response to issuance. Never returned again. */
  token?: string;
}

export interface IssueTokenRequest {
  name: string;
  scopes: AgentScope[];
  branchId?: string | null;
  validityDays?: number;
}

export const DEFAULT_VALIDITY_DAYS = 90;
export const MAX_VALIDITY_DAYS = 365;

/** Unused this long and it is probably a credential to revoke, not to keep. */
export const STALE_UNUSED_DAYS = 30;

export function isWriteCapable(scopes: AgentScope[]): boolean {
  return scopes.some((s) => WRITE_SCOPES.includes(s));
}

export function daysUntilExpiry(token: AgentToken, now = Date.now()): number {
  return Math.ceil((new Date(token.expiresAt).getTime() - now) / 86_400_000);
}

export function expiringSoon(token: AgentToken, withinDays = 14, now = Date.now()): boolean {
  if (token.status !== 'ACTIVE') return false;
  const left = daysUntilExpiry(token, now);
  return left <= withinDays && left >= 0;
}

/**
 * A live credential nobody is using is a credential nobody would notice leaking.
 * Never-used tokens count from issuance.
 */
export function unusedTooLong(token: AgentToken, now = Date.now()): boolean {
  if (token.status !== 'ACTIVE') return false;
  const reference = token.lastUsedAt ?? token.createdAt;
  return now - new Date(reference).getTime() > STALE_UNUSED_DAYS * 86_400_000;
}

export function validateIssueRequest(req: IssueTokenRequest): string[] {
  const errors: string[] = [];
  if (!req.name?.trim()) errors.push('Name is required');
  if (!req.scopes?.length) errors.push('Select at least one scope');
  if (req.validityDays !== undefined) {
    if (req.validityDays < 1) errors.push('Validity must be at least 1 day');
    if (req.validityDays > MAX_VALIDITY_DAYS) {
      errors.push(`Validity cannot exceed ${MAX_VALIDITY_DAYS} days`);
    }
  }
  return errors;
}
