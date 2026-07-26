/**
 * Agent token management (WO-001 / T-010).
 *
 * This screen exists so a hospital admin can revoke a leaked agent credential
 * without a DBA. That is its whole justification, so revocation is one click
 * from the list rather than buried in a detail view.
 *
 * The plaintext token appears exactly once, at issuance. There is no recovery
 * path — only reissue — because a credential the system can redisplay is one an
 * attacker can read out of the database.
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';

import { issueToken, listTokens, revokeToken } from './api';
import {
  AGENT_SCOPES,
  AgentScope,
  AgentToken,
  DEFAULT_VALIDITY_DAYS,
  SCOPE_LABELS,
  WRITE_SCOPES,
  daysUntilExpiry,
  expiringSoon,
  isWriteCapable,
  unusedTooLong,
  validateIssueRequest,
} from './types';

export default function AgentTokensPage() {
  const queryClient = useQueryClient();
  const [name, setName] = useState('');
  const [scopes, setScopes] = useState<AgentScope[]>([]);
  const [validityDays, setValidityDays] = useState(DEFAULT_VALIDITY_DAYS);
  const [issued, setIssued] = useState<AgentToken | null>(null);
  const [errors, setErrors] = useState<string[]>([]);
  const [confirmingRevoke, setConfirmingRevoke] = useState<string | null>(null);

  const { data: tokens = [], isLoading } = useQuery({
    queryKey: ['agent-tokens'],
    queryFn: listTokens,
  });

  const create = useMutation({
    mutationFn: issueToken,
    onSuccess: (token) => {
      setIssued(token);
      setName('');
      setScopes([]);
      queryClient.invalidateQueries({ queryKey: ['agent-tokens'] });
    },
  });

  const revoke = useMutation({
    mutationFn: revokeToken,
    onSuccess: () => {
      setConfirmingRevoke(null);
      queryClient.invalidateQueries({ queryKey: ['agent-tokens'] });
    },
  });

  const toggleScope = (scope: AgentScope) => {
    setScopes((current) =>
      current.includes(scope) ? current.filter((s) => s !== scope) : [...current, scope],
    );
  };

  const submit = () => {
    const req = { name, scopes, validityDays };
    const found = validateIssueRequest(req);
    setErrors(found);
    if (found.length === 0) create.mutate(req);
  };

  return (
    <div className="space-y-6 p-6">
      <header>
        <h1 className="text-xl font-semibold text-slate-900">Agent API tokens</h1>
        <p className="mt-1 text-sm text-slate-600">
          Credentials for the automated assistant. Revoking one takes that automation
          offline immediately.
        </p>
      </header>

      {/* One-time reveal */}
      {issued?.token && (
        <div className="rounded-lg border border-emerald-300 bg-emerald-50 p-4">
          <h2 className="font-medium text-emerald-900">Copy this token now</h2>
          <p className="mt-1 text-sm text-emerald-800">
            It cannot be shown again. If it is lost you will need to issue a new one.
          </p>
          <div className="mt-3 flex items-center gap-2">
            <code className="flex-1 overflow-x-auto rounded border border-emerald-300 bg-white px-3 py-2 font-mono text-sm">
              {issued.token}
            </code>
            <button
              type="button"
              onClick={() => navigator.clipboard?.writeText(issued.token ?? '')}
              className="rounded bg-emerald-700 px-3 py-2 text-sm font-medium text-white"
            >
              Copy
            </button>
            <button
              type="button"
              onClick={() => setIssued(null)}
              className="rounded border border-emerald-300 px-3 py-2 text-sm text-emerald-900"
            >
              Done
            </button>
          </div>
        </div>
      )}

      {/* Issue */}
      <section className="rounded-lg border border-slate-200 bg-white p-4">
        <h2 className="mb-3 font-medium text-slate-800">Issue a token</h2>

        <label className="block text-sm text-slate-700" htmlFor="token-name">
          Name
        </label>
        <input
          id="token-name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="e.g. WhatsApp scheduling assistant"
          className="mb-3 w-full rounded border border-slate-300 px-3 py-2 text-sm"
        />

        <fieldset className="mb-3">
          <legend className="mb-2 text-sm text-slate-700">Permissions</legend>
          <div className="space-y-1">
            {AGENT_SCOPES.map((scope) => (
              <label key={scope} className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={scopes.includes(scope)}
                  onChange={() => toggleScope(scope)}
                />
                <span>{SCOPE_LABELS[scope]}</span>
                {WRITE_SCOPES.includes(scope) && (
                  <span className="rounded bg-amber-100 px-1.5 py-0.5 text-xs text-amber-800">
                    changes data
                  </span>
                )}
              </label>
            ))}
          </div>
        </fieldset>

        <label className="block text-sm text-slate-700" htmlFor="token-validity">
          Valid for (days)
        </label>
        <input
          id="token-validity"
          type="number"
          min={1}
          max={365}
          value={validityDays}
          onChange={(e) => setValidityDays(Number(e.target.value))}
          className="mb-3 w-32 rounded border border-slate-300 px-3 py-2 text-sm"
        />

        {errors.length > 0 && (
          <ul className="mb-3 list-inside list-disc text-sm text-red-700">
            {errors.map((e) => (
              <li key={e}>{e}</li>
            ))}
          </ul>
        )}

        <button
          type="button"
          onClick={submit}
          disabled={create.isPending}
          className="rounded bg-slate-800 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
        >
          {create.isPending ? 'Issuing…' : 'Issue token'}
        </button>
      </section>

      {/* List */}
      <section className="rounded-lg border border-slate-200 bg-white">
        <h2 className="border-b px-4 py-3 font-medium text-slate-800">Existing tokens</h2>
        {isLoading && <p className="p-4 text-sm text-slate-500">Loading…</p>}
        {!isLoading && tokens.length === 0 && (
          <p className="p-4 text-sm text-slate-500">No tokens yet.</p>
        )}
        <ul>
          {tokens.map((token) => (
            <li key={token.id} className="flex items-start justify-between border-b px-4 py-3">
              <div>
                <div className="flex items-center gap-2">
                  <span className="font-medium text-slate-800">{token.name}</span>
                  <span
                    className={`rounded px-1.5 py-0.5 text-xs ${
                      token.status === 'ACTIVE'
                        ? 'bg-emerald-100 text-emerald-800'
                        : 'bg-slate-200 text-slate-600'
                    }`}
                  >
                    {token.status}
                  </span>
                  {isWriteCapable(token.scopes) && (
                    <span className="rounded bg-amber-100 px-1.5 py-0.5 text-xs text-amber-800">
                      can change data
                    </span>
                  )}
                </div>
                <p className="mt-1 text-xs text-slate-500">
                  {token.scopes.length} permission(s) ·{' '}
                  {token.lastUsedAt
                    ? `last used ${new Date(token.lastUsedAt).toLocaleDateString()}`
                    : 'never used'}{' '}
                  · expires in {daysUntilExpiry(token)} day(s)
                </p>
                {expiringSoon(token) && (
                  <p className="mt-1 text-xs text-amber-700">
                    Expiring soon — issue a replacement before this stops working.
                  </p>
                )}
                {unusedTooLong(token) && (
                  <p className="mt-1 text-xs text-amber-700">
                    Unused for a long time. If nothing needs it, revoke it.
                  </p>
                )}
              </div>

              {token.status === 'ACTIVE' && (
                <div className="shrink-0">
                  {confirmingRevoke === token.id ? (
                    <div className="flex items-center gap-2">
                      <span className="text-xs text-red-700">Take this offline?</span>
                      <button
                        type="button"
                        onClick={() => revoke.mutate(token.id)}
                        disabled={revoke.isPending}
                        className="rounded bg-red-600 px-3 py-1 text-xs font-medium text-white"
                      >
                        Revoke
                      </button>
                      <button
                        type="button"
                        onClick={() => setConfirmingRevoke(null)}
                        className="rounded border border-slate-300 px-3 py-1 text-xs"
                      >
                        Cancel
                      </button>
                    </div>
                  ) : (
                    <button
                      type="button"
                      onClick={() => setConfirmingRevoke(token.id)}
                      className="rounded border border-red-300 px-3 py-1 text-xs text-red-700"
                    >
                      Revoke
                    </button>
                  )}
                </div>
              )}
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
