/**
 * Manual TPA insurance desk — types and pure logic (WO-020 / ID-006).
 *
 * Everything here is a pure function so it can be tested without a DOM or a
 * server. The stage-unlocking rules and the card-expiry comparison are the two
 * places where a subtle bug is invisible on screen but wrong in the claim, so
 * they live here rather than inline in a component.
 *
 * All amounts are in PAISE, matching the backend. Components format for display
 * at the edge; nothing in this file works in rupees.
 */

/** The nine workflow stages. Order here is display order. */
export const WORKFLOW_STAGES = [
  'PREAUTHORISATION',
  'PREAUTHORISATION_APPROVAL',
  'PREAUTHORISATION_REJECTED',
  'ENHANCEMENT_REQUEST',
  'ENHANCEMENT_APPROVAL',
  'ENHANCEMENT_REJECTED',
  'CHECK_LIST_ENTRY',
  'DISPATCH_ENTRY',
  'DISALLOWANCE_ENTRY',
] as const;

export type WorkflowStage = (typeof WORKFLOW_STAGES)[number];

/**
 * Progression rank, mirroring `InsuranceWorkflowStage.rank()` on the server.
 *
 * Alternative outcomes of the same desk step share a rank — approval and
 * rejection are both "the TPA answered".
 */
export const STAGE_RANK: Record<WorkflowStage, number> = {
  PREAUTHORISATION: 0,
  PREAUTHORISATION_APPROVAL: 1,
  PREAUTHORISATION_REJECTED: 1,
  ENHANCEMENT_REQUEST: 2,
  ENHANCEMENT_APPROVAL: 3,
  ENHANCEMENT_REJECTED: 3,
  CHECK_LIST_ENTRY: 4,
  DISPATCH_ENTRY: 5,
  DISALLOWANCE_ENTRY: 6,
};

/** The seven steps drawn in the timeline sidebar, in order. */
export const TIMELINE_STEPS = [
  { key: 'preauth', label: 'Preauthorise', stage: 'PREAUTHORISATION' },
  { key: 'preauthApproval', label: 'Preauthorise Approval', stage: 'PREAUTHORISATION_APPROVAL' },
  { key: 'enhancement', label: 'Enhancement Request', stage: 'ENHANCEMENT_REQUEST' },
  { key: 'enhancementApproval', label: 'Enhancement Approval', stage: 'ENHANCEMENT_APPROVAL' },
  { key: 'checkList', label: 'Check-list Entry', stage: 'CHECK_LIST_ENTRY' },
  { key: 'dispatch', label: 'Dispatch Entry', stage: 'DISPATCH_ENTRY' },
  { key: 'disallowance', label: 'Disallowance Entry', stage: 'DISALLOWANCE_ENTRY' },
] as const;

export type TimelineStepKey = (typeof TIMELINE_STEPS)[number]['key'];

export const STAGE_LABELS: Record<WorkflowStage, string> = {
  PREAUTHORISATION: 'Preauthorise',
  PREAUTHORISATION_APPROVAL: 'Preauthorise Approval',
  PREAUTHORISATION_REJECTED: 'Preauthorise Rejected',
  ENHANCEMENT_REQUEST: 'Enhancement Requested',
  ENHANCEMENT_APPROVAL: 'Enhancement Approval',
  ENHANCEMENT_REJECTED: 'Enhancement Rejected',
  CHECK_LIST_ENTRY: 'Check-list Entry',
  DISPATCH_ENTRY: 'Dispatched',
  DISALLOWANCE_ENTRY: 'Disallowance Entry',
};

export type ModeOfCommunication = 'FAX' | 'MAIL';
export type ModeOfDispatch = 'COURIER' | 'EMAIL';
export type TpaDecision = 'APPROVED' | 'REJECTED';
export type CourierVendor =
  | 'PROFESSION_COURIER'
  | 'FIRST_FLIGHT'
  | 'ST_COURIER'
  | 'DTDC'
  | 'BLUE_DART';

export const COURIER_LABELS: Record<CourierVendor, string> = {
  PROFESSION_COURIER: 'Profession Courier',
  FIRST_FLIGHT: 'First Flight',
  ST_COURIER: 'ST Courier',
  DTDC: 'DTDC',
  BLUE_DART: 'Blue Dart',
};

