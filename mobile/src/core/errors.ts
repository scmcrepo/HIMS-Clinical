import type { ApiErrorPayload, PortalErrorCode } from "./contracts";

/**
 * A failure the UI can act on.
 *
 * `correlationId` is carried through deliberately: when a patient calls the
 * hospital to say "the app wouldn't let me book", that string is what turns an
 * unfalsifiable complaint into a server-side trace. It is shown in the error UI.
 */
export class PortalError extends Error {
  readonly code: PortalErrorCode;
  readonly retryable: boolean;
  readonly httpStatus: number | null;
  readonly correlationId: string | null;
  readonly fieldErrors: Record<string, string>;

  constructor(init: {
    code: PortalErrorCode;
    message: string;
    retryable?: boolean;
    httpStatus?: number | null;
    correlationId?: string | null;
    fieldErrors?: Record<string, string>;
  }) {
    super(init.message);
    this.name = "PortalError";
    this.code = init.code;
    this.retryable = init.retryable ?? false;
    this.httpStatus = init.httpStatus ?? null;
    this.correlationId = init.correlationId ?? null;
    this.fieldErrors = init.fieldErrors ?? {};
  }

  /** True when a fresh access token might fix it. */
  get isAuthFailure(): boolean {
    return this.httpStatus === 401 || this.code === "UNAUTHORIZED";
  }

  /** True when the whole session is unrecoverable and the app must log out. */
  get requiresReauthentication(): boolean {
    return (
      this.code === "IDENTITY_TOKEN_REQUIRED" ||
      this.code === "PATIENT_NOT_IN_CANDIDATE_SET"
    );
  }
}

const KNOWN_CODES = new Set<PortalErrorCode>([
  "OTP_RATE_LIMITED",
  "OTP_INVALID",
  "OTP_EXPIRED",
  "OTP_ATTEMPTS_EXCEEDED",
  "IDENTITY_TOKEN_REQUIRED",
  "PATIENT_NOT_IN_CANDIDATE_SET",
  "REGISTRATION_CAP_REACHED",
  "UNAUTHORIZED",
  "SLOT_FULL",
  "BOOKING_WINDOW_EXCEEDED",
  "CANCEL_WINDOW_CLOSED",
  "APPOINTMENT_ALREADY_CHECKED_IN",
  "APPOINTMENT_CANCELLED",
  "NOT_FOUND",
  "VALIDATION_FAILED",
  "NETWORK_UNAVAILABLE",
  "TIMEOUT",
  "UNKNOWN",
]);

function isPortalErrorCode(value: unknown): value is PortalErrorCode {
  return typeof value === "string" && KNOWN_CODES.has(value as PortalErrorCode);
}

/**
 * Turns whatever the server actually sent into a PortalError.
 *
 * Written defensively on purpose. A reverse proxy returning an HTML 502, a
 * captive wifi portal returning a login page with status 200, and a backend
 * returning a well-formed envelope all have to land somewhere sensible — and
 * the one thing that must never happen is a raw server string being shown to a
 * patient, because backend messages sometimes contain identifiers.
 */
export function toPortalError(
  status: number | null,
  body: unknown,
  correlationId: string | null,
): PortalError {
  let code: PortalErrorCode = "UNKNOWN";
  let retryable = status !== null && status >= 500;
  let fieldErrors: Record<string, string> | undefined;
  let serverMessage: string | null = null;

  if (body && typeof body === "object") {
    const rawMsg = (body as { message?: unknown }).message;
    if (typeof rawMsg === "string" && rawMsg.trim().length > 0) {
      serverMessage = rawMsg.trim();
    }
    const data = (body as { data?: unknown }).data;
    if (data && typeof data === "object") {
      const payload = data as Partial<ApiErrorPayload>;
      if (isPortalErrorCode(payload.code)) {
        code = payload.code;
      }
      if (typeof payload.retryable === "boolean") {
        retryable = payload.retryable;
      }
      if (payload.fieldErrors && typeof payload.fieldErrors === "object") {
        fieldErrors = payload.fieldErrors as Record<string, string>;
      }
      if (typeof payload.message === "string" && payload.message.trim().length > 0) {
        serverMessage = payload.message.trim();
      }
    }
  }

  if (code === "UNKNOWN") {
    if (status === 401) code = "UNAUTHORIZED";
    else if (status === 404) code = "NOT_FOUND";
    else if (status === 400 || status === 422) code = "VALIDATION_FAILED";
    else if (status === 429) code = "OTP_RATE_LIMITED";
  }

  return new PortalError({
    code,
    message: code !== "UNKNOWN" ? `error.${code}` : (serverMessage || "error.UNKNOWN"),
    retryable,
    httpStatus: status,
    correlationId,
    ...(fieldErrors ? { fieldErrors } : {}),
  });
}

export function networkError(correlationId: string | null): PortalError {
  return new PortalError({
    code: "NETWORK_UNAVAILABLE",
    message: "error.NETWORK_UNAVAILABLE",
    retryable: true,
    correlationId,
  });
}

export function timeoutError(correlationId: string | null): PortalError {
  return new PortalError({
    code: "TIMEOUT",
    message: "error.TIMEOUT",
    retryable: true,
    correlationId,
  });
}
