import { describe, expect, it, vi } from "vitest";
import type { SessionTokens } from "../src/core/contracts";
import { PortalError } from "../src/core/errors";
import {
  InMemoryTokenStore,
  SessionManager,
  type SessionLossReason,
  type StoredSession,
} from "../src/core/session";
import {
  CLINICAL_ROOTS,
  isPersistable,
  isStale,
  QueryKeys,
} from "../src/core/cachePolicy";

const T0 = new Date(2026, 6, 20, 10, 0, 0).getTime();

function tokens(over: Partial<SessionTokens> = {}): SessionTokens {
  return {
    accessToken: "access-1",
    refreshToken: "refresh-1",
    accessTokenExpiresAt: new Date(T0 + 15 * 60_000).toISOString(),
    refreshTokenExpiresAt: new Date(T0 + 7 * 24 * 60 * 60_000).toISOString(),
    ...over,
  };
}

const SCOPE = { patientId: "p1", tenantId: "t1", branchId: "b1" };

function manager(
  refreshTokens: (rt: string) => Promise<SessionTokens>,
  opts: { startAt?: number; onSessionLost?: (r: SessionLossReason) => void } = {},
) {
  let clock = opts.startAt ?? T0;
  const store = new InMemoryTokenStore();
  const sm = new SessionManager({
    store,
    refreshTokens,
    now: () => new Date(clock),
    ...(opts.onSessionLost ? { onSessionLost: opts.onSessionLost } : {}),
  });
  return {
    sm,
    store,
    advance: (ms: number) => {
      clock += ms;
    },
  };
}

describe("session lifecycle", () => {
  it("persists the session and exposes the access token", async () => {
    const { sm, store } = manager(async () => tokens());
    await sm.establish(tokens(), SCOPE);
    expect(sm.isAuthenticated()).toBe(true);
    expect(sm.getAccessToken()).toBe("access-1");
    const stored = (await store.read()) as StoredSession;
    expect(stored.patientId).toBe("p1");
    expect(stored.tenantId).toBe("t1");
  });

  it("reloads a stored session on cold start", async () => {
    const { sm, store } = manager(async () => tokens());
    await store.write({ ...tokens(), ...SCOPE });
    const loaded = await sm.load();
    expect(loaded?.patientId).toBe("p1");
    expect(sm.getAccessToken()).toBe("access-1");
  });

  it("clears everything on logout", async () => {
    const lost: SessionLossReason[] = [];
    const { sm, store } = manager(async () => tokens(), {
      onSessionLost: (r) => lost.push(r),
    });
    await sm.establish(tokens(), SCOPE);
    await sm.logout();
    expect(sm.isAuthenticated()).toBe(false);
    expect(await store.read()).toBeNull();
    expect(lost).toEqual(["logged_out"]);
  });
});

describe("refresh timing", () => {
  it("refreshes inside the skew window rather than waiting for a 401", async () => {
    const { sm, advance } = manager(async () => tokens());
    await sm.establish(tokens(), SCOPE);
    expect(sm.needsRefresh()).toBe(false);
    advance(14 * 60_000 + 1_000); // 1 minute of validity left
    expect(sm.needsRefresh()).toBe(true);
  });

  it("treats an unparseable expiry as needing refresh", async () => {
    const { sm } = manager(async () => tokens());
    await sm.establish(tokens({ accessTokenExpiresAt: "not-a-date" }), SCOPE);
    expect(sm.needsRefresh()).toBe(true);
  });

  it("reports no refresh needed when unauthenticated", async () => {
    const { sm } = manager(async () => tokens());
    expect(sm.needsRefresh()).toBe(false);
    expect(await sm.refresh()).toBe(false);
  });
});

describe("single-flight refresh", () => {
  it("collapses concurrent refreshes into one network call", async () => {
    // WO-017 §4.1 revokes the whole chain on refresh-token reuse. Five parallel
    // 401s must therefore produce ONE refresh, not five, or the patient's own
    // device trips the theft detector.
    let resolveRefresh: ((t: SessionTokens) => void) | undefined;
    const refreshTokens = vi.fn(
      () =>
        new Promise<SessionTokens>((res) => {
          resolveRefresh = res;
        }),
    );
    const { sm } = manager(refreshTokens);
    await sm.establish(tokens(), SCOPE);

    const all = Promise.all([
      sm.refresh(),
      sm.refresh(),
      sm.refresh(),
      sm.refresh(),
      sm.refresh(),
    ]);
    expect(refreshTokens).toHaveBeenCalledTimes(1);

    resolveRefresh?.(tokens({ accessToken: "access-2", refreshToken: "refresh-2" }));
    const results = await all;

    expect(results).toEqual([true, true, true, true, true]);
    expect(refreshTokens).toHaveBeenCalledTimes(1);
    expect(sm.getAccessToken()).toBe("access-2");
  });

  it("allows a fresh refresh after the first one settles", async () => {
    const refreshTokens = vi.fn(async () => tokens({ accessToken: "access-2" }));
    const { sm } = manager(refreshTokens);
    await sm.establish(tokens(), SCOPE);
    await sm.refresh();
    await sm.refresh();
    expect(refreshTokens).toHaveBeenCalledTimes(2);
  });

  it("always sends the newest refresh token, never a consumed one", async () => {
    const seen: string[] = [];
    const refreshTokens = vi.fn(async (rt: string) => {
      seen.push(rt);
      return tokens({ refreshToken: `refresh-${seen.length + 1}` });
    });
    const { sm } = manager(refreshTokens);
    await sm.establish(tokens(), SCOPE);
    await sm.refresh();
    await sm.refresh();
    expect(seen).toEqual(["refresh-1", "refresh-2"]);
  });

  it("keeps the tenant scope across a refresh", async () => {
    const { sm, store } = manager(async () => tokens({ accessToken: "access-2" }));
    await sm.establish(tokens(), SCOPE);
    await sm.refresh();
    const stored = (await store.read()) as StoredSession;
    expect(stored.tenantId).toBe("t1");
    expect(stored.branchId).toBe("b1");
    expect(stored.patientId).toBe("p1");
  });
});

