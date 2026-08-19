import type {
  Appointment,
  AttachmentMeta,
  BookAppointmentBody,
  CaseSheetSection,
  Consultant,
  DiagnosticOrderGroup,
  HospitalCandidate,
  OtpRequestBody,
  OtpRequestResult,
  OtpVerifyBody,
  OtpVerifyResult,
  Page,
  PatientProfile,
  RegisterPatientBody,
  SessionExchangeBody,
  SessionTokens,
  SignedDownload,
  SlotAvailability,
  VisitDetail,
  VisitSummary,
} from "./contracts";
import type { HttpClient } from "./http";
import { normaliseMobile } from "./validation";

/**
 * One function per portal endpoint (WO-017 §4.3, WO-018 §4.2).
 *
 * Note what is absent: nothing here takes a `patientId` for a read. WO-018 §4.1
 * puts the patient id in the token precisely so that it cannot be a parameter,
 * and keeping the client signatures parameterless makes accidentally
 * reintroducing `?patientId=` a type error rather than a code review question.
 */
export class PortalApi {
  private readonly http: HttpClient;

  constructor(http: HttpClient) {
    this.http = http;
  }

  // --- auth ---------------------------------------------------------------

  requestOtp(mobile: string): Promise<OtpRequestResult> {
    const body: OtpRequestBody = { mobile: normaliseMobile(mobile) };
    return this.http.request<OtpRequestResult>({
      method: "POST",
      path: "/portal/auth/otp/request",
      body,
      anonymous: true,
    });
  }

  verifyOtp(input: {
    challengeId: string;
    mobile: string;
    code: string;
  }): Promise<OtpVerifyResult> {
    const body: OtpVerifyBody = {
      challengeId: input.challengeId,
      mobile: normaliseMobile(input.mobile),
      code: input.code.trim(),
    };
    return this.http.request<OtpVerifyResult>({
      method: "POST",
      path: "/portal/auth/otp/verify",
      body,
      anonymous: true,
    });
  }

  /** Exchanges the identity token for patient-scoped tokens. */
  exchangeSession(
    identityToken: string,
    selection: SessionExchangeBody,
  ): Promise<SessionTokens> {
    return this.http.request<SessionTokens>({
      method: "POST",
      path: "/portal/auth/session",
      body: selection,
      anonymous: true,
      headers: { Authorization: `Bearer ${identityToken}` },
    });
  }

  /** `noRetryOnUnauthorized` so a failed refresh cannot recurse into itself. */
  refresh(refreshToken: string): Promise<SessionTokens> {
    return this.http.request<SessionTokens>({
      method: "POST",
      path: "/portal/auth/refresh",
      body: { refreshToken },
      anonymous: true,
      noRetryOnUnauthorized: true,
    });
  }

  logout(): Promise<void> {
    return this.http.request<void>({
      method: "POST",
      path: "/portal/auth/logout",
      noRetryOnUnauthorized: true,
    });
  }

  // --- registration -------------------------------------------------------

  listHospitals(identityToken: string): Promise<HospitalCandidate[]> {
    return this.http.request<HospitalCandidate[]>({
      method: "GET",
      path: "/portal/hospitals",
      anonymous: true,
      headers: { Authorization: `Bearer ${identityToken}` },
    });
  }

  register(
    identityToken: string,
    body: RegisterPatientBody,
  ): Promise<SessionTokens> {
    return this.http.request<SessionTokens>({
      method: "POST",
      path: "/portal/patients/register",
      body: { ...body, mobile: normaliseMobile(body.mobile) },
      anonymous: true,
      headers: { Authorization: `Bearer ${identityToken}` },
    });
  }

  // --- profile and booking -------------------------------------------------

  getProfile(): Promise<PatientProfile> {
    return this.http.request<PatientProfile>({
      method: "GET",
      path: "/portal/me",
    });
  }

