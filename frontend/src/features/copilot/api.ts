/** Copilot data access. Mirrors the existing feature-module service pattern. */
import axios from 'axios';

import type { Escalation, OperatorDecision } from './types';

const BASE = '/api/hitl';

export async function fetchQueue(): Promise<Escalation[]> {
  const { data } = await axios.get(`${BASE}/escalations`);
  return data?.data ?? [];
}

export async function fetchEscalation(runId: string): Promise<Escalation> {
  const { data } = await axios.get(`${BASE}/escalations/${runId}`);
  return data?.data;
}

export async function submitDecision(decision: OperatorDecision): Promise<void> {
  await axios.post(`${BASE}/escalations/${decision.runId}/decision`, decision);
}