describe("refresh failure handling", () => {
  it("drops the session when the server rejects the refresh token", async () => {
    const lost: SessionLossReason[] = [];
    const { sm, store } = manager(
      async () => {
        throw new PortalError({
          code: "UNAUTHORIZED",
          message: "error.UNAUTHORIZED",
          retryable: false,
          httpStatus: 401,
        });
      },
      { onSessionLost: (r) => lost.push(r) },
    );
    await sm.establish(tokens(), SCOPE);
    expect(await sm.refresh()).toBe(false);
    expect(sm.isAuthenticated()).toBe(false);
    expect(await store.read()).toBeNull();
    expect(lost).toEqual(["refresh_rejected"]);
  });

  it("keeps the session through a transient network failure", async () => {
    // A lift, a tunnel, a dropped tower. Logging the patient out here would mean
    // re-doing an SMS OTP to read their own appointment.
    const { sm } = manager(async () => {
      throw new PortalError({
        code: "NETWORK_UNAVAILABLE",
        message: "error.NETWORK_UNAVAILABLE",
        retryable: true,
      });
    });
    await sm.establish(tokens(), SCOPE);
    expect(await sm.refresh()).toBe(false);
    expect(sm.isAuthenticated()).toBe(true);
    expect(sm.getAccessToken()).toBe("access-1");
  });

  it("does not attempt a refresh once the refresh token itself has expired", async () => {
    const refreshTokens = vi.fn(async () => tokens());
    const lost: SessionLossReason[] = [];
    const { sm, advance } = manager(refreshTokens, {
      onSessionLost: (r) => lost.push(r),
    });
    await sm.establish(tokens(), SCOPE);
    advance(8 * 24 * 60 * 60_000);
    expect(await sm.refresh()).toBe(false);
    expect(refreshTokens).not.toHaveBeenCalled();
    expect(lost).toEqual(["refresh_expired"]);
  });
});

describe("cache policy — clinical data never reaches disk", () => {
  it("persists only the profile, appointments and visit list", () => {
    expect(isPersistable(QueryKeys.profile)).toBe(true);
    expect(isPersistable(QueryKeys.appointments("upcoming"))).toBe(true);
    expect(isPersistable(QueryKeys.visits(0))).toBe(true);
  });

  it("refuses every clinical query key", () => {
    expect(isPersistable(QueryKeys.visitDetail("e1"))).toBe(false);
    expect(isPersistable(QueryKeys.casesheet("e1"))).toBe(false);
    expect(isPersistable(QueryKeys.labReports("e1"))).toBe(false);
    expect(isPersistable(QueryKeys.diagnosticReports("e1"))).toBe(false);
    expect(isPersistable(QueryKeys.attachments("e1"))).toBe(false);
    for (const root of CLINICAL_ROOTS) {
      expect(isPersistable([root, "anything"])).toBe(false);
    }
  });

  it("is an allowlist, so an unknown future key is not persisted by default", () => {
    expect(isPersistable(["prescriptions", "e1"])).toBe(false);
    expect(isPersistable(["bills"])).toBe(false);
    expect(isPersistable([])).toBe(false);
    expect(isPersistable([42])).toBe(false);
  });

  it("distinguishes the visit list from the visit detail", () => {
    // The list is date/doctor/type/status — roughly a paper appointment card.
    // The detail is the diagnosis.
    expect(isPersistable(["visits", 0])).toBe(true);
    expect(isPersistable(["visit", "e1"])).toBe(false);
  });

  it("expires cached appointments quickly because the front desk can cancel them", () => {
    expect(isStale("appointments", T0, T0 + 4 * 60_000)).toBe(false);
    expect(isStale("appointments", T0, T0 + 6 * 60_000)).toBe(true);
    expect(isStale("profile", T0, T0 + 6 * 60_000)).toBe(false);
  });
});
