/** ABHA verification & creation — Screen 1.1 (WO-012 / AB-002). */

/** Which identifier the front desk offered for the OTP challenge. */
export type OtpChannel = 'AADHAAR' | 'MOBILE';

/**
 * Linkage lifecycle. Mirrors AbhaService's constants exactly — if these drift,
 * the badge silently stops rendering rather than failing loudly.
 */
export type LinkageState = 'PENDING_OTP' | 'LINKED' | 'FAILED';

export interface AbhaLinkage {
  id: string;
  patientId: string;
  /** Masked by the server, e.g. XX-XXXX-XXXX-0123. Never the full number. */
  abhaNumberMasked: string | null;
  abhaAddress: string | null;
  linkageState: LinkageState;
  linkedAt: string | null;
  failureCode: string | null;
}

export interface StartEnrolmentRequest {
  patientId: string;
  channel: OtpChannel;
  /** Aadhaar or mobile. Sent once, never retained client-side. */
  loginId: string;
}

export interface VerifyOtpRequest {
  otp: string;
  mobile?: string;
}

export const AADHAAR_LENGTH = 12;
export const MOBILE_LENGTH = 10;
export const OTP_MIN_LENGTH = 4;
export const OTP_MAX_LENGTH = 8;

export const CHANNEL_LABELS: Record<OtpChannel, string> = {
  AADHAAR: 'Aadhaar (OTP to linked mobile)',
  MOBILE: 'Mobile number',
};

export const STATE_LABELS: Record<LinkageState, string> = {
  PENDING_OTP: 'Awaiting OTP',
  LINKED: 'ABHA verified',
  FAILED: 'Verification failed',
};

export interface ValidationResult {
  valid: boolean;
  errors: string[];
}

/**
 * Validate before hitting the gateway.
 *
 * <p>ABDM rate-limits OTP requests per identifier, so a typo that reaches the
 * gateway costs the patient one of a small number of attempts. Catching the
 * obvious cases here is not redundant with server validation — it protects a
 * scarce resource the patient owns.
 */
export function validateStartRequest(req: StartEnrolmentRequest): ValidationResult {
  const errors: string[] = [];

  if (!req.patientId) {
    errors.push('Select a patient first');
  }

  const digits = (req.loginId ?? '').replace(/\s/g, '');
  if (!digits) {
    errors.push(req.channel === 'AADHAAR' ? 'Aadhaar number is required' : 'Mobile number is required');
  } else if (!/^\d+$/.test(digits)) {
    errors.push('Only digits are allowed');
  } else if (req.channel === 'AADHAAR' && digits.length !== AADHAAR_LENGTH) {
    errors.push(`Aadhaar must be ${AADHAAR_LENGTH} digits`);
  } else if (req.channel === 'MOBILE' && digits.length !== MOBILE_LENGTH) {
    errors.push(`Mobile must be ${MOBILE_LENGTH} digits`);
  }

  return { valid: errors.length === 0, errors };
}

export function validateOtp(otp: string): ValidationResult {
  const errors: string[] = [];
  const trimmed = (otp ?? '').trim();

  if (!trimmed) {
    errors.push('Enter the OTP sent to the patient');
  } else if (!/^\d+$/.test(trimmed)) {
    errors.push('OTP must be digits only');
  } else if (trimmed.length < OTP_MIN_LENGTH || trimmed.length > OTP_MAX_LENGTH) {
    errors.push(`OTP must be ${OTP_MIN_LENGTH}–${OTP_MAX_LENGTH} digits`);
  }

  return { valid: errors.length === 0, errors };
}

/** Drives the green checkmark on the patient master profile. */
export function isVerified(linkage: AbhaLinkage | null | undefined): boolean {
  return linkage?.linkageState === 'LINKED' && !!linkage.abhaNumberMasked;
}

/** The active linkage from a history list, if the patient has one. */
export function activeLinkage(history: AbhaLinkage[]): AbhaLinkage | null {
  return history.find((l) => l.linkageState === 'LINKED') ?? null;
}

/**
 * Whether a fresh enrolment may be started.
 *
 * <p>A patient already holding an ABHA must not be enrolled again — that mints a
 * second national id for one person. The server enforces this too; the button is
 * disabled so the desk never sees an avoidable error.
 */
export function canStartEnrolment(history: AbhaLinkage[]): boolean {
  return activeLinkage(history) === null;
}

/**
 * The ABHA card is downloadable only once an identity actually exists.
 */
export function canDownloadCard(linkage: AbhaLinkage | null | undefined): boolean {
  return isVerified(linkage);
}

/**
 * Turn a failure code into something a receptionist can act on.
 *
 * <p>Codes come from the server as exception type names, deliberately without
 * gateway message text, because ABDM error bodies can echo the submitted
 * Aadhaar. That means the mapping lives here rather than being passed through.
 */
export function failureMessage(code: string | null | undefined): string {
  if (!code) return 'Verification failed. Please try again.';
  switch (code) {
    case 'ConsentRequiredException':
      return 'Record the patient’s ABHA consent before linking.';
    case 'BusinessRuleViolationException':
      return 'This patient already has a linked ABHA, or the enrolment has expired.';
    case 'GovApiException':
      return 'ABDM did not accept the request. Check the number and try again.';
    default:
      return 'Verification failed. Please try again.';
  }
}
