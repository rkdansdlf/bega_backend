#!/usr/bin/env bash
set -euo pipefail

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  cat <<'EOF'
Mate payout dark launch smoke script.

Required env (one of):
  ADMIN_BEARER_TOKEN       Internal ADMIN|SUPER_ADMIN bearer token
  ADMIN_EMAIL + ADMIN_PASSWORD

Optional env:
  BACKEND_BASE_URL         Default: http://localhost:8080
  PAYOUT_PROVIDER          Default: TOSS
  ADMIN_EMAIL              Used when ADMIN_BEARER_TOKEN is not provided
  ADMIN_PASSWORD           Used when ADMIN_BEARER_TOKEN is not provided
  SELLER_USER_ID           When set with PROVIDER_SELLER_ID, upsert seller mapping first
  PROVIDER_SELLER_ID       Provider seller id for mapping registration
  SELLER_KYC_STATUS        Default: APPROVED
  SELLER_METADATA_JSON     Default: {}
  MATE_TEST_PAYMENT_ID     If set, request manual payout for this payment transaction id
  EXPECTED_PAYOUT_STATUS   Default: SKIPPED (dark launch: enabled=false)
  EXPECTED_FAILURE_CODE    Default: PAYMENT_PAYOUT_DISABLED (dark launch: enabled=false)

Examples:
  ADMIN_BEARER_TOKEN=... \
  BACKEND_BASE_URL=https://api-stage.example.com \
  SELLER_USER_ID=101 PROVIDER_SELLER_ID=toss_seller_101 \
  MATE_TEST_PAYMENT_ID=9001 \
  ./scripts/mate_payout_dark_launch_smoke.sh

  ADMIN_EMAIL=admin@example.com ADMIN_PASSWORD=****** \
  BACKEND_BASE_URL=https://api-stage.example.com \
  MATE_TEST_PAYMENT_ID=9001 \
  ./scripts/mate_payout_dark_launch_smoke.sh
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
ADMIN_BEARER_TOKEN="${ADMIN_BEARER_TOKEN:-}"
ADMIN_EMAIL="${ADMIN_EMAIL:-}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-}"
PAYOUT_PROVIDER="${PAYOUT_PROVIDER:-TOSS}"
SELLER_USER_ID="${SELLER_USER_ID:-}"
PROVIDER_SELLER_ID="${PROVIDER_SELLER_ID:-}"
SELLER_KYC_STATUS="${SELLER_KYC_STATUS:-APPROVED}"
SELLER_METADATA_JSON="${SELLER_METADATA_JSON:-{}}"
MATE_TEST_PAYMENT_ID="${MATE_TEST_PAYMENT_ID:-}"
EXPECTED_PAYOUT_STATUS="${EXPECTED_PAYOUT_STATUS:-SKIPPED}"
EXPECTED_FAILURE_CODE="${EXPECTED_FAILURE_CODE:-PAYMENT_PAYOUT_DISABLED}"

resolve_admin_token() {
  if [[ -n "$ADMIN_BEARER_TOKEN" ]]; then
    printf '%s' "$ADMIN_BEARER_TOKEN"
    return
  fi

  if [[ -z "$ADMIN_EMAIL" || -z "$ADMIN_PASSWORD" ]]; then
    echo "[ERROR] ADMIN_BEARER_TOKEN or (ADMIN_EMAIL + ADMIN_PASSWORD) is required." >&2
    exit 1
  fi

  cookie_jar="$(mktemp)"
  login_response="$(mktemp)"
  login_payload="$(jq -n \
    --arg email "$ADMIN_EMAIL" \
    --arg password "$ADMIN_PASSWORD" \
    '{email: $email, password: $password}')"

  login_status="$(curl -sS -o "$login_response" -c "$cookie_jar" -w "%{http_code}" \
    -X POST "${BACKEND_BASE_URL}/api/auth/login" \
    -H "$JSON_HEADER" \
    -d "$login_payload")"

  if [[ "$login_status" != "200" ]]; then
    echo "[ERROR] Admin login failed. status=${login_status}" >&2
    cat "$login_response" >&2
    rm -f "$cookie_jar" "$login_response"
    exit 1
  fi

  login_token="$(awk '$0 !~ /^#/ && $6 == "Authorization" {print $7}' "$cookie_jar" | tail -n1)"
  rm -f "$cookie_jar" "$login_response"

  if [[ -z "$login_token" ]]; then
    echo "[ERROR] Login succeeded but Authorization cookie token was not found." >&2
    exit 1
  fi

  printf '%s' "$login_token"
}

