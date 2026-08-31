#!/usr/bin/env python3
"""Execute every statement ErasureService would issue against the replayed
schema, inside a transaction that is rolled back. Postgres validates table
names, column names and types for us; nothing is written.

This is the check that would have caught the original hitl_escalations bug:
not 'does it parse' but 'does it reference columns that exist, and is it
scoped by BOTH tenant and patient'.
"""
import re
import subprocess
import sys

PID = "'00000000-0000-0000-0000-000000000001'::uuid"
TID = "'00000000-0000-0000-0000-000000000002'::uuid"

DELETE = [
    "agent_idempotency_keys", "portal_sessions", "discovered_policies",
    "patient_policy_coverages", "abha_linkages", "external_health_records",
    "abdm_consent_artifacts", "abdm_consent_requests",
]
ANONYMISE_SIMPLE = ["insurances", "bills", "payments", "pharmacy_sales", "sales_returns"]
RETAIN = [
    "clinical_encounters", "diagnostic_orders", "visits", "attachments",
    "patient_pediatric", "grievances", "incident_affected_principals",
    "consent_records",
]

stmts = []
for t in DELETE:
    stmts.append((t, "DELETE",
                  f"DELETE FROM {t} WHERE patient_id = {PID} AND tenant_id = {TID}"))

stmts.append(("hitl_escalations", "ANONYMISE",
    "UPDATE hitl_escalations SET transcript = NULL, operator_reply = NULL, "
    f"detail = NULL, patient_id = NULL WHERE tenant_id = {TID} AND patient_id = {PID}"))
stmts.append(("agent_tool_invocations", "ANONYMISE",
    "UPDATE agent_tool_invocations SET target_entity_id = NULL "
    f"WHERE tenant_id = {TID} AND target_entity_id = {PID}"))
stmts.append(("nhcx_transactions", "ANONYMISE",
    "UPDATE nhcx_transactions SET patient_id = NULL, response_payload = NULL "
    f"WHERE tenant_id = {TID} AND patient_id = {PID}"))
for t in ANONYMISE_SIMPLE:
    stmts.append((t, "ANONYMISE",
        f"UPDATE {t} SET patient_id = NULL WHERE tenant_id = {TID} AND patient_id = {PID}"))
stmts.append(("appointments", "ANONYMISE",
    "UPDATE appointments SET patient_id = NULL, notes = NULL "
    f"WHERE tenant_id = {TID} AND patient_id = {PID}"))
stmts.append(("patients", "ANONYMISE",
    "UPDATE patients SET first_name = NULL, last_name = NULL, "
    "contact_number = NULL, contact_number_token = NULL, email = NULL, "
    "address = NULL, blood_group = NULL, date_of_birth = NULL, "
    "pediatric_data = NULL, template_data = NULL, status = 0 "
    f"WHERE tenant_id = {TID} AND id = {PID}"))

for t in RETAIN:
    if t == "patient_pediatric":
        # No tenant_id column (V010). Scoped through patients.
        stmts.append((t, "RETAIN",
            "SELECT COUNT(*) FROM patient_pediatric pp "
            "JOIN patients p ON p.id = pp.patient_id "
            f"WHERE pp.patient_id = {PID} AND p.tenant_id = {TID}"))
    else:
        stmts.append((t, "RETAIN",
            f"SELECT COUNT(*) FROM {t} WHERE patient_id = {PID} AND tenant_id = {TID}"))

# legacy unattributable sweep
stmts.append(("hitl_escalations", "LEGACY",
    "UPDATE hitl_escalations SET transcript = NULL, operator_reply = NULL, detail = NULL "
    f"WHERE tenant_id = {TID} AND patient_id IS NULL AND transcript IS NOT NULL"))
stmts.append(("agent_idempotency_keys", "LEGACY",
    f"DELETE FROM agent_idempotency_keys WHERE tenant_id = {TID} AND patient_id IS NULL"))


import os

def run(sql):
    body = "BEGIN;\n" + sql + ";\nROLLBACK;\n"
    path = "/tmp/erasure_stmt.sql"
    with open(path, "w") as fh:
        fh.write(body)
    os.chmod(path, 0o644)
    p = subprocess.run(
        ["su", "postgres", "-c",
         f"psql -v ON_ERROR_STOP=1 -q -d hmsreplay -f {path}"],
        capture_output=True, text=True)
    return p.returncode, (p.stderr or "").strip()


fails, unscoped = [], []
for table, strategy, sql in stmts:
    rc, err = run(sql)
    status = "ok" if rc == 0 else "FAIL"
    if rc != 0:
        fails.append((table, strategy, err.splitlines()[0] if err else "?"))
    # scoping audit: every statement must name tenant_id
    if "tenant_id" not in sql:
        unscoped.append((table, strategy))
    print(f"  {status:4s} {strategy:10s} {table}")

print(f"\n{len(stmts)} statements executed against the replayed schema "
      f"(all rolled back).")
if fails:
    print(f"\n{len(fails)} FAILED — these reference columns that do not exist:")
    for t, s, e in fails:
        print(f"  {t} [{s}]: {e}")
else:
    print("All statements valid against the real schema.")

if unscoped:
    print("\nNOT TENANT-SCOPED:")
    for t, s in unscoped:
        print(f"  {t} [{s}]")
else:
    print("Every statement is tenant-scoped.")

sys.exit(1 if fails else 0)
