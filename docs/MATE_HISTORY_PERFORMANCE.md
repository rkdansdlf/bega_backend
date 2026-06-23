# Mate History Performance Verification

This runbook verifies that `GET /api/parties/my/history` uses the mate history indexes and that latency is observable after release.

## Indexes

Expected migrations:

- Oracle: `V148__add_mate_history_indexes.sql`
- PostgreSQL: `V153__add_mate_history_indexes.sql`

Expected indexes:

- `idx_parties_host_created` on `parties(hostid, createdat DESC, id DESC)`
- `idx_party_app_applicant_party` on `party_applications(applicantid, is_approved, partyid)`

PostgreSQL index check:

```sql
SELECT indexname, indexdef
FROM pg_indexes
WHERE schemaname = current_schema()
  AND tablename IN ('parties', 'party_applications')
  AND indexname IN ('idx_parties_host_created', 'idx_party_app_applicant_party')
ORDER BY indexname;
```

Oracle index check:

```sql
SELECT index_name, table_name
FROM user_indexes
WHERE index_name IN ('IDX_PARTIES_HOST_CREATED', 'IDX_PARTY_APP_APPLICANT_PARTY')
ORDER BY index_name;

SELECT index_name, column_name, column_position, descend
FROM user_ind_columns
WHERE index_name IN ('IDX_PARTIES_HOST_CREATED', 'IDX_PARTY_APP_APPLICANT_PARTY')
ORDER BY index_name, column_position;
```

## Execution Plan

Use a real `:user_id` with enough hosted and approved-participant history rows to make the optimizer choose a meaningful plan. Do not run production `ANALYZE`-style checks without explicit approval.

PostgreSQL staging:

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.*
FROM parties p
WHERE (
    p.hostid = :user_id
    OR EXISTS (
        SELECT 1
        FROM party_applications pa
        WHERE pa.partyid = p.id
          AND pa.applicantid = :user_id
          AND pa.is_approved = true
    )
)
ORDER BY p.createdat DESC, p.id DESC
LIMIT 20 OFFSET 0;
```

Oracle production-safe plan:

```sql
EXPLAIN PLAN FOR
SELECT p.*
FROM parties p
WHERE (
    p.hostid = :user_id
    OR EXISTS (
        SELECT 1
        FROM party_applications pa
        WHERE pa.partyid = p.id
          AND pa.applicantid = :user_id
          AND pa.is_approved = 1
    )
)
ORDER BY p.createdat DESC, p.id DESC
FETCH FIRST 20 ROWS ONLY;

SELECT *
FROM TABLE(DBMS_XPLAN.DISPLAY);
```

Pass criteria:

- Hosted path uses `idx_parties_host_created` or an equivalent selective access path.
- Approved-participant path uses `idx_party_app_applicant_party` or an equivalent selective access path.
- No broad `parties` full table scan appears for normal user history lookups.
- If the `OR` predicate causes a broad `parties` scan, split a follow-up task to rewrite the history lookup as hosted and approved-participant queries combined with `UNION` semantics.

## Automated DB Plan Report

After the staging observability smoke passes, capture reusable DB evidence with:

```bash
python3 scripts/mate_history_db_plan_report.py --dry-run --db postgresql --user-id 1
```

PostgreSQL staging:

```bash
PG_URL='postgresql://user:***@host:5432/dbname' \
python3 scripts/mate_history_db_plan_report.py \
  --db postgresql \
  --user-id <history_user_id> \
  --groups all,completed,ongoing \
  --page 0 \
  --size 20 \
  --confirm-staging
```

Oracle staging:

```bash
ORACLE_WALLET_DIR=/path/to/wallet \
ORACLE_SERVICE_NAME=<staging_alias> \
ORACLE_USER=ADMIN \
ORACLE_PASSWORD='***' \
python3 scripts/mate_history_db_plan_report.py \
  --db oracle \
  --user-id <history_user_id> \
  --groups all,completed,ongoing \
  --page 0 \
  --size 20 \
  --confirm-staging