export interface ChecklistItem {
  name: string;
  toBeSubmit: number;
  submitted: number;
  nonSubmission?: string | null;
}

export interface ChequeReceipt {
  id?: string | null;
  chequeNo: string;
  accountNo?: string | null;
  chequeDate?: string | null;
  drawnOn?: string | null;
  payableAt?: string | null;
  /** Paise. */
  amount: number;
  authorisedBy?: string | null;
}

export interface StageTimestamps {
  preauth: string | null;
  preauthApproval: string | null;
  enhancement: string | null;
  enhancementApproval: string | null;
  checkList: string | null;
  dispatch: string | null;
  disallowance: string | null;
}

/** The full desk payload from `GET /insurance/{id}/desk`. */
export interface InsuranceDesk {
  id: string;
  patientId: string | null;
  billId: string | null;
  encounterId: string | null;
  insurerName: string;
  tpaName: string | null;
  policyNumber: string | null;
  memberId: string | null;
  policyType: string | null;

  patientNo?: string | null;
  patientName?: string | null;
  patientGender?: string | null;
  patientAge?: string | null;
  billAmount?: number | null;

  /** Null on records created before the desk workflow existed. */
  currentStage: WorkflowStage | null;
  currentStageLabel: string | null;
  stageTimestamps: StageTimestamps;

  billLinked: boolean;
  cardExpired: boolean;
  effectiveApprovedLimit: number | null;

  cardValidity: string | null;
  preAuthType: string | null;
  preauthCommunicationToTpa: ModeOfCommunication | null;
  preauthFaxNo: string | null;
  preauthMailId: string | null;
  preauthAppliedDate: string | null;
  preauthRequestedAmount: number | null;

  claimNo: string | null;
  preauthApprovalStatus: TpaDecision | null;
  preauthDateOfApproval: string | null;
  preauthCommunicationByTpa: ModeOfCommunication | null;
  preauthApproveFaxNo: string | null;
  preauthApproveMailId: string | null;
  preauthApprovedLimit: number | null;
  preauthRejectionReason: string | null;

  enhancementType: string | null;
  enhancementAppliedDate: string | null;
  enhancementRequestedAmount: number | null;
  enhancementCommunicationToTpa: ModeOfCommunication | null;
  enhancementFaxNo: string | null;
  enhancementMailId: string | null;
  reasonForEnhancement: string | null;

  enhancementApprovalStatus: TpaDecision | null;
  enhancementDateOfApproval: string | null;
  enhancementCommunicationByTpa: ModeOfCommunication | null;
  enhancementApprovedLimit: number | null;
  enhancementRejectionReason: string | null;

  checklist: { checklists?: ChecklistItem[] } | null;

  modeOfDispatch: ModeOfDispatch | null;
  courier: CourierVendor | null;
  dispatchDate: string | null;
  dispatchedBy: string | null;
  dispatchMailId: string | null;
  podNo: string | null;
  reasonForDelay: string | null;

  cheques: ChequeReceipt[];
  /** Paise. */
  totalReceived: number;
}

// ── Pure logic ──────────────────────────────────────────────────────────────

/** Whether the claim has reached or passed `stage`. Null current stage = legacy record. */
export function hasReached(current: WorkflowStage | null, stage: WorkflowStage): boolean {
  if (!current) return false;
  return STAGE_RANK[current] >= STAGE_RANK[stage];
}

/**
 * The stages that may be worked next, mirroring
 * `InsuranceWorkflowStage.nextStages()` on the server.
 *
 * This is an explicit map rather than a "current rank + 1" shortcut, because
 * the shortcut gets the common case wrong: most claims never need an
 * enhancement, and the desk goes straight from pre-auth approval (rank 1) to
 * the checklist (rank 4). Any rank arithmetic locks that path.
 */
