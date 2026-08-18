import { describe, expect, it, vi } from "vitest";
import {
  HttpClient,
  makeCorrelationId,
  type FetchLike,
  type HttpLikeResponse,
} from "../src/core/http";
import { PortalError } from "../src/core/errors";
import { PortalApi } from "../src/core/api";

function response(
  status: number,
  body: unknown,
  headers: Record<string, string> = {},
): HttpLikeResponse {
  return {
    status,
    ok: status >= 200 && status < 300,
    headers: { get: (n: string) => headers[n] ?? null },
    text: async () => (body === undefined ? "" : JSON.stringify(body)),
  };
}

interface Recorded {
  url: string;
  method: string;
  headers: Record<string, string>;
  body?: string;
}

function recorder(
  handler: (
    call: Recorded,
    index: number,
  ) => HttpLikeResponse | Promise<HttpLikeResponse>,
) {
  const calls: Recorded[] = [];
  const fetchImpl: FetchLike = async (url, init) => {
    const call: Recorded = {
      url,
      method: init.method,
      headers: init.headers,
      ...(init.body !== undefined ? { body: init.body } : {}),
    };
    calls.push(call);
    return handler(call, calls.length - 1);
  };
  return { calls, fetchImpl };
}

/** Awaits a request expected to fail and returns the typed error. */
async function expectError(promise: Promise<unknown>): Promise<PortalError> {
  try {
    await promise;
  } catch (e) {
    return e as PortalError;
  }
  throw new Error("expected the request to reject, but it resolved");
}

function client(
  fetchImpl: FetchLike,
  over: Partial<{
    getAccessToken: () => string | null;
    onUnauthorized: () => Promise<boolean>;
    timeoutMs: number;
  }> = {},
): HttpClient {
  let n = 0;
  return new HttpClient({
    baseUrl: "https://hospital.example/api",
    fetchImpl,
    getAccessToken: over.getAccessToken ?? (() => "access-1"),
    ...(over.onUnauthorized ? { onUnauthorized: over.onUnauthorized } : {}),
    newCorrelationId: () => `corr-${(n += 1)}`,
    ...(over.timeoutMs !== undefined ? { timeoutMs: over.timeoutMs } : {}),
  });
}

describe("envelope handling", () => {
  it("unwraps ApiResponse.ok so callers never see the envelope", async () => {
    const { fetchImpl } = recorder(() =>
      response(200, { message: "OK", data: { patientId: "p1" } }),
    );
    const result = await client(fetchImpl).request<{ patientId: string }>({
      method: "GET",
      path: "/portal/me",
    });
    expect(result).toEqual({ patientId: "p1" });
  });

  it("tolerates an empty body on DELETE", async () => {
    const { fetchImpl } = recorder(() => response(204, undefined));
    const result = await client(fetchImpl).request({
      method: "DELETE",
      path: "/portal/appointments/a1",
    });
    expect(result).toBeUndefined();
  });

  it("treats an HTML captive-portal page as an error, not a success", async () => {
    const fetchImpl: FetchLike = async () => ({
      status: 200,
      ok: true,
      headers: { get: () => null },
      text: async () => "<html>Sign in to wifi</html>",
    });
    const err = await expectError(
      client(fetchImpl).request({ method: "GET", path: "/portal/me" }),
    );
    expect(err.code).toBe("UNKNOWN");
  });
});

describe("headers", () => {
  it("attaches a correlation id and a bearer token", async () => {
    const { calls, fetchImpl } = recorder(() =>
      response(200, { message: "", data: 1 }),
    );
    await client(fetchImpl).request({ method: "GET", path: "/portal/me" });
    expect(calls[0]?.headers["X-Correlation-Id"]).toBe("corr-1");
    expect(calls[0]?.headers.Authorization).toBe("Bearer access-1");
  });

  it("omits the bearer token on anonymous calls", async () => {
    const { calls, fetchImpl } = recorder(() =>
      response(200, { message: "", data: 1 }),
    );
    await client(fetchImpl).request({
      method: "POST",
      path: "/portal/auth/otp/request",
      body: { mobile: "9876543210" },
      anonymous: true,
    });
    expect(calls[0]?.headers.Authorization).toBeUndefined();
    expect(calls[0]?.headers["Content-Type"]).toBe("application/json");
  });

  it("gives the retry a fresh correlation id so two traces stay distinct", async () => {
    const { calls, fetchImpl } = recorder((_c, i) =>
      i === 0
        ? response(401, { message: "", data: { code: "UNAUTHORIZED" } })
        : response(200, { message: "", data: "ok" }),
    );
    const c = client(fetchImpl, { onUnauthorized: async () => true });
    await c.request({ method: "GET", path: "/portal/me" });
    expect(calls).toHaveLength(2);
    expect(calls[0]?.headers["X-Correlation-Id"]).toBe("corr-1");
    expect(calls[1]?.headers["X-Correlation-Id"]).toBe("corr-2");
  });
});

