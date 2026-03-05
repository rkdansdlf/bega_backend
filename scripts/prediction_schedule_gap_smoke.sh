#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
DATES=("2026-03-22" "2026-03-23" "2026-03-28")

fail() {
  echo "[FAIL] $1" >&2
  exit 1
}

pass() {
  echo "[PASS] $1"
}

count_json_array() {
  python3 -c 'import json,sys; data=json.load(sys.stdin); print(len(data) if isinstance(data,list) else -1)'
}

count_range_content() {
  python3 -c 'import json,sys; data=json.load(sys.stdin); print(len(data.get("content", [])) if isinstance(data,dict) else -1)'
}

check_schedule_date() {
  local date="$1"
  local response
  response="$(curl -sS "${BASE_URL}/api/kbo/schedule?date=${date}")" || fail "schedule request failed (${date})"
  local count
  count="$(printf '%s' "${response}" | count_json_array)" || fail "schedule response parse failed (${date})"
  [[ "${count}" -gt 0 ]] || fail "schedule empty (${date})"
  pass "schedule non-empty (${date}) count=${count}"
}

for date in "${DATES[@]}"; do
  check_schedule_date "${date}"
done

range_response="$(curl -sS "${BASE_URL}/api/matches/range?startDate=2026-03-22&endDate=2026-03-31&withMeta=true")" \
  || fail "matches/range request failed"
range_count="$(printf '%s' "${range_response}" | count_range_content)" \
  || fail "matches/range parse failed"
[[ "${range_count}" -gt 0 ]] || fail "matches/range content empty"
pass "matches/range content non-empty count=${range_count}"

bounds_response="$(curl -sS "${BASE_URL}/api/matches/bounds")" || fail "matches/bounds request failed"
python3 - "$bounds_response" <<'PY'
import json
import sys

payload = json.loads(sys.argv[1])
if not payload.get("hasData"):
    print("[FAIL] matches/bounds hasData=false", file=sys.stderr)
    sys.exit(1)

latest = payload.get("latestGameDate")
if latest is None or str(latest) < "2026-03-22":
    print(f"[FAIL] matches/bounds latestGameDate invalid: {latest}", file=sys.stderr)
    sys.exit(1)

print(f"[PASS] matches/bounds latestGameDate={latest}")
PY

pass "prediction schedule gap smoke completed"