export const NEXT_STAGES: Record<WorkflowStage, readonly WorkflowStage[]> = {
  PREAUTHORISATION: ['PREAUTHORISATION_APPROVAL', 'PREAUTHORISATION_REJECTED'],
  // Straight to the checklist when no enhancement is needed.
  PREAUTHORISATION_APPROVAL: ['ENHANCEMENT_REQUEST', 'CHECK_LIST_ENTRY'],
  PREAUTHORISATION_REJECTED: [],
  ENHANCEMENT_REQUEST: ['ENHANCEMENT_APPROVAL', 'ENHANCEMENT_REJECTED'],
  ENHANCEMENT_APPROVAL: ['CHECK_LIST_ENTRY'],
  // A rejected enhancement still proceeds: the claim is filed for the
  // originally sanctioned amount.
  ENHANCEMENT_REJECTED: ['CHECK_LIST_ENTRY'],
  CHECK_LIST_ENTRY: ['DISPATCH_ENTRY'],
  DISPATCH_ENTRY: ['DISALLOWANCE_ENTRY'],
  DISALLOWANCE_ENTRY: [],
};

/**
 * Which timeline steps the clerk may open.
 *
 * A step is open when it has already been reached — corrections must always be
 * possible, a clerk fixing a fax number after dispatch is routine — or when it
 * is one of the stages the server would accept next. Two additional gates:
 *
 * - Enhancement needs a linked bill. The server enforces this too; the UI
 *   disabling it is a courtesy, not the guard.
 * - Nothing is open past a rejected pre-auth, because the claim is dead.
 */
export function unlockedSteps(desk: {
  currentStage: WorkflowStage | null;
  billLinked: boolean;
}): Record<TimelineStepKey, boolean> {
  const current = desk.currentStage;
  const preauthRejected = current === 'PREAUTHORISATION_REJECTED';
  const next = current ? NEXT_STAGES[current] : [];

  const open = (stage: WorkflowStage) => {
    if (preauthRejected) return false;
    return hasReached(current, stage) || next.includes(stage);
  };

  return {
    // Always available: a legacy record with no recorded stage has to start
    // somewhere, and a correction has to be possible at any point.
    preauth: !preauthRejected,
    preauthApproval: open('PREAUTHORISATION_APPROVAL'),
    enhancement: open('ENHANCEMENT_REQUEST') && desk.billLinked,
    enhancementApproval: open('ENHANCEMENT_APPROVAL'),
    checkList: open('CHECK_LIST_ENTRY'),
    dispatch: open('DISPATCH_ENTRY'),
    disallowance: open('DISALLOWANCE_ENTRY'),
  };
}

/** Why a step is locked, for the tooltip. Null when it is open. */
export function lockReason(
  step: TimelineStepKey,
  desk: { currentStage: WorkflowStage | null; billLinked: boolean },
): string | null {
  if (unlockedSteps(desk)[step]) return null;
  if (desk.currentStage === 'PREAUTHORISATION_REJECTED') {
    return 'The TPA rejected this pre-authorisation. The claim cannot proceed.';
  }
  if (step === 'enhancement' && !desk.billLinked) {
    return 'Link the patient\u2019s credit bill before requesting an enhancement.';
  }
  if (step === 'enhancementApproval') {
    return 'Raise the enhancement request first.';
  }
  return 'Complete the earlier stages first.';
}

/**
 * Card expiry, compared date-only.
 *
 * A card valid *through* today is not expired — expiry is end-of-day. Comparing
 * timestamps instead would flag a card as lapsed from midnight on its last
 * valid day.
 */
export function isCardExpired(cardValidity: string | null | undefined, today = new Date()): boolean {
  if (!cardValidity) return false;
  const validity = new Date(`${cardValidity.slice(0, 10)}T00:00:00`);
  if (Number.isNaN(validity.getTime())) return false;
  const todayMidnight = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  return validity.getTime() < todayMidnight.getTime();
}

/** Days until the card lapses. Negative when already lapsed, null when no expiry is recorded. */
export function daysUntilCardExpiry(
  cardValidity: string | null | undefined,
  today = new Date(),
): number | null {
  if (!cardValidity) return null;
  const validity = new Date(`${cardValidity.slice(0, 10)}T00:00:00`);
  if (Number.isNaN(validity.getTime())) return null;
  const todayMidnight = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  return Math.round((validity.getTime() - todayMidnight.getTime()) / 86_400_000);
}

/**
 * The communication endpoint required by the chosen mode.
 * Returns an error message, or null when valid — mirroring the server rule so
 * the clerk finds out before the round trip, not after.
 */
