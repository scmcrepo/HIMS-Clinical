import axios from 'axios';

import type { AgentToken, IssueTokenRequest } from './types';

const BASE = '/api/agent/tokens';

export async function listTokens(): Promise<AgentToken[]> {
  const { data } = await axios.get(BASE);
  return data?.data ?? [];
}

export async function issueToken(req: IssueTokenRequest): Promise<AgentToken> {
  const { data } = await axios.post(BASE, req);
  return data?.data;
}

export async function revokeToken(id: string): Promise<void> {
  await axios.delete(`${BASE}/${id}`);
}
