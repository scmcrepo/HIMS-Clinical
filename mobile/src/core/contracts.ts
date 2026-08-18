/**
 * Wire contracts for the portal API (WO-017 auth, WO-018 data).
 *
 * These are hand-written rather than generated because the backend endpoints do
 * not exist yet — this file IS the agreed contract, and WO-019 R1 tracks
 * replacing it with types generated from the springdoc OpenAPI document once
 * `/portal/**` ships. When that happens, this file should be deleted, not edited
 * to match: two sources of truth for a wire format is how a client silently
 * starts sending a field the server stopped reading.
 *
 * Naming follows the backend records exactly (`fromTime`, not `startTime`;
 * `numberSequenceSuffix`, not `patientNumber`) so a mismatch is a compile error
 * rather than an undefined at runtime.
 */

/** The backend wraps every response: `ApiResponse.ok(message, data)`. */
export interface ApiEnvelope<T> {
  message: string;
  data: T;
  success?: boolean;
}

/** Error payloads reuse the agent chain's shape: `{message, data:{code, retryable}}`. */
export interface ApiErrorPayload {
  code: PortalErrorCode;
  retryable: boolean;
  /** Present only on validation failures; keyed by field name. */
  fieldErrors?: Record<string, string>;
}

export type PortalErrorCode =
  // WO-017 — auth
  | "OTP_RATE_LIMITED"
  | "OTP_INVALID"
  | "OTP_EXPIRED"
  | "OTP_ATTEMPTS_EXCEEDED"
  | "IDENTITY_TOKEN_REQUIRED"
  | "PATIENT_NOT_IN_CANDIDATE_SET"
  | "REGISTRATION_CAP_REACHED"
  | "UNAUTHORIZED"
  // WO-018 — data
  | "SLOT_FULL"
  | "BOOKING_WINDOW_EXCEEDED"
  | "CANCEL_WINDOW_CLOSED"
  | "APPOINTMENT_ALREADY_CHECKED_IN"
  | "APPOINTMENT_CANCELLED"
  | "NOT_FOUND"
  | "VALIDATION_FAILED"
  // client-side only
  | "NETWORK_UNAVAILABLE"
  | "TIMEOUT"
  | "UNKNOWN";

// ---------------------------------------------------------------------------
// Authentication (WO-017)
// ---------------------------------------------------------------------------

export interface OtpRequestBody {
  /** 10 digits, no country code — matches Patient.isContactNumberValidForSms(). */
  mobile: string;
}

/**
 * Deliberately says nothing about whether the number exists. Revealing that a
 * number is registered at a hospital is itself a disclosure — a person's
 * attendance at a de-addiction or fertility clinic is inferable from existence
 * alone. WO-017 §4.1.
 */
export interface OtpRequestResult {
  challengeId: string;
  expiresInSeconds: number;
  resendAvailableInSeconds: number;
}

export interface OtpVerifyBody {
  challengeId: string;
  mobile: string;
  code: string;
}

export interface OtpVerifyResult {
  /** Scope PORTAL_IDENTITY, ~10 min. Proves possession of the number only. */
  identityToken: string;
  identityTokenExpiresAt: string;
  candidates: HospitalCandidate[];
}

export interface HospitalCandidate {
  tenantId: string;
  tenantName: string;
  address: string | null;
  contactNumber: string | null;
  logoUrl: string | null;
  patients: PatientCandidate[];
  branches: BranchSummary[];
}

export interface PatientCandidate {
  patientId: string;
  /** Server-composed from salutation + first + last; the client never assembles PII. */
  fullName: string;
  age: number | null;
  gender: string;
  numberSequenceSuffix: string | null;
  photoUrl: string | null;
}

export interface BranchSummary {
  branchId: string;
  name: string;
  code: string | null;
  address: string | null;
  contactNumber: string | null;
  isDefault: boolean;
  isActive: boolean;
}

export interface SessionExchangeBody {
  patientId: string;
  tenantId: string;
  branchId: string;
}

export interface SessionTokens {
  accessToken: string;
  refreshToken: string;
  /** ISO-8601. The client refreshes on a skew ahead of this, never on a timer. */
  accessTokenExpiresAt: string;
  refreshTokenExpiresAt: string;
}

// ---------------------------------------------------------------------------
// Portal data (WO-018)
// ---------------------------------------------------------------------------

