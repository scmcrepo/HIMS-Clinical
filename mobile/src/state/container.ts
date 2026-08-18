import Constants from "expo-constants";
import * as Crypto from "expo-crypto";
import { PortalApi } from "../core/api";
import { HttpClient, makeCorrelationId } from "../core/http";
import { SessionManager, type SessionLossReason } from "../core/session";
import { SecureTokenStore } from "./secureTokenStore";

/**
 * Composition root.
 *
 * Everything core/ needs injected is bound here exactly once. The circular
 * shape is deliberate and worth reading closely: the HttpClient needs a
 * SessionManager to supply tokens, and the SessionManager needs a PortalApi
 * (built on that same HttpClient) to perform the refresh call. Breaking the
 * cycle with a late-bound closure keeps refresh going through the same
 * transport — same timeouts, same correlation ids — instead of a second ad-hoc
 * fetch that would drift from it.
 */

export interface AppContainer {
  api: PortalApi;
  session: SessionManager;
  http: HttpClient;
}

type LogSink = (event: string, fields: Record<string, unknown>) => void;

const defaultLog: LogSink = (event, fields) => {
  // Client logs land in the OS log buffer, which on older Android any installed
  // app can read. WO-019 §6: event names and codes only, never identifiers.
  if (__DEV__) console.log(`[${event}]`, fields);
};

export function createContainer(options?: {
  baseUrl?: string;
  onSessionLost?: (reason: SessionLossReason) => void;
  log?: LogSink;
}): AppContainer {
  const extra = (Constants.expoConfig?.extra ?? {}) as {
    apiBaseUrl?: string;
    requestTimeoutMs?: number;
  };
  const baseUrl = options?.baseUrl ?? extra.apiBaseUrl ?? "";
  if (!baseUrl) {
    throw new Error(
      "apiBaseUrl is not configured. Set expo.extra.apiBaseUrl in app.json.",
    );
  }

  const log = options?.log ?? defaultLog;
  let session: SessionManager | undefined;
  let api: PortalApi | undefined;

  const http = new HttpClient({
    baseUrl,
    fetchImpl: globalThis.fetch as never,
    getAccessToken: () => session?.getAccessToken() ?? null,
    onUnauthorized: async () => (session ? session.refresh() : false),
    newCorrelationId: () =>
      makeCorrelationId((n) => Crypto.getRandomBytes(n)),
    ...(extra.requestTimeoutMs !== undefined
      ? { timeoutMs: extra.requestTimeoutMs }
      : {}),
    log,
  });

  api = new PortalApi(http);

  session = new SessionManager({
    store: new SecureTokenStore(),
    refreshTokens: (refreshToken) => (api as PortalApi).refresh(refreshToken),
    now: () => new Date(),
    ...(options?.onSessionLost ? { onSessionLost: options.onSessionLost } : {}),
    log,
  });

  return { api, session, http };
}
