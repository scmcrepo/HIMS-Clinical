#!/usr/bin/env python3
"""
Assert that every entity extending AuditableEntity has a table carrying the
columns AuditableEntity maps.

WHY THIS EXISTS

AuditableEntity maps id, tenant_id, branch_id, status, created_by, created_at,
modified_by, modified_at. Hibernate emits all of them in every SELECT and
INSERT for every subclass. A table missing one does not fail at startup — it
fails the first time that repository is touched, which for a compliance table
may be months later, at the exact moment the control matters.

That is how six tables (V179, V209, V210, V213 x3) shipped with no branch_id
and survived four work orders: the parent tables were correct, so the
subsystems looked wired up from the top, and the only symptom was one ERROR
line at boot that nothing was watching.

THE ORACLE IS A REAL DATABASE, NOT THE MIGRATION TEXT

The first version of this script parsed the migration SQL and produced twelve
false positives, because V113-V118 add columns through EXECUTE format() loops
over hardcoded table arrays and no regex reads those honestly. Parsing DDL to
decide whether DDL is correct is circular anyway. Replay the migrations into a
scratch database and ask the database:

    createdb hmscheck
    for f in $(ls backend/src/main/resources/db/migration/V*.sql | sort -V); do
        psql -d hmscheck -v ON_ERROR_STOP=1 -q -f "$f" || break
    done
    python3 .hms-agent/scripts/check_entity_schema.py . "dbname=hmscheck"

Where no driver is available, dump the schema and pass the file instead:

    psql -d hmscheck -t -A -c "SELECT table_name||'|'||string_agg(column_name,',') \
        FROM information_schema.columns WHERE table_schema='public' \
        GROUP BY table_name" > /tmp/schema_cols.txt
    python3 .hms-agent/scripts/check_entity_schema.py . /tmp/schema_cols.txt

This is complementary to `spring.jpa.hibernate.ddl-auto: validate` (see
backend/src/test/resources/application-schemacheck.yml), which catches type and
nullability mismatches this cannot see but needs a compiler and a Spring
context. This one needs neither, so it runs where the build cannot. Run both.

Exit 0 clean, 1 on any mismatch.
"""
import os
import re
import sys

# Columns AuditableEntity maps. If that class gains a @Column, add it here.
REQUIRED = ["tenant_id", "branch_id", "status", "created_by", "created_at",
            "modified_by", "modified_at"]

USAGE = ("usage: check_entity_schema.py <repo-root> "
         "<libpq-dsn | schema-dump-file>")


def load_schema_db(dsn):
    """{table: {column, ...}} for the public schema of a migrated database."""
    try:
        import psycopg2
    except ImportError:
        sys.exit("psycopg2 not installed — either pip install psycopg2-binary "
                 "or pass a schema dump file instead. See the module docstring.")
    conn = psycopg2.connect(dsn)
    cur = conn.cursor()
    cur.execute("SELECT table_name, column_name FROM information_schema.columns "
                "WHERE table_schema = 'public'")
    schema = {}
    for table, column in cur.fetchall():
        schema.setdefault(table, set()).add(column)
    conn.close()
    return schema


def load_schema_file(path):
    """Same shape, from a 'table|col,col,col' dump."""
    schema = {}
    for line in open(path):
        line = line.strip()
        if not line or "|" not in line:
            continue
        table, cols = line.split("|", 1)
        schema[table.strip()] = {c.strip() for c in cols.split(",")}
    return schema


def entities(root):
    """(class, table, path) for every AuditableEntity subclass."""
    found = []
    for dirpath, _, files in os.walk(os.path.join(root, "backend/src/main/java")):
        for f in files:
            if not f.endswith(".java"):
                continue
            path = os.path.join(dirpath, f)
            src = open(path, encoding="utf-8", errors="ignore").read()
            if "@Entity" not in src or "extends AuditableEntity" not in src:
                continue
            t = re.search(r'@Table\(\s*name\s*=\s*"([^"]+)"', src)
            if t:
                found.append((f[:-5], t.group(1).lower(), path))
    return sorted(found)


def main():
    if len(sys.argv) < 3:
        sys.exit(USAGE)

    root, source = sys.argv[1], sys.argv[2]
    schema = (load_schema_file(source) if os.path.exists(source)
              else load_schema_db(source))
    ents = entities(root)

    missing_table, missing_cols = [], []
    for name, table, path in ents:
        if table not in schema:
            missing_table.append((name, table, path))
            continue
        gaps = [c for c in REQUIRED if c not in schema[table]]
        if gaps:
            missing_cols.append((name, table, gaps, path))

    print(f"AuditableEntity subclasses : {len(ents)}")
    print(f"Tables in schema           : {len(schema)}")

    if missing_table:
        print(f"\nFAIL — {len(missing_table)} entity maps a table that does not exist:")
        for name, table, path in missing_table:
            print(f"  {table:34} {name:34} no such relation")
            print(f"      {path}")

    if missing_cols:
        print(f"\nFAIL — {len(missing_cols)} entity/table column mismatch(es):")
        for name, table, gaps, path in missing_cols:
            print(f"  {table:34} {name:34} missing: {', '.join(gaps)}")
            print(f"      {path}")

    if missing_table or missing_cols:
        print("\nEach of these throws at runtime on first repository access. An "
              "entity with a live controller and no table is a 500, not dead code.")
        return 1

    print("\nPASS — every AuditableEntity table carries all mapped columns.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
