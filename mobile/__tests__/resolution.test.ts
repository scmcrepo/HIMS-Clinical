import { describe, expect, it } from "vitest";
import type { HospitalCandidate } from "../src/core/resolution";
import {
  autoSelectableBranch,
  needsRegistration,
  resolve,
  stepBack,
} from "../src/core/resolution";
import type {
  BranchSummary,
  HospitalCandidate as Candidate,
  PatientCandidate,
} from "../src/core/contracts";

function patient(id: string, branchId?: string): PatientCandidate {
  return {
    patientId: id,
    fullName: `Patient ${id}`,
    age: 32,
    gender: "MALE",
    numberSequenceSuffix: `P-${id}`,
    photoUrl: null,
    branchId: branchId ?? null,
  };
}

function branch(
  id: string,
  opts: { active?: boolean; isDefault?: boolean } = {},
): BranchSummary {
  return {
    branchId: id,
    name: `Branch ${id}`,
    code: id.toUpperCase(),
    address: null,
    contactNumber: null,
    isDefault: opts.isDefault ?? false,
    isActive: opts.active ?? true,
  };
}

function hospital(
  id: string,
  patients: PatientCandidate[],
  branches: BranchSummary[],
): Candidate {
  return {
    tenantId: id,
    tenantName: `Hospital ${id}`,
    address: null,
    contactNumber: null,
    logoUrl: null,
    patients,
    branches,
  };
}

describe("auto-skip (PRD §3.2 steps 2-4)", () => {
  it("skips all three screens when there is exactly one of everything", () => {
    const candidates = [hospital("t1", [patient("p1", "b1")], [branch("b1")])];
    const state = resolve(candidates);

    expect(state.step).toBe("complete");
    expect(state.visibleTrail).toEqual([]);
    expect(state.resolved).toEqual({
      tenantId: "t1",
      patientId: "p1",
      branchId: "b1",
    });
  });

  it("shows all three screens in order when each has multiple options", () => {
    const candidates = [
      hospital(
        "t1",
        [patient("p1", "b1"), patient("p2", "b2"), patient("p3", "b2")],
        [branch("b1"), branch("b2")],
      ),
      hospital("t2", [patient("p4", "b3")], [branch("b3")]),
    ];

    const atHospital = resolve(candidates);
    expect(atHospital.step).toBe("hospital");
    expect(atHospital.resolved).toBeNull();

    const atBranch = resolve(candidates, { tenantId: "t1" });
    expect(atBranch.step).toBe("branch");
    expect(atBranch.visibleTrail).toEqual(["hospital", "branch"]);

    const atPatient = resolve(candidates, { tenantId: "t1", branchId: "b2" });
    expect(atPatient.step).toBe("patient");

    const done = resolve(candidates, {
      tenantId: "t1",
      branchId: "b2",
      patientId: "p2",
    });
    expect(done.step).toBe("complete");
    expect(done.visibleTrail).toEqual(["hospital", "branch", "patient"]);
    expect(done.resolved?.patientId).toBe("p2");
    expect(done.resolved?.branchId).toBe("b2");
  });

  it("skips only the steps that are unambiguous", () => {
    // One hospital, one branch, three family members in that branch.
    const candidates = [
      hospital(
        "t1",
        [patient("p1", "b1"), patient("p2", "b1"), patient("p3", "b1")],
        [branch("b1")],
      ),
    ];
    const state = resolve(candidates);
    expect(state.step).toBe("patient");
    expect(state.visibleTrail).toEqual(["patient"]);
  });

  it("counts only active branches when deciding whether to skip", () => {
    const candidates = [
      hospital(
        "t1",
        [patient("p1", "b1")],
        [branch("b1"), branch("b2", { active: false })],
      ),
    ];
    const state = resolve(candidates);
    expect(state.step).toBe("complete");
    expect(state.resolved?.branchId).toBe("b1");
  });

  it("refuses to auto-select when several registered branches are active", () => {
    const h = hospital(
      "t1",
      [patient("p1", "b1"), patient("p2", "b2")],
      [branch("b1", { isDefault: true }), branch("b2")],
    );
    expect(autoSelectableBranch(h)).toBeNull();
    expect(resolve([h]).step).toBe("branch");
  });

  it("filters branches to only branches where patients are registered", () => {
    // Hospital has b1, b2, b3, but patient is only registered in b1
    const h = hospital(
      "t1",
      [patient("p1", "b1")],
      [branch("b1"), branch("b2"), branch("b3")],
    );
    // Should auto-select b1 and complete!
    const state = resolve([h]);
    expect(state.step).toBe("complete");
    expect(state.resolved?.branchId).toBe("b1");
  });

  it("ignores a selection pointing at a hospital that is not a candidate", () => {
    const candidates = [
      hospital("t1", [patient("p1", "b1")], [branch("b1")]),
      hospital("t2", [patient("p2", "b2")], [branch("b2")]),
    ];
    const state = resolve(candidates, { tenantId: "t-not-mine" });
    expect(state.step).toBe("hospital");
    expect(state.selection.tenantId).toBeUndefined();
  });
});