describe("error mapping", () => {
  it("maps the server error envelope to a typed code", async () => {
    const { fetchImpl } = recorder(() =>
      response(409, {
        message: "Slot full",
        data: { code: "SLOT_FULL", retryable: false },
      }),
    );
    const err = await expectError(
      client(fetchImpl).request({
        method: "POST",
        path: "/portal/appointments",
        body: {},
      }),
    );
    expect(err).toBeInstanceOf(PortalError);
    expect(err.code).toBe("SLOT_FULL");
    expect(err.retryable).toBe(false);
    // The message is an i18n key, never server prose, because backend messages
    // can carry identifiers.
    expect(err.message).toBe("error.SLOT_FULL");
  });

  it("falls back to the status code when the body has no envelope", async () => {
    const { fetchImpl } = recorder(() => response(404, { oops: true }));
    const err = await expectError(
      client(fetchImpl).request({ method: "GET", path: "/portal/visits/x" }),
    );
    expect(err.code).toBe("NOT_FOUND");
  });

  it("marks 5xx retryable and 4xx not", async () => {
    const { fetchImpl: f500 } = recorder(() => response(503, {}));
    const e500 = await expectError(
      client(f500).request({ method: "GET", path: "/x" }),
    );
    expect(e500.retryable).toBe(true);

    const { fetchImpl: f400 } = recorder(() => response(400, {}));
    const e400 = await expectError(
      client(f400).request({ method: "GET", path: "/x" }),
    );
    expect(e400.retryable).toBe(false);
    expect(e400.code).toBe("VALIDATION_FAILED");
  });

  it("prefers the server's correlation id when it echoes one back", async () => {
    const { fetchImpl } = recorder(() =>
      response(500, {}, { "X-Correlation-Id": "server-side-id" }),
    );
    const err = await expectError(
      client(fetchImpl).request({ method: "GET", path: "/x" }),
    );
    expect(err.correlationId).toBe("server-side-id");
  });

  it("reports a network failure distinctly from a server error", async () => {
    const fetchImpl: FetchLike = async () => {
      throw new TypeError("Network request failed");
    };
    const err = await expectError(
      client(fetchImpl).request({ method: "GET", path: "/x" }),
    );
    expect(err.code).toBe("NETWORK_UNAVAILABLE");
    expect(err.retryable).toBe(true);
  });

  it("reports an abort as a timeout", async () => {
    const fetchImpl: FetchLike = async () => {
      const e = new Error("aborted");
      e.name = "AbortError";
      throw e;
    };
    const err = await expectError(
      client(fetchImpl).request({ method: "GET", path: "/x" }),
    );
    expect(err.code).toBe("TIMEOUT");
  });

  it("aborts a request that exceeds the timeout budget", async () => {
    const fetchImpl: FetchLike = (_url, init) =>
      new Promise((_res, rej) => {
        init.signal?.addEventListener("abort", () => {
          const e = new Error("aborted");
          e.name = "AbortError";
          rej(e);
        });
      });
    const err = await expectError(
      client(fetchImpl, { timeoutMs: 20 }).request({ method: "GET", path: "/x" }),
    );
    expect(err.code).toBe("TIMEOUT");
  });

  it("carries field errors through for form display", async () => {
    const { fetchImpl } = recorder(() =>
      response(400, {
        message: "",
        data: {
          code: "VALIDATION_FAILED",
          retryable: false,
          fieldErrors: { firstName: "too long" },
        },
      }),
    );
    const err = await expectError(
      client(fetchImpl).request({
        method: "POST",
        path: "/portal/patients/register",
        body: {},
      }),
    );
    expect(err.fieldErrors.firstName).toBe("too long");
  });

  it("flags the codes that mean the whole session is unrecoverable", () => {
    const reauth = new PortalError({
      code: "PATIENT_NOT_IN_CANDIDATE_SET",
      message: "x",
    });
    expect(reauth.requiresReauthentication).toBe(true);
    const transient = new PortalError({ code: "SLOT_FULL", message: "x" });
    expect(transient.requiresReauthentication).toBe(false);
  });
});

