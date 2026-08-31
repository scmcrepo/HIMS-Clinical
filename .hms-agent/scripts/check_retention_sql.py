#!/usr/bin/env python3
"""Execute the statements RetentionService would build for each seeded policy
against the replayed schema, inside rolled-back transactions.

RetentionService validates policies at @PostConstruct against a schema snapshot,
but that path has never run. This checks the same three things the service
depends on, and which a reading of the code cannot settle:

  1. countMatching  — target_store has the date_column AND a tenant_id
  2. apply/DELETE   — the id-subquery form is valid against the real table
  3. apply/ANONYMISE— anonymise_column exists on the target

Policies are read out of V213's seed block rather than retyped, so this stays
honest if the seed changes.

Nothing is written; every statement runs inside BEGIN/ROLLBACK.
"""
import os
import re
import subprocess
import sys

MIGRATION = ("/home/claude/repo/HIMS-Clinical-multi-tenant-data-encrypted/backend/"
             "src/main/resources/db/migration/V213__retention_policy_engine.sql")
DB = "hmsreplay"
TID = "'00000000-0000-0000-0000-000000000002'::uuid"
CUTOFF = "'2020-01-01T00:00:00Z'::timestamptz"

# NEVER_SWEEP from RetentionService — a policy naming one of these is refused
# by the service regardless of what the row says.
NEVER_SWEEP = {
    "clinical_encounters", "visits", "diagnostic_orders", "attachments",
    "patient_pediatric", "patients", "consent_records", "consent_notices",
    "erasure_requests", "erasure_targets", "security_incidents",
    "incident_affected_principals", "grievances", "grievance_events",
    "retention_policies", "retention_runs", "retention_run_items",
    "users", "roles", "features", "role_features", "tenants",
}

SAFE_IDENTIFIER = re.compile(r"^[a-z][a-z0-9_]{0,59}$")


def parse_policies(path):
    """Pull (store, datecol, days, action, anoncol) tuples out of the VALUES block."""
    src = open(path).read()
    start = src.find("CROSS JOIN (VALUES")
    if start < 0:
        sys.exit("Could not find the seed VALUES block in V213")
    block = src[start:]
    pat = re.compile(
        r"\(\s*'([a-z_]+)'\s*,\s*'([a-z_]+)'\s*,\s*(\d+)\s*,\s*'(DELETE|ANONYMISE)'\s*,"
        r"\s*(?:'([a-z_]+)'|NULL)")
    out = []
    for m in pat.finditer(block):
        out.append({
            "store": m.group(1), "datecol": m.group(2), "days": int(m.group(3)),
            "action": m.group(4), "anoncol": m.group(5),
        })
    return out


def run(sql):
    path = "/tmp/retention_stmt.sql"
    with open(path, "w") as fh:
        fh.write("BEGIN;\n" + sql + ";\nROLLBACK;\n")
    os.chmod(path, 0o644)
    p = subprocess.run(
        ["su", "postgres", "-c",
         f"psql -v ON_ERROR_STOP=1 -q -d {DB} -f {path}"],
        capture_output=True, text=True)
    err = (p.stderr or "").strip().splitlines()
    return p.returncode, (err[0] if err else "")


policies = parse_policies(MIGRATION)
print(f"{len(policies)} policies parsed from V213.\n")

problems = []

for p in policies:
    store, datecol, action, anoncol = p["store"], p["datecol"], p["action"], p["anoncol"]
    label = f"{store} [{action}]"

    # Guard checks the service itself applies.
    for ident in filter(None, [store, datecol, anoncol]):
        if not SAFE_IDENTIFIER.match(ident):
            problems.append((label, f"identifier rejected by SAFE_IDENTIFIER: {ident}"))
    if store in NEVER_SWEEP:
        problems.append((label, "policy targets a NEVER_SWEEP store; job would refuse it"))

    cap = 500
    stmts = {
        "countMatching":
            f"SELECT COUNT(*) FROM {store} WHERE {datecol} < {CUTOFF} AND tenant_id = {TID}",
    }
    select_ids = (f"SELECT id FROM {store} WHERE {datecol} < {CUTOFF} "
                  f"AND tenant_id = {TID} LIMIT {cap}")
    if action == "ANONYMISE":
        stmts["apply"] = (f"UPDATE {store} SET {anoncol} = NULL "
                          f"WHERE id IN ({select_ids})")
    else:
        stmts["apply"] = f"DELETE FROM {store} WHERE id IN ({select_ids})"

    for name, sql in stmts.items():
        rc, err = run(sql)
        status = "ok  " if rc == 0 else "FAIL"
        print(f"  {status} {name:14s} {label}")
        if rc != 0:
            problems.append((f"{label} {name}", err))

print(f"\n{len(policies) * 2} statements executed against the replayed schema "
      f"(all rolled back).")
if problems:
    print(f"\n{len(problems)} PROBLEM(S):")
    for where, what in problems:
        print(f"  {where}: {what}")
else:
    print("All seeded policies produce valid, tenant-scoped SQL.")

sys.exit(1 if problems else 0)