```

The script writes:

- `reports/mate-history-db-plan/latest.md`
- `reports/mate-history-db-plan/latest.json`
- run-specific raw plan files under `reports/mate-history-db-plan/<timestamp>/`

The generated report redacts the raw `user_id` and never writes DB passwords, tokens, or row payloads. It captures both content and count query plans for each group, matching the Spring `Page` behavior.

Verdict rules:

- `PASS`: both expected indexes exist, both are observed in captured plans, and no broad `parties` full scan is detected.
- `WARN`: no broad `parties` full scan is detected, but at least one expected index is not directly observed and needs manual review.
- `FAIL`: an expected index is missing or a broad `parties` full scan is detected.

If the verdict is `FAIL`, do not rewrite the query as part of verification. Create a separate follow-up to split the hosted and approved-participant branches into `UNION`-style queries, then rerun this report after the rewrite.

## Metrics

The API records `mate_history_request_duration_seconds` through Micrometer.

Tags:

- `group`: `all`, `completed`, `ongoing`, `invalid`, `unknown`
- `result`: `success`, `failure`

Local/authorized actuator checks:

```bash
curl -fsS "http://localhost:8080/actuator/metrics/mate_history_request_duration_seconds" | jq '{name, measurements, availableTags}'
curl -fsS "http://localhost:8080/actuator/prometheus" | grep 'mate_history_request_duration_seconds'
```

Prometheus examples:

```promql
sum(rate(mate_history_request_duration_seconds_count[5m])) by (group, result)
histogram_quantile(0.95, sum(rate(mate_history_request_duration_seconds_bucket[5m])) by (le, group))
```

Dashboard and alert checks:

```bash
curl -fsS "http://localhost:9090/api/v1/rules" \
  | jq '.data.groups[] | select(.name=="mate-history-alerts") | .rules[] | .name'
```

Expected alert names:

- `MateHistoryP95LatencyHigh`
- `MateHistoryFailureSpike`

Grafana should load the provisioned `KBO Mate / History` dashboard from:

- `monitoring/grafana/dashboards/mate-history.json`

The dashboard should show group-level p95 latency, request rate, failure count, success/failure ratio, and request volume panels. The latency warning threshold is p95 greater than `1.5s` over a `10m` window for `5m`.

Static asset validation:

```bash
bash scripts/check_monitoring_assets.sh
```

The `Monitoring Assets` GitHub Actions workflow runs the same validation when `monitoring/**`, `scripts/check_monitoring_assets.sh`, this document, or the workflow file changes. If it fails, check the workflow log for the invalid dashboard JSON, duplicate dashboard uid, Prometheus rule error, or Prometheus config error.

Staging observability smoke:

```bash
API_BASE_URL=https://api-staging.example.com \
PROMETHEUS_URL=https://prometheus-staging.example.com \
TEST_EMAIL=operator-smoke@example.com \
TEST_PASSWORD='***' \
bash scripts/mate_history_observability_smoke.sh
```

The smoke writes reusable artifacts to `reports/mate-history-observability-smoke/latest.md` and `reports/mate-history-observability-smoke/latest.json`. It validates authenticated history API responses, the actuator metric, Prometheus rule loading, and Prometheus count/p95 query evaluation. It does not capture DB execution plans; run the automated DB plan report above after the smoke passes.

Verification sequence:

1. Call `/api/parties/my/history?group=all&page=0&size=20` with an authenticated user.
2. Confirm the actuator metric count increases with `group="all"` and `result="success"`.
3. Call an invalid group in a non-production environment and confirm `group="invalid"` and `result="failure"`.
4. Confirm Prometheus loaded the `mate-history-alerts` rule group.
5. Confirm the Grafana `KBO Mate / History` dashboard panels load without query errors.
6. Run `scripts/mate_history_db_plan_report.py` against staging and attach `latest.md`/`latest.json` to the deployment ticket.
