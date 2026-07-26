import { describe, expect, it } from 'vitest';

import {
  Escalation,
  STALE_AFTER_MS,
  isStale,
  requiresReason,
  sortEscalations,
} from './types';

function esc(over: Partial<Escalation>): Escalation {
  return {
    runId: 'r-1',
    correlationId: 'c-1',
    tenantId: 't-1',
    channel: 'whatsapp',
    reason: 'low_confidence',
    detail: '',
    intent: 'scheduling',
    confidence: 0.5,
    transcript: [],
    proposedActions: [],
    raisedAt: new Date().toISOString(),
    expiresAt: new Date(Date.now() + 60_000).toISOString(),
    ...over,
  };
}

describe('queue ordering', () => {
  it('puts distress ahead of everything else', () => {
    // This is a safety property, not a presentation preference: a queue sorted
    // purely by age buries a frightened caller behind routine bookings.
    const older = esc({ runId: 'old', reason: 'low_confidence',
                        raisedAt: new Date(Date.now() - 600_000).toISOString() });
    const distress = esc({ runId: 'distress', reason: 'distress' });

    expect(sortEscalations([older, distress])[0].runId).toBe('distress');
  });

  it('ranks an explicit request for a person above routine items', () => {
    const routine = esc({ runId: 'routine', reason: 'low_confidence' });
    const asked = esc({ runId: 'asked', reason: 'human_requested' });

    expect(sortEscalations([routine, asked])[0].runId).toBe('asked');
  });

  it('falls back to oldest-first within the same reason', () => {
    const newer = esc({ runId: 'newer', raisedAt: new Date().toISOString() });
    const older = esc({ runId: 'older',
                        raisedAt: new Date(Date.now() - 120_000).toISOString() });

    expect(sortEscalations([newer, older])[0].runId).toBe('older');
  });

  it('does not mutate the input array', () => {
    const items = [esc({ runId: 'a' }), esc({ runId: 'b', reason: 'distress' })];
    const before = items.map((i) => i.runId);
    sortEscalations(items);
    expect(items.map((i) => i.runId)).toEqual(before);
  });

  it('handles an empty queue', () => {
    expect(sortEscalations([])).toEqual([]);
  });
});

describe('staleness', () => {
  it('flags an item waiting past the threshold', () => {
    // A graph paused for a human is a patient waiting; silent indefinite waiting
    // is the worst failure mode of HITL systems.
    const old = esc({ raisedAt: new Date(Date.now() - STALE_AFTER_MS - 1000).toISOString() });
    expect(isStale(old)).toBe(true);
  });

  it('does not flag a fresh item', () => {
    expect(isStale(esc({}))).toBe(false);
  });
});

describe('operator decisions', () => {
  it('requires a reason for correct and override', () => {
    // The reason is both the audit record of why a human disagreed and the
    // training signal for improving the agent.
    expect(requiresReason('correct')).toBe(true);
    expect(requiresReason('override')).toBe(true);
  });

  it('does not require one for approve or take over', () => {
    expect(requiresReason('approve')).toBe(false);
    expect(requiresReason('take_over')).toBe(false);
  });
});