export interface PatientProfile {
  patientId: string;
  fullName: string;
  age: number | null;
  gender: string;
  bloodGroup: string | null;
  numberSequenceSuffix: string | null;
  photoUrl: string | null;
  selfRegistered: boolean;
  tenantName: string;
  branchName: string;
}

export interface Consultant {
  consultantId: string;
  fullName: string;
  specialisation: string | null;
  qualification: string | null;
  departmentName: string | null;
  consultantType: "INTERNAL" | "VISITING" | null;
  photoUrl: string | null;
}

/** Mirrors SlotAvailabilityResponse in api/appointment/response/ exactly. */
export interface SlotAvailability {
  slotId: string;
  /** "HH:mm" or "HH:mm:ss" — Java LocalTime serialises either way. */
  fromTime: string;
  toTime: string;
  maxPatients: number;
  bookedCount: number;
  availableCount: number;
  isAvailable: boolean;
}

export type AppointmentStatus =
  | "BOOKED"
  | "RESCHEDULED"
  | "CHECKED_IN"
  | "CANCELLED"
  | "COMPLETED"
  | "NO_SHOW";

export interface Appointment {
  appointmentId: string;
  /** ISO date, "YYYY-MM-DD". */
  appointmentDate: string;
  fromTime: string;
  toTime: string;
  status: AppointmentStatus;
  consultantId: string;
  consultantName: string;
  departmentName: string | null;
  branchName: string | null;
  notes: string | null;
}

export interface BookAppointmentBody {
  /**
   * No patientId. WO-018 §4.1 — the server takes it from the token, so a
   * tampered body cannot book on someone else's behalf.
   */
  providerId: string;
  slotId: string;
  appointmentDate: string;
  notes?: string | null;
}

export type EncounterStatus =
  | "CHECKED_IN"
  | "CASESHEET_RECORDED"
  | "CONSULTATION_STARTED"
  | "BILLING_DONE";

export interface VisitSummary {
  encounterId: string;
  /** ISO instant. */
  visitDate: string;
  consultantName: string | null;
  encounterType: "OP" | "IP";
  status: EncounterStatus;
}

export interface VisitDetail extends VisitSummary {
  branchName: string | null;
  departmentName: string | null;
  /** Encrypted at rest; decrypted server-side for the owning patient only. */
  diagnosis: string | null;
  counts: {
    casesheet: number;
    labReports: number;
    diagnosticReports: number;
    attachments: number;
  };
}

export interface CaseSheetField {
  key: string;
  label: string;
  /** Template field type: TEXT, NUMBER, DATE, SELECT, TEXTAREA, CHECKBOX. */
  type: string;
  value: string | number | boolean | null;
}

export interface CaseSheetSection {
  templateName: string;
  visitType: "OP" | "IP";
  recordedBy: string | null;
  recordedAt: string | null;
  fields: CaseSheetField[];
}

export interface DiagnosticReportLine {
  reportId: string;
  testName: string;
  value: string | null;
  unit: string | null;
  referenceRange: string | null;
  /** e.g. NORMAL, ABNORMAL, HIGH, LOW. */
  result: string | null;
  /** Always true in portal responses — unapproved rows never leave the server. */
  isApproved: boolean;
}

export interface DiagnosticOrderGroup {
  orderId: string;
  sequenceNumber: string | null;
  orderDate: string;
  status: "PENDING" | "RESULTED" | "CANCELLED";
  lines: DiagnosticReportLine[];
}

export interface AttachmentMeta {
  attachmentId: string;
  fileName: string;
  contentType: string;
  category: string | null;
  sizeBytes: number | null;
  uploadedAt: string;
}

export interface SignedDownload {
  url: string;
  /** ISO instant, 5 minutes out. WO-018 §4.2. */
  expiresAt: string;
}

// ---------------------------------------------------------------------------
// Self-registration (WO-017 PT-006)
// ---------------------------------------------------------------------------

export interface RegisterPatientBody {
  tenantId: string;
  branchId: string;
  salutation?: string | null;
  firstName: string;
  lastName: string;
  gender: "MALE" | "FEMALE" | "OTHER";
  /** "YYYY-MM-DD". */
  dateOfBirth: string;
  mobile: string;
  email?: string | null;
  bloodGroup?: string | null;
  address?: string | null;
  /** Version of the consent text the patient accepted. WO-017 §5. */
  consentVersion: string;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
