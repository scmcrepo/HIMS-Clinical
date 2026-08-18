/**
 * Client-side validation, mirroring the backend constraints exactly.
 *
 * The regexes are copied from the Patient entity and RegisterPatientRequest
 * rather than invented, because a client that accepts what the server rejects
 * produces a form the patient cannot submit and cannot understand — and a client
 * that rejects what the server accepts silently loses legitimate registrations
 * (names with apostrophes, single-character surnames).
 */

/** Patient.isContactNumberValidForSms() — exactly ten digits, no country code. */
const MOBILE_RE = /^\d{10}$/;

/**
 * Letters and spaces only, 1-60 first / 1-40 last.
 *
 * This is `RegisterPatientRequest`'s actual pattern (`^[a-zA-Z\s]+$`), NOT the
 * requirement document's `^[a-zA-Z][a-zA-Z.\-\s]*$`. The two disagree: the PRD
 * allows dots and hyphens and the server does not. Following the PRD would let
 * "Jean-Pierre" and "St. John" pass on the phone and then be rejected on submit
 * with a server-side message — the exact failure this file exists to prevent.
 *
 * The server is the authority. If dots and hyphens should be allowed — and for
 * Indian and Anglo names they arguably should — the fix belongs in
 * RegisterPatientRequest, and this constant follows it.
 */
const NAME_RE = /^[a-zA-Z\s]+$/;

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

const OTP_RE = /^\d{6}$/;

export const BLOOD_GROUPS = [
  "A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-",
] as const;

export const SALUTATIONS = ["Mr", "Mrs", "Ms", "Dr", "Master"] as const;

export const GENDERS = ["MALE", "FEMALE", "OTHER"] as const;

export type FieldErrors = Record<string, string>;

/**
 * Normalises what patients actually type: "+91 98765 43210", "098765-43210",
 * "(987) 654 3210". All of these are the same number and all of them are
 * rejected by a bare ten-digit check, which is a needless dead end on the very
 * first screen of the app.
 */
export function normaliseMobile(raw: string): string {
  let digits = raw.replace(/\D/g, "");
  if (digits.length === 12 && digits.startsWith("91")) digits = digits.slice(2);
  if (digits.length === 11 && digits.startsWith("0")) digits = digits.slice(1);
  return digits;
}

export function validateMobile(raw: string): FieldErrors {
  const digits = normaliseMobile(raw);
  if (digits.length === 0) return { mobile: "validation.mobile.required" };
  if (!MOBILE_RE.test(digits)) return { mobile: "validation.mobile.format" };
  // Indian mobile numbering: subscriber numbers begin 6-9. A number starting
  // 0-5 is a landline or a typo and will never receive the OTP.
  if (!/^[6-9]/.test(digits)) return { mobile: "validation.mobile.notMobile" };
  return {};
}

export function validateOtp(code: string): FieldErrors {
  const trimmed = code.trim();
  if (trimmed.length === 0) return { code: "validation.otp.required" };
  if (!OTP_RE.test(trimmed)) return { code: "validation.otp.format" };
  return {};
}

export interface RegistrationFormValues {
  salutation?: string;
  firstName: string;
  lastName: string;
  gender: string;
  dateOfBirth: string;
  mobile: string;
  email?: string;
  bloodGroup?: string;
  address?: string;
}

export function validateRegistration(
  values: RegistrationFormValues,
  now: Date,
): FieldErrors {
  const errors: FieldErrors = {};

  const first = values.firstName?.trim() ?? "";
  if (first.length === 0) errors.firstName = "validation.firstName.required";
  else if (first.length > 60) errors.firstName = "validation.firstName.tooLong";
  else if (!NAME_RE.test(first)) errors.firstName = "validation.firstName.format";

  const last = values.lastName?.trim() ?? "";
  if (last.length === 0) errors.lastName = "validation.lastName.required";
  else if (last.length > 40) errors.lastName = "validation.lastName.tooLong";
  else if (!NAME_RE.test(last)) errors.lastName = "validation.lastName.format";

  if (!GENDERS.includes(values.gender as (typeof GENDERS)[number])) {
    errors.gender = "validation.gender.required";
  }

  Object.assign(errors, validateDateOfBirth(values.dateOfBirth, now));
  Object.assign(errors, validateMobile(values.mobile));

  const email = values.email?.trim();
  if (email && !EMAIL_RE.test(email)) errors.email = "validation.email.format";

  const bg = values.bloodGroup?.trim();
  if (bg && !BLOOD_GROUPS.includes(bg as (typeof BLOOD_GROUPS)[number])) {
    errors.bloodGroup = "validation.bloodGroup.invalid";
  }

  if (values.address && values.address.length > 500) {
    errors.address = "validation.address.tooLong";
  }

  return errors;
}

export function validateDateOfBirth(value: string, now: Date): FieldErrors {
  if (!value || value.trim().length === 0) {
    return { dateOfBirth: "validation.dob.required" };
  }
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value.trim());
  if (!m) return { dateOfBirth: "validation.dob.format" };

  const year = Number(m[1]);
  const month = Number(m[2]);
  const day = Number(m[3]);
  const parsed = new Date(year, month - 1, day);
  // Rejects 2026-02-31, which Date otherwise rolls forward to 3 March.
  if (
    parsed.getFullYear() !== year ||
    parsed.getMonth() !== month - 1 ||
    parsed.getDate() !== day
  ) {
    return { dateOfBirth: "validation.dob.format" };
  }
  if (parsed.getTime() > now.getTime()) {
    return { dateOfBirth: "validation.dob.future" };
  }
  if (year < now.getFullYear() - 130) {
    return { dateOfBirth: "validation.dob.implausible" };
  }
  return {};
}

export function hasErrors(errors: FieldErrors): boolean {
  return Object.keys(errors).length > 0;
}
