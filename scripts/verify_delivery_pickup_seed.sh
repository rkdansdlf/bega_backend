#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PROJECT_DIR="$ROOT_DIR/BEGA_PROJECT"

read_env_value() {
  local key="$1"
  local file="$2"
  [ -f "$file" ] || return 1
  local line
  line="$(grep -m1 -E "^${key}=" "$file" || true)"
  [ -n "$line" ] || return 1
  printf '%s' "${line#*=}"
}

DB_URL="${DB_URL:-}"
DB_USERNAME="${DB_USERNAME:-}"
DB_PASSWORD="${DB_PASSWORD:-}"

if [ -z "$DB_URL" ]; then
  DB_URL="$(read_env_value DB_URL "$ROOT_DIR/../.env" || true)"
fi
if [ -z "$DB_URL" ]; then
  DB_URL="$(read_env_value DB_URL "$PROJECT_DIR/.env" || true)"
fi

if [ -z "$DB_USERNAME" ]; then
  DB_USERNAME="$(read_env_value DB_USERNAME "$ROOT_DIR/../.env" || true)"
fi
if [ -z "$DB_USERNAME" ]; then
  DB_USERNAME="$(read_env_value DB_USERNAME "$PROJECT_DIR/.env" || true)"
fi

if [ -z "$DB_PASSWORD" ]; then
  DB_PASSWORD="$(read_env_value DB_PASSWORD "$ROOT_DIR/../.env" || true)"
fi
if [ -z "$DB_PASSWORD" ]; then
  DB_PASSWORD="$(read_env_value DB_PASSWORD "$PROJECT_DIR/.env" || true)"
fi

: "${DB_URL:?DB_URL is required (env or .env file)}"
: "${DB_USERNAME:?DB_USERNAME is required (env or .env file)}"
: "${DB_PASSWORD:?DB_PASSWORD is required (env or .env file)}"

PSQL_URL="${DB_URL#jdbc:}"

echo "[INFO] Flyway status for delivery migrations"
PGPASSWORD="$DB_PASSWORD" psql "$PSQL_URL" -U "$DB_USERNAME" -Atc \
  "select version || '|' || description || '|' || success from public.flyway_schema_history where version in ('102','103','104') order by version;"

echo "[INFO] Delivery count by stadium"
PGPASSWORD="$DB_PASSWORD" psql "$PSQL_URL" -U "$DB_USERNAME" -Atc \
  "select s.stadium_id || '|' || coalesce(d.cnt,0) from public.stadiums s left join (select stadium_id, count(*) as cnt from public.places where category='delivery' group by stadium_id) d on d.stadium_id=s.stadium_id order by s.stadium_id;"

echo "[INFO] Category totals"
PGPASSWORD="$DB_PASSWORD" psql "$PSQL_URL" -U "$DB_USERNAME" -Atc \
  "select category || '|' || count(*) from public.places group by category order by category;"