describe("back navigation", () => {
  it("returns null from a fully auto-skipped session, so Back means log out", () => {
    const candidates = [hospital("t1", [patient("p1", "b1")], [branch("b1")])];
    const state = resolve(candidates);
    expect(stepBack(candidates, state)).toBeNull();
  });

  it("re-enters at the last shown step, not the last evaluated one", () => {
    // Two hospitals, but chosen one has single branch and single patient
    const candidates = [
      hospital("t1", [patient("p1", "b1")], [branch("b1")]),
      hospital("t2", [patient("p2", "b2")], [branch("b2")]),
    ];
    const done = resolve(candidates, { tenantId: "t1" });
    expect(done.step).toBe("complete");
    expect(done.visibleTrail).toEqual(["hospital"]);

    const back = stepBack(candidates, done);
    expect(back?.step).toBe("hospital");
    expect(back?.selection.tenantId).toBeUndefined();
  });

  it("clears downstream choices when rewinding", () => {
    const candidates = [
      hospital(
        "t1",
        [patient("p1", "b1"), patient("p2", "b2"), patient("p3", "b2")],
        [branch("b1"), branch("b2")],
      ),
      hospital("t2", [patient("p4", "b3")], [branch("b3")]),
    ];
    const done = resolve(candidates, {
      tenantId: "t1",
      branchId: "b2",
      patientId: "p2",
    });
    const back = stepBack(candidates, done);
    expect(back?.step).toBe("patient");
    expect(back?.selection.patientId).toBeUndefined();
    expect(back?.selection.branchId).toBe("b2");

    const twice = stepBack(candidates, resolve(candidates, back!.selection));
    expect(twice?.step).toBe("branch");
    expect(twice?.selection.branchId).toBeUndefined();
    expect(twice?.selection.tenantId).toBe("t1");
  });

  it("walks the whole trail back to the first shown screen and then stops", () => {
    const candidates = [
      hospital(
        "t1",
        [patient("p1", "b1"), patient("p2", "b2"), patient("p3", "b2")],
        [branch("b1"), branch("b2")],
      ),
      hospital("t2", [patient("p4", "b3")], [branch("b3")]),
    ];
    let selection = { tenantId: "t1", branchId: "b2", patientId: "p2" };
    let state = resolve(candidates, selection);

    const seen: string[] = [];
    for (let i = 0; i < 5; i += 1) {
      const back = stepBack(candidates, state);
      if (!back) break;
      seen.push(back.step);
      selection = back.selection as typeof selection;
      state = resolve(candidates, selection);
    }
    expect(seen).toEqual(["patient", "branch", "hospital"]);
  });
});

describe("registration handoff", () => {
  it("routes to registration when the number matched nothing", () => {
    expect(needsRegistration([])).toBe(true);
    const state = resolve([]);
    // With no candidates there is no hospital to auto-select, so the machine
    // parks on 'hospital' and the caller checks needsRegistration first.
    expect(state.resolved).toBeNull();
  });

  it("does not route to registration when a candidate exists", () => {
    expect(needsRegistration([hospital("t1", [patient("p1")], [branch("b1")])])).toBe(
      false,
    );
  });
});

// Type-level guard: the re-export used by screens must stay assignable.
const _typecheck: HospitalCandidate = hospital("t", [], []);
void _typecheck;
