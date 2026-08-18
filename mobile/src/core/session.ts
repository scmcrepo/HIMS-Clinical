import type { SessionTokens } from "./contracts";
import { PortalError } from "./errors";

/**
 * Token lifecycle.
 *
 * The single-flight refresh is the load-bearing part. WO-017 §4.1 rotates
 * refresh tokens and treats reuse of a consumed one as credential theft — it
 * revokes the entire chain and pages someone. A dashboard that fires five
 * queries at once, all 401ing on an expired access token, would send five
 * refreshes with the same token and trip that detector against the patient's own
 * device. So concurrent refreshes must collapse into one awaited promise.
 */

/**
 * Storage contract. Implemented over expo-secure-store (Keychain / Keystore) in
 * `src/state/secureTokenStore.ts`; the in-memory implementation below is for
 * tests only. Deliberately an interface so that `core/` stays free of native
 * imports and so that an accidental AsyncStorage implementation would have to be
 * written on purpose rather than reached for by habit.
 */
export interface TokenStore {
  read(): Promise<StoredSession | null>;
  write(session: StoredSession): Promise<void>;
  clear(): Promise<void>;
}

export interface StoredSession {
  accessToken: string;
  refreshToken: string;
  accessTokenExpiresAt: string;
  refreshTokenExpiresAt: string;
  patientId: string;
  tenantId: string;
  branchId: string;
}

export class InMemoryTokenStore implements TokenStore {
  private value: StoredSession | null = null;
  async read(): Promise<StoredSession | null> {
    return this.value;
  }
  async write(session: StoredSession): Promise<void> {
    this.value = session;
  }
  async clear(): Promise<void> {
    this.value = null;
  }
}

/** Refresh this long before expiry rather than waiting for a 401. */
export const REFRESH_SKEW_MS = 60_000;

export interface SessionManagerDeps {
  store: TokenStore;
  /** Calls POST /portal/auth/refresh. Must not itself retry on 401. */
  refreshTokens: (refreshToken: string) => Promise<SessionTokens>;
  now: () => Date;
  /** Invoked when the session is unrecoverable and the UI must return to login. */
  onSessionLost?: (reason: SessionLossReason) => void;
  log?: (event: string, fields: Record<string, unknown>) => void;
}

export type SessionLossReason =
  | "refresh_expired"
  | "refresh_rejected"
  | "logged_out";

export class SessionManager {
  private readonly deps: SessionManagerDeps;
  private current: StoredSession | null = null;
  private inFlight: Promise<boolean> | null = null;

  constructor(deps: SessionManagerDeps) {
    this.deps = deps;
  }

  async load(): Promise<StoredSession | null> {
    this.current = await this.deps.store.read();
    return this.current;
  }

  getAccessToken(): string | null {
    return this.current?.accessToken ?? null;
  }

  getSession(): StoredSession | null {
    return this.current;
  }

  isAuthenticated(): boolean {
    return this.current !== null;
  }

  async establish(
    tokens: SessionTokens,
    scope: { patientId: string; tenantId: string; branchId: string },
  ): Promise<void> {
    const session: StoredSession = { ...tokens, ...scope };
    this.current = session;
    await this.deps.store.write(session);
    this.deps.log?.("app.auth.session_established", {
      tenantId: scope.tenantId,
      branchId: scope.branchId,
    });
  }

  /** True when the access token is expired or within the skew window. */
  needsRefresh(): boolean {
    if (!this.current) return false;
    const expiry = Date.parse(this.current.accessTokenExpiresAt);
    if (Number.isNaN(expiry)) return true;
    return this.deps.now().getTime() >= expiry - REFRESH_SKEW_MS;
  }

  private refreshTokenExpired(): boolean {
    if (!this.current) return true;
    const expiry = Date.parse(this.current.refreshTokenExpiresAt);
    if (Number.isNaN(expiry)) return true;
    return this.deps.now().getTime() >= expiry;
  }

  /**
   * Refreshes, collapsing concurrent callers onto one request.
   *
   * Returns true when a usable access token is available afterwards. Callers
   * treat false as "the patient must log in again"; they never retry, because a
   * loop here is a loop against the reuse detector.
   */
  async refresh(): Promise<boolean> {
    if (this.inFlight) return this.inFlight;
    this.inFlight = this.doRefresh().finally(() => {
      this.inFlight = null;
    });
    return this.inFlight;
  }

  private async doRefresh(): Promise<boolean> {
    const session = this.current;
    if (!session) return false;

    if (this.refreshTokenExpired()) {
      await this.forget("refresh_expired");
      return false;
    }

    try {
      const tokens = await this.deps.refreshTokens(session.refreshToken);
      const next: StoredSession = {
        ...tokens,
        patientId: session.patientId,
        tenantId: session.tenantId,
        branchId: session.branchId,
      };
      this.current = next;
      await this.deps.store.write(next);
      return true;
    } catch (err) {
      // A 401 from refresh means the token was consumed, revoked, or the chain
      // was killed by reuse detection. None of those are retryable.
      if (err instanceof PortalError && !err.retryable) {
        this.deps.log?.("app.token.refresh_failed", { code: err.code });
        await this.forget("refresh_rejected");
        return false;
      }
      // A network blip: keep the session so the patient is not logged out by a
      // tunnel or a lift.
      this.deps.log?.("app.token.refresh_failed", { code: "transient" });
      return false;
    }
  }

  async logout(): Promise<void> {
    await this.forget("logged_out");
  }

  private async forget(reason: SessionLossReason): Promise<void> {
    this.current = null;
    await this.deps.store.clear();
    this.deps.onSessionLost?.(reason);
  }
}