  listConsultants(filter?: {
    q?: string;
    departmentId?: string;
  }): Promise<Consultant[]> {
    const params = new URLSearchParams();
    if (filter?.q) params.set("q", filter.q);
    if (filter?.departmentId) params.set("departmentId", filter.departmentId);
    const qs = params.toString();
    return this.http.request<Consultant[]>({
      method: "GET",
      path: `/portal/consultants${qs ? `?${qs}` : ""}`,
    });
  }

  getAvailability(
    consultantId: string,
    isoDate: string,
  ): Promise<SlotAvailability[]> {
    return this.http.request<SlotAvailability[]>({
      method: "GET",
      path: `/portal/consultants/${consultantId}/availability?date=${isoDate}`,
    });
  }

  /**
   * `idempotencyKey` is required, not optional.
   *
   * WO-018 R3: a patient on 3G taps Confirm, the response is lost, they tap
   * again, and without this they now hold two appointments and the slot has lost
   * capacity for someone else. Making it a mandatory parameter means a caller
   * cannot forget it.
   */
  bookAppointment(
    body: BookAppointmentBody,
    idempotencyKey: string,
  ): Promise<Appointment> {
    return this.http.request<Appointment>({
      method: "POST",
      path: "/portal/appointments",
      body,
      headers: { "Idempotency-Key": idempotencyKey },
    });
  }

  listAppointments(
    scope: "upcoming" | "past",
    page = 0,
    size = 20,
  ): Promise<Page<Appointment>> {
    return this.http.request<Page<Appointment>>({
      method: "GET",
      path: `/portal/appointments?scope=${scope}&page=${page}&size=${size}`,
    });
  }

  rescheduleAppointment(
    appointmentId: string,
    body: { slotId: string; appointmentDate: string },
    idempotencyKey: string,
  ): Promise<Appointment> {
    return this.http.request<Appointment>({
      method: "PUT",
      path: `/portal/appointments/${appointmentId}/reschedule`,
      body,
      headers: { "Idempotency-Key": idempotencyKey },
    });
  }

  cancelAppointment(appointmentId: string): Promise<Appointment> {
    return this.http.request<Appointment>({
      method: "POST",
      path: `/portal/appointments/${appointmentId}/cancel`,
    });
  }

  // --- records -------------------------------------------------------------

  listVisits(page = 0, size = 10): Promise<Page<VisitSummary>> {
    return this.http.request<Page<VisitSummary>>({
      method: "GET",
      path: `/portal/visits?page=${page}&size=${size}`,
    });
  }

  getVisit(encounterId: string): Promise<VisitDetail> {
    return this.http.request<VisitDetail>({
      method: "GET",
      path: `/portal/visits/${encounterId}`,
    });
  }

  getCasesheet(encounterId: string): Promise<CaseSheetSection[]> {
    return this.http.request<CaseSheetSection[]>({
      method: "GET",
      path: `/portal/visits/${encounterId}/casesheet`,
    });
  }

  getLabReports(encounterId: string): Promise<DiagnosticOrderGroup[]> {
    return this.http.request<DiagnosticOrderGroup[]>({
      method: "GET",
      path: `/portal/visits/${encounterId}/lab-reports`,
    });
  }

  getDiagnosticReports(encounterId: string): Promise<DiagnosticOrderGroup[]> {
    return this.http.request<DiagnosticOrderGroup[]>({
      method: "GET",
      path: `/portal/visits/${encounterId}/diagnostic-reports`,
    });
  }

  listAttachments(encounterId: string): Promise<AttachmentMeta[]> {
    return this.http.request<AttachmentMeta[]>({
      method: "GET",
      path: `/portal/visits/${encounterId}/attachments`,
    });
  }

  /** Returns a 5-minute signed URL; the download itself is audited server-side. */
  getAttachmentDownload(attachmentId: string): Promise<SignedDownload> {
    return this.http.request<SignedDownload>({
      method: "GET",
      path: `/portal/attachments/${attachmentId}/download`,
    });
  }
}
