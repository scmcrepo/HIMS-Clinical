/** Policy discovery & coverage — Screens 1.2, 1.3, 2.1 (WO-013). */

export type PolicyType = 'INDIVIDUAL' | 'FAMILY_FLOATER' | 'PM_JAY' | 'GROUP';
export type Relationship = 'SELF' | 'SPOUSE' | 'CHILD' | 'PARENT' | 'OTHER';
export type PolicyStatus = 'ACTIVE' | 'EXPIRED' | 'LAPSED' | 'SUSPENDED' | 'UNKNOWN';
export type ExclusionKind = 'EXCLUSION' | 'RESTRICTION' | 'SUB_LIMIT';

export const POLICY_TYPE_LABELS: Record<PolicyType, string> = {
  INDIVIDUAL: 'Individual',
  FAMILY_FLOATER: 'Family Floater',
  PM_JAY: 'PM-JAY (Ayushman)',
  GROUP: 'Group',
};

export const POLICY_STATUS_LABELS: Record<PolicyStatus, string> = {
  ACTIVE: 'Active',
  EXPIRED: 'Expired',
  LAPSED: 'Lapsed',
  SUSPENDED: 'Suspended',
  UNKNOWN: 'Not verified',
};

/**
 * Banner colour for the live policy status.
 *
 * <p>UNKNOWN is amber, not red. "We could not reach the payer" and "this policy
 * is dead" are different facts, and showing both in red teaches the desk to
 * treat a transient outage as a refusal.
 */
export type StatusTone = 'positive' | 'warning' | 'negative';

export function statusTone(status: PolicyStatus): StatusTone {
  switch (status) {
    case 'ACTIVE':
      return 'positive';
    case 'UNKNOWN':
      return 'warning';
    default:
      return 'negative';
  }
}

export interface DiscoveredPolicy {
  id: string;
  payerName: string | null;
  tpaName: string | null;
  policyNumberMasked: string | null;
  memberIdMasked: string | null;
  policyType: PolicyType | null;
  policyStartDate: string | null;
  policyEndDate: string | null;
  primaryInsuredName: string | null;
  relationship: Relationship | null;
  linked: boolean;
}

export interface PolicyExclusion {
  kind: ExclusionKind;
  code: string | null;
  description: string;
  limitPaise: number | null;
}

export interface PolicyCoverage {
  id: string;
  policyStatus: PolicyStatus;
  sumInsuredPaise: number | null;
  utilisedPaise: number | null;
  balancePaise: number | null;
  roomRentCapPaise: number | null;
  icuCapPaise: number | null;
  deductiblePaise: number | null;
  roomCategory: string | null;
  coPayBasisPoints: number | null;
  pedWaitingMonths: number | null;
  pedWaitingSatisfied: boolean | null;
  checkedAt: string;
  exclusions: PolicyExclusion[];
}

/**
 * Format paise as Indian rupees.
 *
 * <p>Returns the em-dash for null rather than "₹0". A payer that stated no
 * room-rent cap has not stated a cap of zero, and rendering the two identically
 * could put a patient in a room the policy will not pay for.
 */
export function formatPaise(paise: number | null | undefined): string {
  if (paise === null || paise === undefined) return '—';
  const rupees = paise / 100;
  return `₹${rupees.toLocaleString('en-IN', {
    minimumFractionDigits: rupees % 1 === 0 ? 0 : 2,
    maximumFractionDigits: 2,
  })}`;
}

/** Basis points to a display percentage: 750 renders as "7.5%". */
export function formatCoPay(basisPoints: number | null | undefined): string {
  if (basisPoints === null || basisPoints === undefined) return '—';
  const pct = basisPoints / 100;
  return `${pct % 1 === 0 ? pct : pct.toFixed(1)}%`;
}

/**
 * Split an estimated bill between insurer and patient.
 *
 * <p>The patient's share is computed and the insurer takes the remainder, rather
 * than both being computed independently and rounded. Rounding each separately
 * lets the two shares fail to sum to the bill, which surfaces as a rupee or two
 * unaccounted for on every claim.
 */
export function splitCoPay(
  billPaise: number,
  basisPoints: number | null | undefined,
): { patientPaise: number; insurerPaise: number } {
  if (!basisPoints || basisPoints <= 0) {
    return { patientPaise: 0, insurerPaise: billPaise };
  }
  const patientPaise = Math.round((billPaise * basisPoints) / 10000);
  return { patientPaise, insurerPaise: billPaise - patientPaise };
}

/** Utilisation as a percentage of the sum insured, for the dashboard bar. */
export function utilisationPercent(coverage: PolicyCoverage): number | null {
  const { sumInsuredPaise, utilisedPaise } = coverage;
  if (!sumInsuredPaise || sumInsuredPaise <= 0) return null;
  const used = utilisedPaise ?? 0;
  return Math.min(100, Math.round((used / sumInsuredPaise) * 100));
}

/**
 * Whether the desk may admit against this policy without an override.
 *
 * <p>Deliberately strict: anything other than a verified ACTIVE policy with
 * remaining balance needs a human decision, because the alternative is admitting
 * a cashless patient against cover that will not pay.
 */
export function admissibleWithoutOverride(coverage: PolicyCoverage | null): boolean {
  if (!coverage) return false;
  if (coverage.policyStatus !== 'ACTIVE') return false;
  if (coverage.balancePaise === null) return false;
  return coverage.balancePaise > 0;
}

/** Whether the requested room exceeds the policy's daily cap. */
export function roomExceedsCap(
  roomRatePaise: number,
  coverage: PolicyCoverage | null,
): boolean {
  const cap = coverage?.roomRentCapPaise;
  if (cap === null || cap === undefined) return false; // no cap stated
  return roomRatePaise > cap;
}

/** PED waiting period still running — a common cause of later rejection. */
export function pedWaitingActive(coverage: PolicyCoverage | null): boolean {
  return coverage?.pedWaitingSatisfied === false;
}

export interface ManualPolicyForm {
  insurerName: string;
  policyNumber: string;
  memberId: string;
  tpaName: string;
  policyType: PolicyType | '';
}

/** Screen 1.3 — the fallback when nothing was retrieved digitally. */
export function validateManualPolicy(form: ManualPolicyForm): {
  valid: boolean;
  errors: string[];
} {
  const errors: string[] = [];
  if (!form.insurerName?.trim()) errors.push('Insurer name is required');
  if (!form.policyNumber?.trim() && !form.memberId?.trim()) {
    // Either identifies the policy to the payer; requiring both blocks the
    // common case of a health card that shows only a member id.
    errors.push('Enter a policy number or a member/card ID');
  }
  return { valid: errors.length === 0, errors };
}
