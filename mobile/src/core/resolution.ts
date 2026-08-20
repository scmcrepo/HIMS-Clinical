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

export type ResolutionStep = "hospital" | "branch" | "patient" | "complete";

export interface ResolutionSelection {
  tenantId?: string;
  branchId?: string;
  patientId?: string;
}

export interface ResolvedSession {
  tenantId: string;
  branchId: string;
  patientId: string;
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
 * Returns branches where at least one patient is registered, or all active
 * branches if patients have no branch assigned.
 */
export function branchesForHospital(hospital: HospitalCandidate): BranchSummary[] {
  const active = activeBranches(hospital);
  const registeredBranchIds = new Set(
    hospital.patients.map((p) => p.branchId).filter(Boolean) as string[],
  );
  if (registeredBranchIds.size === 0) {
    return active;
  }
  const matched = active.filter((b) => registeredBranchIds.has(b.branchId));
  return matched.length > 0 ? matched : active;
}

/**
 * With one active registered branch, auto-select it.
 */
export function autoSelectableBranch(
  hospital: HospitalCandidate,
): BranchSummary | null {
  const branches = branchesForHospital(hospital);
  return branches.length === 1 ? (branches[0] as BranchSummary) : null;
}

export function findHospital(
  candidates: HospitalCandidate[],
  tenantId: string | undefined,
): HospitalCandidate | null {
  if (!tenantId) return null;
  return candidates.find((h) => h.tenantId === tenantId) ?? null;
}

/**
 * Returns patients belonging to the chosen hospital and branch.
 */
export function patientsForHospitalAndBranch(
  hospital: HospitalCandidate | null,
  branchId: string | undefined,
): PatientCandidate[] {
  if (!hospital) return [];
  if (!branchId) return hospital.patients;
  const inBranch = hospital.patients.filter((p) => !p.branchId || p.branchId === branchId);
  return inBranch.length > 0 ? inBranch : hospital.patients;
}

export function findPatient(
  hospital: HospitalCandidate | null,
  branchId: string | undefined,
  patientId: string | undefined,
): PatientCandidate | null {
  if (!hospital || !patientId) return null;
  const patients = patientsForHospitalAndBranch(hospital, branchId);
  return patients.find((p) => p.patientId === patientId) ?? null;
}

/**
 * Folds the candidate list and any choices already made into the next state:
 * Step 1: Hospital selection (if > 1 hospital)
 * Step 2: Branch selection (only branches where mobile is registered)
 * Step 3: Patient profile selection (patients registered in that branch)
 * Step 4: Complete session
 */
export function resolve(
  candidates: HospitalCandidate[],
  selection: ResolutionSelection = {},
): ResolutionState {
  const visibleTrail: Exclude<ResolutionStep, "complete">[] = [];
  const chosen: ResolutionSelection = {};

  // --- Step 1: Hospital -----------------------------------------------------
  let hospital: HospitalCandidate | null;
  if (candidates.length === 1) {
    hospital = candidates[0] as HospitalCandidate;
    chosen.tenantId = hospital.tenantId;
  } else if (candidates.length > 1) {
    visibleTrail.push("hospital");
    hospital = findHospital(candidates, selection.tenantId);
    if (!hospital) {
      return { step: "hospital", selection: chosen, visibleTrail, resolved: null };
    }
    chosen.tenantId = hospital.tenantId;
  } else {
    return { step: "hospital", selection: chosen, visibleTrail, resolved: null };
  }

  // --- Step 2: Branch -------------------------------------------------------
  const availableBranches = branchesForHospital(hospital);
  let branch: BranchSummary | null;
  if (availableBranches.length === 1) {
    branch = availableBranches[0] as BranchSummary;
    chosen.branchId = branch.branchId;
  } else if (availableBranches.length > 1) {
    visibleTrail.push("branch");
    branch = availableBranches.find((b) => b.branchId === selection.branchId) ?? null;
    if (!branch) {
      return { step: "branch", selection: chosen, visibleTrail, resolved: null };
    }
    chosen.branchId = branch.branchId;
  } else {
    const all = activeBranches(hospital);
    if (all.length > 0) {
      branch = all[0] as BranchSummary;
      chosen.branchId = branch.branchId;
    } else {
      return { step: "branch", selection: chosen, visibleTrail, resolved: null };
    }
  }

  // --- Step 3: Patient profile ---------------------------------------------
  const availablePatients = patientsForHospitalAndBranch(hospital, chosen.branchId);
  let patient: PatientCandidate | null;
  if (availablePatients.length === 1) {
    patient = availablePatients[0] as PatientCandidate;
    chosen.patientId = patient.patientId;
  } else if (availablePatients.length > 1) {
    visibleTrail.push("patient");
    patient = findPatient(hospital, chosen.branchId, selection.patientId);
    if (!patient) {
      return { step: "patient", selection: chosen, visibleTrail, resolved: null };
    }
    chosen.patientId = patient.patientId;
  } else {
    return { step: "patient", selection: chosen, visibleTrail, resolved: null };
  }

  // --- Step 4: Complete -----------------------------------------------------
  return {
    step: "complete",
    selection: chosen,
    visibleTrail,
    resolved: {
      tenantId: chosen.tenantId as string,
      branchId: chosen.branchId as string,
      patientId: chosen.patientId as string,
    },
  };
}

/**
 * What Back should do from `state`.
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

  if (target === "hospital") {
    delete rewound.tenantId;
    delete rewound.branchId;
    delete rewound.patientId;
  } else if (target === "branch") {
    delete rewound.branchId;
    delete rewound.patientId;
  } else {
    delete rewound.patientId;
  }

  const next = resolve(candidates, rewound);
  return { selection: rewound, step: next.step };
}

/** Zero candidates means the new-patient registration flow. */
export function needsRegistration(candidates: HospitalCandidate[]): boolean {
  return candidates.length === 0;
}