JSON_HEADER="Content-Type: application/json"
RESOLVED_ADMIN_TOKEN="$(resolve_admin_token)"
AUTH_HEADER="Authorization: Bearer ${RESOLVED_ADMIN_TOKEN}"

echo "[1/4] Actuator health check"
curl -fsS "${BACKEND_BASE_URL}/actuator/health" | jq '.'

echo "[2/4] Mate payment metrics check"
curl -fsS "${BACKEND_BASE_URL}/actuator/metrics/mate_settlement_payout_total" | jq '{name, measurements, availableTags}'
curl -fsS "${BACKEND_BASE_URL}/actuator/metrics/mate_refund_total" | jq '{name, measurements, availableTags}'
curl -fsS "${BACKEND_BASE_URL}/actuator/metrics/mate_payment_compensation_total" | jq '{name, measurements, availableTags}'

if [[ -n "$SELLER_USER_ID" && -n "$PROVIDER_SELLER_ID" ]]; then
  echo "[3/4] Upsert seller payout profile (provider=${PAYOUT_PROVIDER})"
  upsert_payload="$(jq -n \
    --argjson userId "$SELLER_USER_ID" \
    --arg provider "$PAYOUT_PROVIDER" \
    --arg providerSellerId "$PROVIDER_SELLER_ID" \
    --arg kycStatus "$SELLER_KYC_STATUS" \
    --arg metadataJson "$SELLER_METADATA_JSON" \
    '{userId: $userId, provider: $provider, providerSellerId: $providerSellerId, kycStatus: $kycStatus, metadataJson: $metadataJson}')"

  curl -fsS -X POST "${BACKEND_BASE_URL}/api/internal/payout/sellers" \
    -H "$AUTH_HEADER" \
    -H "$JSON_HEADER" \
    -d "$upsert_payload" | jq '.'

  curl -fsS "${BACKEND_BASE_URL}/api/internal/payout/sellers/${SELLER_USER_ID}?provider=${PAYOUT_PROVIDER}" \
    -H "$AUTH_HEADER" | jq '.'
else
  echo "[3/4] Skip seller profile upsert (SELLER_USER_ID or PROVIDER_SELLER_ID missing)"
fi

if [[ -n "$MATE_TEST_PAYMENT_ID" ]]; then
  echo "[4/4] Request manual payout for paymentId=${MATE_TEST_PAYMENT_ID}"
  response_file="$(mktemp)"
  http_code="$(curl -sS -o "$response_file" -w "%{http_code}" \
    -X POST "${BACKEND_BASE_URL}/api/internal/settlements/${MATE_TEST_PAYMENT_ID}/payout" \
    -H "$AUTH_HEADER" \
    -H "$JSON_HEADER")"

  if [[ "$http_code" != "200" ]]; then
    echo "[ERROR] Manual payout request failed. status=${http_code}" >&2
    cat "$response_file" >&2
    rm -f "$response_file"
    exit 1
  fi

  cat "$response_file" | jq '.'

  actual_status="$(jq -r '.data.status // empty' "$response_file")"
  actual_failure_code="$(jq -r '.data.failureCode // empty' "$response_file")"

  if [[ -n "$EXPECTED_PAYOUT_STATUS" && "$actual_status" != "$EXPECTED_PAYOUT_STATUS" ]]; then
    echo "[ERROR] Unexpected payout status. expected=${EXPECTED_PAYOUT_STATUS}, actual=${actual_status}" >&2
    rm -f "$response_file"
    exit 1
  fi

  if [[ -n "$EXPECTED_FAILURE_CODE" && "$actual_failure_code" != "$EXPECTED_FAILURE_CODE" ]]; then
    echo "[ERROR] Unexpected failureCode. expected=${EXPECTED_FAILURE_CODE}, actual=${actual_failure_code}" >&2
    rm -f "$response_file"
    exit 1
  fi

  rm -f "$response_file"
else
  echo "[4/4] Skip manual payout request (MATE_TEST_PAYMENT_ID missing)"
fi

echo "[DONE] Mate payout dark launch smoke completed."
