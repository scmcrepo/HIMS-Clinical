import type { ApiEnvelope } from "./contracts";
import { networkError, PortalError, timeoutError, toPortalError } from "./errors";

/**
 * The transport.
 *
 * `fetch`, the clock and the id generator are all injected rather than imported.
 * That is what lets the retry, timeout and refresh behaviour be tested under
 * Node in milliseconds instead of against a running backend — and those three
 * behaviours are exactly where a mobile client on Indian mobile data either
 * works or doesn't.
 */

export type FetchLike = (
  input: string,
  init: {
    method: string;
    headers: Record<string, string>;
    body?: string;
    signal?: AbortSignal;
  },
) => Promise<HttpLikeResponse>;

export interface HttpLikeResponse {
  status: number;
  ok: boolean;
  headers: { get(name: string): string | null };
  text(): Promise<string>;
}

export interface HttpDeps {
  baseUrl: string;
  fetchImpl: FetchLike;
  /** Returns the current access token, or null when unauthenticated. */
  getAccessToken: () => string | null;
  /**
   * Called once on a 401 with a token attached. Returns true if a fresh token is
   * now available and the request should be retried exactly once.
   */
  onUnauthorized?: () => Promise<boolean>;
  newCorrelationId: () => string;
  timeoutMs?: number;
  /** Structured client log sink. Never given patient data — see WO-019 §6. */
  log?: (event: string, fields: Record<string, unknown>) => void;
}

export interface RequestOptions {
  method: "GET" | "POST" | "PUT" | "DELETE";
  path: string;
  body?: unknown;
  /** Extra headers; `Idempotency-Key` for booking, `Authorization` overrides. */
  headers?: Record<string, string>;
  /** Skips the Authorization header — used by the OTP endpoints. */
  anonymous?: boolean;
  /** Skips the 401 refresh-and-retry, so refresh itself cannot recurse. */
  noRetryOnUnauthorized?: boolean;
}

export class HttpClient {
  private readonly deps: HttpDeps;

  constructor(deps: HttpDeps) {
    this.deps = deps;
  }

  async request<T>(options: RequestOptions): Promise<T> {
    const correlationId = this.deps.newCorrelationId();
    try {
      return await this.send<T>(options, correlationId, false);
    } catch (err) {
      if (
        err instanceof PortalError &&
        err.isAuthFailure &&
        !options.noRetryOnUnauthorized &&
        !options.anonymous &&
        this.deps.onUnauthorized
      ) {
        const refreshed = await this.deps.onUnauthorized();
        if (refreshed) {
          // New correlation id: the retry is a distinct server-side request and
          // sharing the id would collapse two traces into one.
          return this.send<T>(options, this.deps.newCorrelationId(), true);
        }
      }
      throw err;
    }
  }

  private async send<T>(
    options: RequestOptions,
    correlationId: string,
    isRetry: boolean,
  ): Promise<T> {
    const headers: Record<string, string> = {
      Accept: "application/json",
      "X-Correlation-Id": correlationId,
      ...options.headers,
    };
    if (options.body !== undefined) {
      headers["Content-Type"] = "application/json";
    }
    if (!options.anonymous) {
      const token = this.deps.getAccessToken();
      if (token) headers.Authorization = `Bearer ${token}`;
    }

    const controller = new AbortController();
    const timeoutMs = this.deps.timeoutMs ?? 15_000;
    const timer = setTimeout(() => controller.abort(), timeoutMs);

    let response: HttpLikeResponse;
    try {
      response = await this.deps.fetchImpl(`${this.deps.baseUrl}${options.path}`, {
        method: options.method,
        headers,
        ...(options.body !== undefined
          ? { body: JSON.stringify(options.body) }
          : {}),
        signal: controller.signal,
      });
    } catch (cause) {
      clearTimeout(timer);
      const aborted =
        (cause as { name?: string } | null)?.name === "AbortError" ||
        controller.signal.aborted;
      this.deps.log?.("app.http.failed", {
        path: options.path,
        correlationId,
        reason: aborted ? "timeout" : "network",
        isRetry,
      });
      throw aborted ? timeoutError(correlationId) : networkError(correlationId);
    } finally {
      clearTimeout(timer);
    }

    const serverCorrelationId =
      response.headers.get("X-Correlation-Id") ?? correlationId;
    const raw = await response.text();
    const parsed = safeParse(raw);

    if (!response.ok) {
      throw toPortalError(response.status, parsed, serverCorrelationId);
    }

    // 204 and empty bodies are legitimate (DELETE /portal/appointments/{id}).
    if (raw.length === 0) return undefined as T;

    if (parsed === null || typeof parsed !== "object") {
      throw toPortalError(response.status, null, serverCorrelationId);
    }
    // Every backend response is ApiResponse.ok(message, data); unwrap it here so
    // no caller ever has to know about the envelope.
    return (parsed as ApiEnvelope<T>).data;
  }
}

function safeParse(raw: string): unknown {
  if (raw.length === 0) return null;
  try {
    return JSON.parse(raw);
  } catch {
    // A captive-portal HTML page or a proxy error page. Returning null lets
    // toPortalError fall back to the status code instead of throwing here.
    return null;
  }
}

/**
 * RFC 4122 v4 from injected randomness. Not `Math.random`, because these ids
 * are the join key between a patient's complaint and a server trace; collisions
 * would silently merge two patients' request histories in Loki.
 */
export function makeCorrelationId(
  randomBytes: (n: number) => Uint8Array,
): string {
  const b = randomBytes(16);
  b[6] = ((b[6] as number) & 0x0f) | 0x40;
  b[8] = ((b[8] as number) & 0x3f) | 0x80;
  const hex = Array.from(b, (x) => x.toString(16).padStart(2, "0")).join("");
  return (
    `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-` +
    `${hex.slice(16, 20)}-${hex.slice(20, 32)}`
  );
}
