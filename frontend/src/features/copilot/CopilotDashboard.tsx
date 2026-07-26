/**
 * Administrative Copilot (WO-010, roadmap Phase 5).
 *
 * When the agent graph interrupts, an operator lands here. Everything on screen
 * is chosen so they can decide quickly and so the decision is auditable
 * afterwards.
 *
 * Polling rather than websockets for the pilot: the queue is small, the backend
 * needs no new infrastructure, and a dropped socket silently stops updating a
 * screen someone is relying on. Revisit if the queue grows.
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';

import { fetchQueue, submitDecision } from './api';
import {
  OperatorAction,
  REASON_LABELS,
  isStale,
  requiresReason,
  sortEscalations,
} from './types';

const POLL_INTERVAL_MS = 5000;

export default function CopilotDashboard() {
  const queryClient = useQueryClient();
  const [selectedRunId, setSelectedRunId] = useState<string | null>(null);
  const [reason, setReason] = useState('');
  const [reply, setReply] = useState('');

  const { data: queue = [], isLoading } = useQuery({
    queryKey: ['copilot', 'queue'],
    queryFn: fetchQueue,
    refetchInterval: POLL_INTERVAL_MS,
  });

  const sorted = useMemo(() => sortEscalations(queue), [queue]);
  const selected = sorted.find((e) => e.runId === selectedRunId) ?? sorted[0] ?? null;

  const decide = useMutation({
    mutationFn: submitDecision,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['copilot', 'queue'] });
      setReason('');
      setReply('');
      setSelectedRunId(null);
    },
  });

  const act = (action: OperatorAction) => {
    if (!selected) return;
    // Enforced here as well as server-side: the reason is the audit record of
    // why a human disagreed, and the signal used to improve the agent.
    if (requiresReason(action) && !reason.trim()) return;
    decide.mutate({ runId: selected.runId, action, reason: reason.trim() || undefined,
                    reply: reply.trim() || undefined });
  };

  const reasonMissing = !!selected && requiresReason('correct') && !reason.trim();

  return (
    <div className="flex h-full gap-4 p-4">
      {/* Queue */}
      <aside className="w-80 shrink-0 overflow-y-auto rounded-lg border border-slate-200 bg-white">
        <header className="flex items-center justify-between border-b px-4 py-3">
          <h2 className="font-semibold text-slate-800">Waiting</h2>
          <span className="rounded-full bg-slate-100 px-2 py-0.5 text-sm text-slate-600">
            {sorted.length}
          </span>
        </header>

        {isLoading && <p className="p-4 text-sm text-slate-500">Loading…</p>}
        {!isLoading && sorted.length === 0 && (
          <p className="p-4 text-sm text-slate-500">Nothing waiting.</p>
        )}

        <ul>
          {sorted.map((item) => (
            <li key={item.runId}>
              <button
                type="button"
                onClick={() => setSelectedRunId(item.runId)}
                className={`w-full border-b px-4 py-3 text-left hover:bg-slate-50 ${
                  selected?.runId === item.runId ? 'bg-slate-50' : ''
                }`}
              >
                <div className="flex items-center gap-2">
                  {item.reason === 'distress' && (
                    <span className="rounded bg-red-100 px-1.5 py-0.5 text-xs font-semibold text-red-700">
                      URGENT
                    </span>
                  )}
                  {isStale(item) && (
                    <span className="rounded bg-amber-100 px-1.5 py-0.5 text-xs text-amber-800">
                      waiting
                    </span>
                  )}
                  <span className="text-sm font-medium text-slate-800">
                    {REASON_LABELS[item.reason]}
                  </span>
                </div>
                <p className="mt-1 truncate text-xs text-slate-500">
                  {item.channel} · {new Date(item.raisedAt).toLocaleTimeString()}
                </p>
              </button>
            </li>
          ))}
        </ul>
      </aside>

      {/* Detail */}
      <section className="flex-1 overflow-y-auto rounded-lg border border-slate-200 bg-white">
        {!selected && <p className="p-6 text-slate-500">Select an item to review.</p>}

        {selected && (
          <div className="flex h-full flex-col">
            <header className="border-b px-6 py-4">
              <h1 className="text-lg font-semibold text-slate-900">
                {REASON_LABELS[selected.reason]}
              </h1>
              <p className="mt-1 text-sm text-slate-600">{selected.detail}</p>
              <dl className="mt-3 flex flex-wrap gap-x-6 gap-y-1 text-xs text-slate-500">
                <div>
                  <dt className="inline">Intent: </dt>
                  <dd className="inline font-medium text-slate-700">{selected.intent}</dd>
                </div>
                <div>
                  <dt className="inline">Confidence: </dt>
                  <dd className="inline font-medium text-slate-700">
                    {(selected.confidence * 100).toFixed(0)}%
                  </dd>
                </div>
                <div>
                  {/* Support needs this to pull logs and traces for the run. */}
                  <dt className="inline">Correlation: </dt>
                  <dd className="inline font-mono text-slate-700">{selected.correlationId}</dd>
                </div>
              </dl>
            </header>

            <div className="flex-1 space-y-4 px-6 py-4">
              <section>
                <h3 className="mb-2 text-sm font-semibold text-slate-700">Conversation</h3>
                <div className="space-y-2">
                  {selected.transcript.map((entry, i) => (
                    <div
                      key={i}
                      className={`rounded-lg px-3 py-2 text-sm ${
                        entry.role === 'user'
                          ? 'bg-slate-100 text-slate-800'
                          : 'bg-blue-50 text-slate-800'
                      }`}
                    >
                      <span className="mr-2 text-xs font-medium uppercase text-slate-500">
                        {entry.role}
                      </span>
                      {entry.content}
                    </div>
                  ))}
                </div>
              </section>

              {selected.proposedActions.length > 0 && (
                <section>
                  <h3 className="mb-2 text-sm font-semibold text-slate-700">
                    What the agent wanted to do
                  </h3>
                  <ul className="space-y-1">
                    {selected.proposedActions.map((action) => (
                      <li
                        key={action.fingerprint}
                        className="rounded border border-slate-200 px-3 py-2 font-mono text-xs text-slate-700"
                      >
                        {action.tool}
                      </li>
                    ))}
                  </ul>
                </section>
              )}
            </div>

            <footer className="space-y-3 border-t bg-slate-50 px-6 py-4">
              <textarea
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="Why are you correcting or overriding? (required for those actions)"
                rows={2}
                className="w-full rounded border border-slate-300 px-3 py-2 text-sm"
              />
              <input
                value={reply}
                onChange={(e) => setReply(e.target.value)}
                placeholder="Optional reply to send to the patient"
                className="w-full rounded border border-slate-300 px-3 py-2 text-sm"
              />
              <div className="flex flex-wrap gap-2">
                <button
                  type="button"
                  onClick={() => act('approve')}
                  disabled={decide.isPending}
                  className="rounded bg-emerald-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
                >
                  Approve
                </button>
                <button
                  type="button"
                  onClick={() => act('correct')}
                  disabled={decide.isPending || reasonMissing}
                  className="rounded bg-amber-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
                >
                  Correct
                </button>
                <button
                  type="button"
                  onClick={() => act('override')}
                  disabled={decide.isPending || reasonMissing}
                  className="rounded bg-slate-700 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
                >
                  Override
                </button>
                <button
                  type="button"
                  onClick={() => act('take_over')}
                  disabled={decide.isPending}
                  className="rounded border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 disabled:opacity-50"
                >
                  Take over
                </button>
              </div>
              {reasonMissing && (
                <p className="text-xs text-amber-700">
                  Correcting or overriding needs a reason — it is the audit record.
                </p>
              )}
            </footer>
          </div>
        )}
      </section>
    </div>
  );
}
