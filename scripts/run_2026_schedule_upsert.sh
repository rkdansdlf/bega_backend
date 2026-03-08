#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_FILE="${SCRIPT_DIR}/upsert_2026_opening_week_schedule.sql"

if [[ -z "${BASEBALL_DB_URL:-}" ]]; then
  echo "BASEBALL_DB_URL is required." >&2
  exit 1
fi

if [[ ! -f "${SQL_FILE}" ]]; then
  echo "SQL file not found: ${SQL_FILE}" >&2
  exit 1
fi

PSQL_URL="${BASEBALL_DB_URL}"
if [[ "${PSQL_URL}" == jdbc:postgresql://* ]]; then
  PSQL_URL="${PSQL_URL#jdbc:}"
fi

if [[ -n "${BASEBALL_DB_USERNAME:-}" ]]; then
  export PGUSER="${BASEBALL_DB_USERNAME}"
elif [[ -n "${DB_USERNAME:-}" ]]; then
  export PGUSER="${DB_USERNAME}"
fi

if [[ -n "${BASEBALL_DB_PASSWORD:-}" ]]; then
  export PGPASSWORD="${BASEBALL_DB_PASSWORD}"
elif [[ -n "${DB_PASSWORD:-}" ]]; then
  export PGPASSWORD="${DB_PASSWORD}"
fi

echo "Running schedule upsert against BASEBALL_DB_URL..."
psql "${PSQL_URL}" -v ON_ERROR_STOP=1 -f "${SQL_FILE}"
echo "Schedule upsert completed."
