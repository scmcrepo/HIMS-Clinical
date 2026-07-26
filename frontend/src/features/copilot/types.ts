/**
 * Administrative Copilot — types (WO-010, roadmap Phase 5).
 *
 * The escalation payload mirrors what the agent service sends when its graph
 * interrupts. Every field here exists because an operator needs it to act:
 * without the transcript they cannot judge, without the correlation id support
 * cannot pull logs, without the confidence and reason they cannot tell a genuine
 * escalation from a flaky classifier.
 */

export type EscalationReason =
  | 'low_confidence'
  | 'human_requested'
  | 'distress'
  | 'validation_failed'
  | 'tool_failure'
  | 'turn_limit';

export type OperatorAction = 'approve' | 'correct' | 'override' | 'take_over';

export interface TranscriptEntry {
  role: 'user' | 'agent' | 'human_operator' | 'system';
  content: string;
  at?: string;
}

export interface ProposedAction {
  tool: string;
  fingerprint: string;
  /** Redacted server-side. Never assume this is safe to render raw. */
  summary?: Record<string, unknown>;
}

export interface Escalation {
  runId: string;
  correlationId: string;
  tenantId: string;
  branchId?: string | null;
  channel: 'whatsapp' | 'voice' | 'web' | 'test';
  reason: EscalationReason;
  detail: string;
  intent: string;
  confidence: number;
  transcript: TranscriptEntry[];
  proposedActions: ProposedAction[];
  raisedAt: string;
  /** Deadline after which the patient is told a person will call back. */
  expiresAt: string;
}

export interface OperatorDecision {
  runId: string;
  action: OperatorAction;
  /**
   * Required for correct/override. It is both the audit record of why a human
   * disagreed and the training signal for improving the agent — which is why the
   * UI makes it mandatory rather than optional.
   */
  reason?: string;
  reply?: string;
}

/** Escalations awaiting a person for longer than this need chasing. */
export const STALE_AFTER_MS = 10 * 60 * 1000;

export const REASON_LABELS: Record<EscalationReason, string> = {
  low_confidence: 'Unclear request',
  human_requested: 'Patient asked for a person',
  distress: 'Possible distress',
  validation_failed: 'Validation failed',
  tool_failure: 'System error',
  turn_limit: 'Conversation stalled',
};

/**
 * Distress first, always. The ordering here is a safety property, not a
 * presentation preference: a queue sorted purely by age buries a frightened
 * caller behind routine booking questions.
 */
export const REASON_PRIORITY: Record<EscalationReason, number> = {
  distress: 0,
  human_requested: 1,
  tool_failure: 2,
  validation_failed: 3,
  turn_limit: 4,
  low_confidence: 5,
};

export function sortEscalations(items: Escalation[]): Escalation[] {
  return [...items].sort((a, b) => {
    const byReason = REASON_PRIORITY[a.reason] - REASON_PRIORITY[b.reason];
    if (byReason !== 0) return byReason;
    return new Date(a.raisedAt).getTime() - new Date(b.raisedAt).getTime();
  });
}

export function isStale(item: Escalation, now = Date.now()): boolean {
  return now - new Date(item.raisedAt).getTime() > STALE_AFTER_MS;
}

export function requiresReason(action: OperatorAction): boolean {
  return action === 'correct' || action === 'override';
}
