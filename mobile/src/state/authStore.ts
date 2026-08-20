import { create } from "zustand";
import type {
  HospitalCandidate,
  PatientCandidate,
  SessionTokens,
} from "../core/contracts";
import { PortalError } from "../core/errors";
import {
  needsRegistration,
  patientsForHospitalAndBranch,
  resolve,
  stepBack,
  type ResolutionSelection,
  type ResolutionState,
} from "../core/resolution";
import type { AppContainer } from "./container";

/**
 * Login and hospital/profile/branch resolution state.
 *
 * All the branching lives in core/resolution.ts; this store only holds the
 * server responses and the patient's choices, then asks the machine where to
 * go. That split is why the auto-skip rules are covered by 14 Node tests
 * instead of needing a simulator.
 */

export type AuthPhase =
  | "unknown"      // still reading SecureStore on cold start
  | "mobile"       // login screen
  | "otp"          // code entry
  | "resolving"    // hospital / profile / branch screens
  | "registering"  // no records found
  | "ready";       // session established

interface AuthState {
  phase: AuthPhase;
  mobile: string;
  challengeId: string | null;
  otpExpiresInSeconds: number;
  resendAvailableInSeconds: number;
  identityToken: string | null;
  candidates: HospitalCandidate[];
  selection: ResolutionSelection;
  resolution: ResolutionState | null;
  busy: boolean;
  error: PortalError | null;

  bootstrap: (c: AppContainer) => Promise<void>;
  requestOtp: (c: AppContainer, mobile: string) => Promise<void>;
  verifyOtp: (c: AppContainer, code: string) => Promise<void>;
  choose: (c: AppContainer, patch: ResolutionSelection) => Promise<void>;
  goBack: () => "exit" | "stay";
  completeRegistration: (
    c: AppContainer,
    tokens: SessionTokens,
    scope: { patientId: string; tenantId: string; branchId: string },
  ) => Promise<void>;
  reset: () => void;
  clearError: () => void;
}

const initial = {
  phase: "unknown" as AuthPhase,
  mobile: "",
  challengeId: null,
  otpExpiresInSeconds: 0,
  resendAvailableInSeconds: 0,
  identityToken: null,
  candidates: [] as HospitalCandidate[],
  selection: {} as ResolutionSelection,
  resolution: null,
  busy: false,
  error: null,
};

export const useAuthStore = create<AuthState>((set, get) => ({
  ...initial,

  async bootstrap(container) {
    const existing = await container.session.load();
    set({ phase: existing ? "ready" : "mobile" });
  },

  async requestOtp(container, mobile) {
    set({ busy: true, error: null });
    try {
      const result = await container.api.requestOtp(mobile);
      set({
        mobile,
        challengeId: result.challengeId,
        otpExpiresInSeconds: result.expiresInSeconds,
        resendAvailableInSeconds: result.resendAvailableInSeconds,
        phase: "otp",
        busy: false,
      });
    } catch (err) {
      set({ busy: false, error: asPortalError(err) });
    }
  },

  async verifyOtp(container, code) {
    const { challengeId, mobile } = get();
    if (!challengeId) {
      set({ phase: "mobile" });
      return;
    }
    set({ busy: true, error: null });
    try {
      const result = await container.api.verifyOtp({ challengeId, mobile, code });

      // No records anywhere: the identity token is kept, because registration
      // needs proof the patient holds this number before it will create a
      // patient row against it.
      if (needsRegistration(result.candidates)) {
        set({
          identityToken: result.identityToken,
          candidates: [],
          phase: "registering",
          busy: false,
        });
        return;
      }

      const resolution = resolve(result.candidates, {});
      set({
        identityToken: result.identityToken,
        candidates: result.candidates,
        resolution,
        selection: resolution.selection,
        phase: "resolving",
        busy: false,
      });

      if (resolution.step === "complete") {
        await get().choose(container, {});
      }
    } catch (err) {
      set({ busy: false, error: asPortalError(err) });
    }
  },

  /**
   * Records a choice and re-runs the machine. When the machine reports
   * `complete`, the identity token is exchanged for the patient-scoped session.
   */
  async choose(container, patch) {
    const { candidates, selection, identityToken } = get();
    const next = { ...selection, ...patch };
    const resolution = resolve(candidates, next);
    set({ selection: resolution.selection, resolution });

    if (resolution.step !== "complete" || !resolution.resolved) return;
    if (!identityToken) {
      set({ phase: "mobile" });
      return;
    }

    set({ busy: true, error: null });
    try {
      const tokens = await container.api.exchangeSession(
        identityToken,
        resolution.resolved,
      );
      await container.session.establish(tokens, resolution.resolved);
      set({ ...initial, phase: "ready", busy: false });
    } catch (err) {
      const error = asPortalError(err);
      // A rejected exchange means the candidate set no longer backs this
      // choice — send the patient back to the number, not to a retry loop.
      if (error.requiresReauthentication) {
        set({ ...initial, phase: "mobile", error });
        return;
      }
      set({ busy: false, error });
    }
  },

  /**
   * Returns "exit" when Back should leave the authenticated flow entirely —
   * which is the correct behaviour for a patient whose hospital, profile and
   * branch were all auto-selected and who therefore never saw a picker.
   */
  goBack() {
    const { candidates, resolution } = get();
    if (!resolution) return "exit";
    const back = stepBack(candidates, resolution);
    if (!back) return "exit";
    set({
      selection: back.selection,
      resolution: resolve(candidates, back.selection),
    });
    return "stay";
  },

  async completeRegistration(container, tokens, scope) {
    await container.session.establish(tokens, scope);
    set({ ...initial, phase: "ready" });
  },

  reset() {
    set({ ...initial, phase: "mobile" });
  },

  clearError() {
    set({ error: null });
  },
}));

function asPortalError(err: unknown): PortalError {
  if (err instanceof PortalError) return err;
  return new PortalError({ code: "UNKNOWN", message: "error.UNKNOWN" });
}

/** Convenience selector for the profile picker screen. */
export function patientsForSelectedHospital(
  candidates: HospitalCandidate[],
  tenantId: string | undefined,
  branchId?: string | undefined,
): PatientCandidate[] {
  if (!tenantId) return [];
  const hospital = candidates.find((h) => h.tenantId === tenantId);
  return patientsForHospitalAndBranch(hospital ?? null, branchId);
}