export function validateCommunication(
  mode: ModeOfCommunication | null | undefined,
  faxNo: string | null | undefined,
  mailId: string | null | undefined,
): string | null {
  if (!mode) return 'Select how this was sent to the TPA.';
  if (mode === 'FAX' && !faxNo?.trim()) return 'Enter the TPA fax number.';
  if (mode === 'MAIL' && !mailId?.trim()) return 'Enter the TPA mail id.';
  return null;
}

/** Server-side rule mirrored: approvals need an amount, rejections need a reason. */
export function validateDecision(
  decision: TpaDecision | null | undefined,
  approvedLimit: number | null | undefined,
  rejectionReason: string | null | undefined,
): string | null {
  if (!decision) return 'Record the TPA\u2019s decision.';
  if (decision === 'APPROVED' && (approvedLimit == null || approvedLimit <= 0)) {
    return 'Enter the amount the TPA sanctioned.';
  }
  if (decision === 'REJECTED' && !rejectionReason?.trim()) {
    return 'Record why the TPA declined.';
  }
  return null;
}

/** Server-side rule mirrored: courier needs a vendor and POD, email needs a destination. */
export function validateDispatch(dispatch: {
  modeOfDispatch: ModeOfDispatch | null | undefined;
  courier?: CourierVendor | null;
  podNo?: string | null;
  dispatchMailId?: string | null;
  dispatchedBy?: string | null;
  reasonForDelay?: string | null;
}): string | null {
  if (!dispatch.modeOfDispatch) return 'Select the mode of dispatch.';
  if (dispatch.modeOfDispatch === 'COURIER') {
    if (!dispatch.courier) return 'Select the courier used.';
    if (!dispatch.podNo?.trim()) {
      return 'Enter the POD / consignment number \u2014 it is the only proof of delivery.';
    }
  }
  if (dispatch.modeOfDispatch === 'EMAIL' && !dispatch.dispatchMailId?.trim()) {
    return 'Enter the destination mail id.';
  }
  if (!dispatch.dispatchedBy?.trim()) {
    return 'Enter the Dispatched By name.';
  }
  return null;
}

/** Total of the cheque grid, in paise. Blank or malformed amounts count as zero. */
export function totalChequeAmount(cheques: readonly ChequeReceipt[]): number {
  return cheques.reduce((sum, c) => sum + (Number.isFinite(c.amount) ? c.amount : 0), 0);
}

export interface ChecklistSummary {
  total: number;
  complete: number;
  shortfallItems: number;
  /** Names of the documents still short, for the dispatch warning. */
  pending: string[];
}

/** Checklist completeness, for the badge on the timeline and the dispatch warning. */
export function summariseChecklist(items: readonly ChecklistItem[]): ChecklistSummary {
  const pending: string[] = [];
  let complete = 0;
  for (const item of items) {
    const expected = Number(item.toBeSubmit) || 0;
    const got = Number(item.submitted) || 0;
    if (got >= expected) complete += 1;
    else pending.push(item.name);
  }
  return {
    total: items.length,
    complete,
    shortfallItems: pending.length,
    pending,
  };
}

/**
 * Outstanding against the sanctioned limit, in paise.
 *
 * Null — not zero — when nothing has been sanctioned. Zero would render as
 * "fully recovered" on a claim the TPA has not even answered.
 */
export function outstandingAgainstLimit(desk: {
  effectiveApprovedLimit: number | null;
  totalReceived: number;
}): number | null {
  if (desk.effectiveApprovedLimit == null) return null;
  return desk.effectiveApprovedLimit - (desk.totalReceived ?? 0);
}

/** Paise to a displayable rupee string. */
export function formatPaise(paise: number | null | undefined): string {
  if (paise == null || !Number.isFinite(paise)) return '\u2014';
  return (paise / 100).toLocaleString('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 2,
  });
}

/** Rupees entered by a clerk to paise, rounded. Non-numeric input becomes null. */
export function rupeesToPaise(value: string | number | null | undefined): number | null {
  if (value === null || value === undefined || value === '') return null;
  const n = typeof value === 'number' ? value : Number(String(value).replace(/,/g, ''));
  if (!Number.isFinite(n)) return null;
  return Math.round(n * 100);
}
