import type {
  BranchSummary,
  HospitalCandidate,
  PatientCandidate,
} from "./contracts";

// Re-exported so a screen importing the machine does not also have to reach into
// contracts.ts for the shapes the machine hands it.
export type { BranchSummary, HospitalCandidate, PatientCandidate };

/**
 * The hospital -> patient -> branch resolution machine (PRD §3.2 steps 2-4).
 *
 * The requirement is three consecutive "skip this screen if there is exactly one
 * option" rules. Written as three components with `useEffect` redirects, that
 * produces a well-known class of bug: the screen flashes before redirecting, and
 * pressing Back lands the patient on a screen that immediately forwards them
 * again, so Back appears broken. Modelling it as a fold over the candidate set
 * makes both the forward path and the back stack derivable instead of emergent.
 *
 * Nothing here imports React or React Native — this file is unit-tested under
 * plain Node, which is the point.
 */

export type ResolutionStep = "hospital" | "patient" | "branch" | "complete";

export interface ResolutionSelection {
  tenantId?: string;
  patientId?: string;
  branchId?: string;
}

export interface ResolvedSession {
  tenantId: string;
  patientId: string;
  branchId: string;
}

export interface ResolutionState {
  /** Where the UI should be right now. */
  step: ResolutionStep;
  selection: ResolutionSelection;
  /**
   * Steps the patient was actually shown, oldest first. Back navigation walks
   * this, not the full step list, so an auto-skipped screen is never revisited.
   */
  visibleTrail: Exclude<ResolutionStep, "complete">[];
  /** Populated only when step === "complete". */
  resolved: ResolvedSession | null;
}

export function activeBranches(hospital: HospitalCandidate): BranchSummary[] {
  return hospital.branches.filter((b) => b.isActive);
}

/**
 * PRD §3.2 step 4: with one active branch, auto-select it. When several are
 * active but exactly one is flagged default, that default is *not* auto-selected
 * — the patient may be visiting the other one, and silently choosing for them
 * sends their appointment to the wrong address. The default only decides
 * ordering and pre-highlighting in the picker.
 */
export function autoSelectableBranch(
  hospital: HospitalCandidate,
): BranchSummary | null {
  const active = activeBranches(hospital);
  return active.length === 1 ? (active[0] as BranchSummary) : null;
}

export function findHospital(
  candidates: HospitalCandidate[],
  tenantId: string | undefined,
): HospitalCandidate | null {
  if (!tenantId) return null;
  return candidates.find((h) => h.tenantId === tenantId) ?? null;
}

export function findPatient(
  hospital: HospitalCandidate | null,
  patientId: string | undefined,
): PatientCandidate | null {
  if (!hospital || !patientId) return null;
  return hospital.patients.find((p) => p.patientId === patientId) ?? null;
}

/**
 * Folds the candidate list and any choices already made into the next state.
 *
 * Pure and total: given the same candidates and selection it always returns the
 * same state, so the screen can be re-derived after a process death without
 * replaying navigation history.
 */
export function resolve(
  candidates: HospitalCandidate[],
  selection: ResolutionSelection = {},
): ResolutionState {
  const visibleTrail: Exclude<ResolutionStep, "complete">[] = [];
  const chosen: ResolutionSelection = {};

  // --- Step 2: hospital -----------------------------------------------------
  let hospital: HospitalCandidate | null;
  if (candidates.length === 1) {
    hospital = candidates[0] as HospitalCandidate;
    chosen.tenantId = hospital.tenantId;
  } else {
    visibleTrail.push("hospital");
    hospital = findHospital(candidates, selection.tenantId);
    if (!hospital) {
      return { step: "hospital", selection: chosen, visibleTrail, resolved: null };
    }
    chosen.tenantId = hospital.tenantId;
  }

  // --- Step 3: patient profile ---------------------------------------------
  let patient: PatientCandidate | null;
  if (hospital.patients.length === 1) {
    patient = hospital.patients[0] as PatientCandidate;
    chosen.patientId = patient.patientId;
  } else {
    visibleTrail.push("patient");
    patient = findPatient(hospital, selection.patientId);
    if (!patient) {
      return { step: "patient", selection: chosen, visibleTrail, resolved: null };
    }
    chosen.patientId = patient.patientId;
  }

  // --- Step 4: branch -------------------------------------------------------
  const auto = autoSelectableBranch(hospital);
  let branch: BranchSummary | null;
  if (auto) {
    branch = auto;
    chosen.branchId = branch.branchId;
  } else {
    visibleTrail.push("branch");
    branch =
      activeBranches(hospital).find((b) => b.branchId === selection.branchId) ??
      null;
    if (!branch) {
      return { step: "branch", selection: chosen, visibleTrail, resolved: null };
    }
    chosen.branchId = branch.branchId;
  }

  return {
    step: "complete",
    selection: chosen,
    visibleTrail,
    resolved: {
      tenantId: chosen.tenantId as string,
      patientId: chosen.patientId as string,
      branchId: chosen.branchId as string,
    },
  };
}

/**
 * What Back should do from `state`.
 *
 * Returns the selection to rewind to, or null when the patient is at the first
 * screen they were actually shown — at which point Back means "log out", not
 * "go to an empty hospital list". A single-hospital, single-patient,
 * single-branch patient has an empty trail, so Back from their dashboard exits
 * the authenticated flow rather than dropping them on three skipped screens.
 */
export function stepBack(
  candidates: HospitalCandidate[],
  state: ResolutionState,
): { selection: ResolutionSelection; step: ResolutionStep } | null {
  const trail = state.visibleTrail;
  if (trail.length === 0) return null;

  const currentIndex =
    state.step === "complete" ? trail.length : trail.indexOf(state.step);
  const targetIndex = currentIndex - 1;
  if (targetIndex < 0) return null;

  const target = trail[targetIndex] as Exclude<ResolutionStep, "complete">;
  const rewound: ResolutionSelection = { ...state.selection };
  // Clear the target's own choice and everything downstream of it, so re-entering
  // a screen shows it unanswered rather than pre-filled with a stale pick.
  if (target === "hospital") {
    delete rewound.tenantId;
    delete rewound.patientId;
    delete rewound.branchId;
  } else if (target === "patient") {
    delete rewound.patientId;
    delete rewound.branchId;
  } else {
    delete rewound.branchId;
  }

  const next = resolve(candidates, rewound);
  return { selection: rewound, step: next.step };
}

/** PRD §3.1: zero candidates means the new-patient registration flow. */
export function needsRegistration(candidates: HospitalCandidate[]): boolean {
  return candidates.length === 0;
}
