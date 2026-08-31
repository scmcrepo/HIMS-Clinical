#!/usr/bin/env python3
"""
Regenerate the factual sections of DATA-INVENTORY-ROPA.md from the source.

Run from the repo root. Prints the schema facts the register depends on, so a
reviewer can diff them against the committed document rather than trusting that
someone remembered to update it.

    python3 .hms-agent/scripts/build_inventory.py
"""
import glob
import json
import os
import re
import sys

MIGRATIONS = "backend/src/main/resources/db/migration/*.sql"
JAVA = "backend/src/main/java/**/*.java"

CREATE = re.compile(
    r"CREATE TABLE(?:\s+IF NOT EXISTS)?\s+([a-z_][a-z0-9_]*)\s*\((.*?)\n\);",
    re.S | re.I)
ADD_COLUMN = re.compile(
    r"ALTER TABLE\s+([a-z_][a-z0-9_]*)\s+ADD COLUMN(?:\s+IF NOT EXISTS)?\s+([a-z_][a-z0-9_]*)",
    re.I)
COLUMN = re.compile(
    r"^\s{0,8}([a-z_][a-z0-9_]*)\s+"
    r"(UUID|VARCHAR|TEXT|NUMERIC|INTEGER|BIGINT|SMALLINT|BOOLEAN|TIMESTAMPTZ|DATE|JSONB|CHAR)",
    re.M | re.I)
ENCRYPTED = re.compile(
    r"@Convert\(converter = EncryptedStringConverter\.class\)(?:.|\n)*?"
    r"private\s+\w+(?:<[^>]*>)?\s+(\w+);")
TARGET = re.compile(r'TARGETS\.put\("([a-z_]+)",\s*Strategy\.(\w+)\)')


def schema():
    tables = {}
    for path in sorted(glob.glob(MIGRATIONS)):
        sql = open(path, encoding="utf-8", errors="replace").read()
        for m in CREATE.finditer(sql):
            tables.setdefault(m.group(1).lower(), set()).update(
                c[0].lower() for c in COLUMN.findall(m.group(2)))
        for m in ADD_COLUMN.finditer(sql):
            tables.setdefault(m.group(1).lower(), set()).add(m.group(2).lower())
    return tables


def encrypted_fields():
    out = {}
    for path in glob.glob(JAVA, recursive=True):
        src = open(path, encoding="utf-8", errors="replace").read()
        if "EncryptedStringConverter.class" not in src:
            continue
        fields = ENCRYPTED.findall(src)
        if not fields:
            continue
        table = re.search(r'@Table\(name\s*=\s*"([a-z_]+)"', src)
        out[os.path.basename(path)[:-5]] = {
            "table": table.group(1) if table else None,
            "fields": fields,
        }
    return out


def erasure_strategies():
    path = "backend/src/main/java/com/hms/application/compliance/ErasureService.java"
    if not os.path.exists(path):
        return {}
    return dict(TARGET.findall(open(path, encoding="utf-8").read()))


def main():
    tables = schema()
    patient_linked = sorted(t for t, c in tables.items() if "patient_id" in c)
    strategies = erasure_strategies()
    enc = encrypted_fields()

    # Tables carrying patient_id that are deliberately outside the sweep. Each is
    # a decision; adding to this list without a reason defeats the check.
    excluded = {
        "erasure_requests": "erasing the record of an erasure destroys the evidence it was honoured",
    }
    # Registered under a column other than patient_id.
    other_key = {"patients", "agent_tool_invocations"}

    missing = [t for t in patient_linked
               if t not in strategies and t not in excluded]
    stale = [t for t in strategies
             if t not in patient_linked and t not in other_key]

    print(f"tables                 : {len(tables)}")
    print(f"patient-linked tables  : {len(patient_linked)}")
    print(f"registered for erasure : {len(strategies)}")
    print(f"encrypted entities     : {len(enc)}")
    print(f"encrypted fields       : {sum(len(v['fields']) for v in enc.values())}")

    print("\npatient-linked tables:")
    for t in patient_linked:
        print(f"  {t:34s} {strategies.get(t, '— NOT REGISTERED —')}")

    if missing:
        print("\nMISSING erasure strategy — erasure would silently skip these:")
        for t in missing:
            print(f"  {t}")
    if stale:
        print("\nSTALE registry entries — table not found in schema:")
        for t in stale:
            print(f"  {t}")

    print("\nencrypted fields by entity:")
    for cls, v in sorted(enc.items()):
        if v["fields"]:
            print(f"  {cls:32s} {str(v['table']):28s} {', '.join(v['fields'])}")

    json.dump({"tables": {k: sorted(v) for k, v in tables.items()},
               "patient_linked": patient_linked,
               "erasure_strategies": strategies,
               "encrypted": enc},
              open("/tmp/inventory.json", "w"), indent=2)
    print("\nmachine-readable: /tmp/inventory.json")

    # Non-zero exit so this can gate CI if wanted.
    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main())
