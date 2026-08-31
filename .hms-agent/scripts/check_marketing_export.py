#!/usr/bin/env python3
"""Assert that no MARKETING-permissioned endpoint returns patient personal data.

WO-031 removed two endpoints that bulk-decrypted the patient base for a
marketing purpose with no ConsentGate. A deletion only stays deleted if
something notices when it comes back, so this checks the property rather than
the absence of two specific method names: if a future endpoint is permissioned
MARKETING and returns a patient-shaped response, it fails here.

Runs on source, so it works without a compiler. A JUnit equivalent that reflects
over the live controller beans should replace this once the build is green —
this catches the source pattern, not a route registered some other way.
"""
import re
import sys
from pathlib import Path

REPO = Path("/home/claude/repo/HIMS-Clinical-multi-tenant-data-encrypted")
SRC = REPO / "backend/src/main/java"

# Response types that carry decrypted patient fields.
PII_RETURNS = ["PatientResponse", "List<String>"]

MAPPING = re.compile(r"@(Get|Post|Put|Delete|Patch|Request)Mapping")
PREAUTH_MARKETING = re.compile(r"@.*PreAuthorize\(\s*\"hasPermission\(\s*'MARKETING'")
CONSENT_GATE = re.compile(r"consentGate|ConsentGate|requireConsent|hasConsent")

problems = []
checked = 0

for f in sorted(SRC.rglob("*.java")):
    text = f.read_text(encoding="utf-8", errors="replace")
    if "MARKETING" not in text:
        continue
    lines = text.splitlines()
    for i, line in enumerate(lines):
        if not PREAUTH_MARKETING.search(line):
            continue
        # Look at the annotation block and signature that follow.
        window = "\n".join(lines[max(0, i - 3): i + 12])
        if not MAPPING.search(window):
            continue
        checked += 1
        # Strip comments so a REMOVED block explaining the old code is not
        # mistaken for the code itself.
        if line.lstrip().startswith(("*", "//", "/*")):
            continue
        returns_pii = any(t in window for t in PII_RETURNS)
        gated = bool(CONSENT_GATE.search(window))
        if returns_pii and not gated:
            problems.append(
                f"{f.relative_to(REPO)}:{i + 1} — endpoint permissioned MARKETING "
                f"returns patient data with no consent gate")

print(f"Scanned {SRC.name} for MARKETING-permissioned endpoints.")
print(f"Live MARKETING endpoints found: {checked}")

if problems:
    print(f"\n{len(problems)} PROBLEM(S):")
    for p in problems:
        print(f"  {p}")
    print("\nDPDP s. 6 requires consent specific to the purpose. Consent to "
          "treatment is not consent to marketing.")
    sys.exit(1)

print("No MARKETING-permissioned endpoint returns patient personal data.")
sys.exit(0)
