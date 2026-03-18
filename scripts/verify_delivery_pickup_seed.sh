#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PROJECT_DIR="$ROOT_DIR/BEGA_PROJECT"
ROOT_ENV_PROD="$ROOT_DIR/../.env.prod"
ROOT_ENV="$ROOT_DIR/../.env"
PROJECT_ENV="$PROJECT_DIR/.env"

read_env_value() {
  local key="$1"
  local file="$2"
  [ -f "$file" ] || return 1
  local line
  line="$(grep -m1 -E "^${key}=" "$file" || true)"
  [ -n "$line" ] || return 1
  printf '%s' "${line#*=}"
}

resolve_setting() {
  local canonical_key="$1"
  local legacy_key="$2"
  local value="${!canonical_key:-}"

  if [ -z "$value" ]; then
    value="${!legacy_key:-}"
  fi
  if [ -z "$value" ]; then
    value="$(read_env_value "$canonical_key" "$ROOT_ENV_PROD" || true)"
  fi
  if [ -z "$value" ]; then
    value="$(read_env_value "$legacy_key" "$ROOT_ENV_PROD" || true)"
  fi
  if [ -z "$value" ]; then
    value="$(read_env_value "$canonical_key" "$ROOT_ENV" || true)"
  fi
  if [ -z "$value" ]; then
    value="$(read_env_value "$legacy_key" "$ROOT_ENV" || true)"
  fi
  if [ -z "$value" ]; then
    value="$(read_env_value "$canonical_key" "$PROJECT_ENV" || true)"
  fi
  if [ -z "$value" ]; then
    value="$(read_env_value "$legacy_key" "$PROJECT_ENV" || true)"
  fi

  printf '%s' "$value"
}

DATABASE_URL="$(resolve_setting BASEBALL_DB_URL DB_URL)"
DATABASE_USERNAME="$(resolve_setting BASEBALL_DB_USERNAME DB_USERNAME)"
DATABASE_PASSWORD="$(resolve_setting BASEBALL_DB_PASSWORD DB_PASSWORD)"

: "${DATABASE_URL:?BASEBALL_DB_URL (or DB_URL fallback) is required (env, .env.prod, or .env file)}"
: "${DATABASE_USERNAME:?BASEBALL_DB_USERNAME (or DB_USERNAME fallback) is required (env, .env.prod, or .env file)}"
: "${DATABASE_PASSWORD:?BASEBALL_DB_PASSWORD (or DB_PASSWORD fallback) is required (env, .env.prod, or .env file)}"

PSQL_URL="${DATABASE_URL#jdbc:}"

echo "[INFO] Flyway status for delivery migrations"
PGPASSWORD="$DATABASE_PASSWORD" psql "$PSQL_URL" -U "$DATABASE_USERNAME" -Atc \
  "select distinct version || '|' || description || '|' || success from public.flyway_schema_history where version in ('102','103','104') order by 1;"

echo "[INFO] Delivery count by stadium"
PGPASSWORD="$DATABASE_PASSWORD" psql "$PSQL_URL" -U "$DATABASE_USERNAME" -Atc \
  "select s.stadium_id || '|' || coalesce(d.cnt,0) from public.stadiums s left join (select stadium_id, count(*) as cnt from public.places where category='delivery' group by stadium_id) d on d.stadium_id=s.stadium_id order by s.stadium_id;"

echo "[INFO] Category totals"
PGPASSWORD="$DATABASE_PASSWORD" psql "$PSQL_URL" -U "$DATABASE_USERNAME" -Atc \
  "select category || '|' || count(*) from public.places group by category order by category;"
