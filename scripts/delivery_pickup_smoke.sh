#!/usr/bin/env bash
set -euo pipefail

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  cat <<'EOF'
Delivery pickup zone smoke script.

Required:
  BACKEND_BASE_URL   Backend base URL (default: http://localhost:8080)

Optional DB verification:
  BASEBALL_DB_URL      Preferred jdbc:postgresql://... format
  BASEBALL_DB_USERNAME Preferred DB username
  BASEBALL_DB_PASSWORD Preferred DB password
  DB_URL/DB_USERNAME/DB_PASSWORD remain supported as fallback

Examples:
  BACKEND_BASE_URL=https://api.example.com \
  ./scripts/delivery_pickup_smoke.sh

  BACKEND_BASE_URL=https://api.example.com \
  BASEBALL_DB_URL=jdbc:postgresql://db-host:5432/bega_backend \
  BASEBALL_DB_USERNAME=user BASEBALL_DB_PASSWORD=pass \
  ./scripts/delivery_pickup_smoke.sh
EOF
  exit 0
fi

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "[ERROR] Required command not found: $1" >&2
    exit 1
  }
}

require_cmd curl
require_cmd jq

BACKEND_BASE_URL="${BACKEND_BASE_URL:-http://localhost:8080}"

echo "[1/4] Health check: ${BACKEND_BASE_URL}/actuator/health"
curl -fsS "${BACKEND_BASE_URL}/actuator/health" | jq '.status'

echo "[2/4] Stadium list and delivery coverage"
stadiums_json="$(curl -fsS "${BACKEND_BASE_URL}/api/stadiums")"
stadium_count="$(printf '%s' "$stadiums_json" | jq 'length')"
if [[ "$stadium_count" -lt 9 ]]; then
  echo "[ERROR] Expected at least 9 stadiums, got ${stadium_count}" >&2
  exit 1
fi

echo "stadium_count=${stadium_count}"
missing=0
while IFS= read -r stadium_id; do
  delivery_count="$(curl -fsS "${BACKEND_BASE_URL}/api/stadiums/${stadium_id}/places?category=delivery" | jq 'length')"
  echo "delivery|${stadium_id}|${delivery_count}"
  if [[ "$delivery_count" -lt 1 ]]; then
    missing=1
  fi
done < <(printf '%s' "$stadiums_json" | jq -r '.[].stadiumId')

if [[ "$missing" -ne 0 ]]; then
  echo "[ERROR] At least one stadium has zero delivery pickup zones." >&2
  exit 1
fi

echo "[3/4] Sample payload checks"
curl -fsS "${BACKEND_BASE_URL}/api/stadiums/JAMSIL/places?category=delivery" | jq '.[0] | {name, lat, lng, address}'
curl -fsS "${BACKEND_BASE_URL}/api/stadiums/GOCHEOK/places?category=delivery" | jq '.[0] | {name, lat, lng, address}'

echo "[4/4] Optional DB checks"
db_url="${BASEBALL_DB_URL:-${DB_URL:-}}"
db_username="${BASEBALL_DB_USERNAME:-${DB_USERNAME:-}}"
db_password="${BASEBALL_DB_PASSWORD:-${DB_PASSWORD:-}}"

if [[ -n "${db_url}" && -n "${db_username}" && -n "${db_password}" ]]; then
  require_cmd psql
  psql_url="${db_url#jdbc:}"
  PGPASSWORD="${db_password}" psql "${psql_url}" -U "${db_username}" -Atc \
    "select distinct version || '|' || success from public.flyway_schema_history where version in ('102','103','104') order by 1;"
  PGPASSWORD="${db_password}" psql "${psql_url}" -U "${db_username}" -Atc \
    "select category || '|' || count(*) from public.places group by category order by category;"
else
  echo "BASEBALL_DB_* or DB_* credentials not provided; skipping DB checks."
fi

echo "[DONE] Delivery pickup smoke passed."