describe("401 handling", () => {
  it("retries exactly once after a successful refresh", async () => {
    const { calls, fetchImpl } = recorder((_c, i) =>
      i === 0
        ? response(401, { message: "", data: { code: "UNAUTHORIZED" } })
        : response(200, { message: "", data: "fresh" }),
    );
    const onUnauthorized = vi.fn(async () => true);
    const result = await client(fetchImpl, { onUnauthorized }).request({
      method: "GET",
      path: "/portal/me",
    });
    expect(result).toBe("fresh");
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
    expect(calls).toHaveLength(2);
  });

  it("does not retry when the refresh fails", async () => {
    const { calls, fetchImpl } = recorder(() =>
      response(401, { message: "", data: { code: "UNAUTHORIZED" } }),
    );
    const onUnauthorized = vi.fn(async () => false);
    const err = await expectError(
      client(fetchImpl, { onUnauthorized }).request({
        method: "GET",
        path: "/portal/me",
      }),
    );
    expect(err.code).toBe("UNAUTHORIZED");
    expect(calls).toHaveLength(1);
  });

  it("does not retry a second time if the retry also 401s", async () => {
    const { calls, fetchImpl } = recorder(() =>
      response(401, { message: "", data: { code: "UNAUTHORIZED" } }),
    );
    const onUnauthorized = vi.fn(async () => true);
    const err = await expectError(
      client(fetchImpl, { onUnauthorized }).request({ method: "GET", path: "/x" }),
    );
    expect(err.code).toBe("UNAUTHORIZED");
    expect(calls).toHaveLength(2);
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it("never refreshes on the refresh endpoint itself", async () => {
    const { calls, fetchImpl } = recorder(() =>
      response(401, { message: "", data: { code: "UNAUTHORIZED" } }),
    );
    const onUnauthorized = vi.fn(async () => true);
    const api = new PortalApi(client(fetchImpl, { onUnauthorized }));
    const err = await expectError(api.refresh("rt-1"));
    expect(err.code).toBe("UNAUTHORIZED");
    expect(onUnauthorized).not.toHaveBeenCalled();
    expect(calls).toHaveLength(1);
  });
});

describe("PortalApi surface", () => {
  it("never sends a patientId query parameter on reads", async () => {
    const { calls, fetchImpl } = recorder(() =>
      response(200, { message: "", data: [] }),
    );
    const api = new PortalApi(client(fetchImpl));
    await api.getProfile();
    await api.listAppointments("upcoming");
    await api.listVisits();
    await api.listConsultants({ q: "rao" });
    for (const call of calls) {
      expect(call.url).not.toContain("patientId");
    }
  });

  it("requires an idempotency key to book and sends no patientId", async () => {
    const { calls, fetchImpl } = recorder(() =>
      response(201, { message: "", data: { appointmentId: "a1" } }),
    );
    const api = new PortalApi(client(fetchImpl));
    await api.bookAppointment(
      { providerId: "c1", slotId: "s1", appointmentDate: "2026-07-22" },
      "idem-key-1",
    );
    expect(calls[0]?.headers["Idempotency-Key"]).toBe("idem-key-1");
    expect(JSON.parse(calls[0]?.body ?? "{}")).not.toHaveProperty("patientId");
  });

  it("normalises the mobile number before it leaves the device", async () => {
    const { calls, fetchImpl } = recorder(() =>
      response(200, { message: "", data: {} }),
    );
    const api = new PortalApi(client(fetchImpl));
    await api.requestOtp("+91 98765 43210");
    expect(JSON.parse(calls[0]?.body ?? "{}").mobile).toBe("9876543210");
  });

  it("sends the identity token, not the access token, when exchanging a session", async () => {
    const { calls, fetchImpl } = recorder(() =>
      response(200, { message: "", data: {} }),
    );
    const api = new PortalApi(client(fetchImpl));
    await api.exchangeSession("identity-abc", {
      patientId: "p1",
      tenantId: "t1",
      branchId: "b1",
    });
    expect(calls[0]?.headers.Authorization).toBe("Bearer identity-abc");
  });

  it("passes the date through to the availability endpoint", async () => {
    const { calls, fetchImpl } = recorder(() =>
      response(200, { message: "", data: [] }),
    );
    const api = new PortalApi(client(fetchImpl));
    await api.getAvailability("c1", "2026-07-22");
    expect(calls[0]?.url).toContain("/portal/consultants/c1/availability?date=2026-07-22");
  });
});

describe("correlation ids", () => {
  it("produces a well-formed v4 uuid", () => {
    const id = makeCorrelationId((n) => new Uint8Array(n).fill(0xab));
    expect(id).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/,
    );
  });

  it("varies with the randomness supplied", () => {
    let counter = 0;
    const gen = () =>
      makeCorrelationId((n) => {
        counter += 1;
        return new Uint8Array(n).fill(counter);
      });
    expect(gen()).not.toBe(gen());
  });
});
