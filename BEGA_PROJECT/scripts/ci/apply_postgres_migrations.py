#!/usr/bin/env python3
"""Apply the PostgreSQL Flyway migration set to a throwaway database, in Flyway order.

CI uses this to build a fully migrated schema so SchemaValidationContextTest can validate the JPA
entities against *this repository's* migrations instead of an external, possibly stale database.

Connection comes from the standard libpq environment (PGHOST, PGPORT, PGUSER, PGPASSWORD,
PGDATABASE). Each file runs in its own transaction and any error aborts the run.

Versions follow Flyway semantics: ``V103_5__x.sql`` is version 103.5 and sorts between V103 and V104.
Repeatable (R__) and undo (U) migrations are not used in this set and are rejected so they cannot be
silently skipped.
"""
import os
import pathlib
import re
import subprocess
import sys

VERSIONED = re.compile(r"^V(\d+(?:_\d+)*)__.+\.sql$")


def version_key(name: str):
    return tuple(int(part) for part in VERSIONED.match(name).group(1).split("_"))


def ordered_migrations(directory: pathlib.Path):
    names = sorted(p.name for p in directory.iterdir() if p.is_file())
    unsupported = [n for n in names if not VERSIONED.match(n)]
    if unsupported:
        raise SystemExit(f"unsupported migration file name(s): {unsupported}")
    keys = {}
    for name in names:
        keys.setdefault(version_key(name), []).append(name)
    duplicates = {k: v for k, v in keys.items() if len(v) > 1}
    if duplicates:
        raise SystemExit(f"duplicate migration versions: {duplicates}")
    return [directory / name for name in sorted(names, key=version_key)]


def main(argv):
    if len(argv) != 2:
        raise SystemExit("usage: apply_postgres_migrations.py <migration_dir>")
    if not os.environ.get("PGDATABASE"):
        # libpq would silently fall back to a database named after the user and migrate the wrong one.
        raise SystemExit("PGDATABASE must be set so the migrations land in the database that is validated")
    files = ordered_migrations(pathlib.Path(argv[1]))
    for path in files:
        result = subprocess.run(
            ["psql", "-v", "ON_ERROR_STOP=1", "-q", "-1", "-f", str(path)],
            capture_output=True,
            text=True,
        )
        if result.returncode != 0:
            sys.stderr.write(f"FAILED {path.name}\n{result.stderr}\n")
            return 1
    print(f"applied {len(files)} migrations to {os.environ['PGDATABASE']}; last={files[-1].name}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
