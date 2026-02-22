#!/usr/bin/env python3
"""
Backend deploy guard for canonical window data integrity.

Checks 2021~2025 legacy-team-code residuals in core baseball tables and exits
non-zero when strict mode is enabled and residual rows are found.
"""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
from typing import Dict
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit

try:
    import psycopg
except ModuleNotFoundError:  # pragma: no cover - handled by runtime checks
    psycopg = None  # type: ignore[assignment]


LEGACY_CODES = ["SK", "OB", "HT", "WO", "DO", "KI", "KW", "NX", "SL", "BE", "MBC", "LOT"]

QUERY_BY_TABLE = {
    "game": """
        SELECT COUNT(*)::bigint
        FROM game g
        JOIN kbo_seasons ks ON g.season_id = ks.season_id
        WHERE ks.season_year BETWEEN %s AND %s
          AND (
            g.home_team = ANY(%s)
            OR g.away_team = ANY(%s)
            OR g.winning_team = ANY(%s)
          )
    """,
    "player_season_batting": """
        SELECT COUNT(*)::bigint
        FROM player_season_batting
        WHERE season BETWEEN %s AND %s
          AND team_code = ANY(%s)
    """,
    "player_season_pitching": """
        SELECT COUNT(*)::bigint
        FROM player_season_pitching
        WHERE season BETWEEN %s AND %s
          AND team_code = ANY(%s)
    """,
    "game_lineups": """
        SELECT COUNT(*)::bigint
        FROM game_lineups gl
        JOIN game g ON gl.game_id = g.game_id
        JOIN kbo_seasons ks ON g.season_id = ks.season_id
        WHERE ks.season_year BETWEEN %s AND %s
          AND gl.team_code = ANY(%s)
    """,
    "game_batting_stats": """
        SELECT COUNT(*)::bigint
        FROM game_batting_stats gs
        JOIN game g ON gs.game_id = g.game_id
        JOIN kbo_seasons ks ON g.season_id = ks.season_id
        WHERE ks.season_year BETWEEN %s AND %s
          AND gs.team_code = ANY(%s)
    """,
    "game_pitching_stats": """
        SELECT COUNT(*)::bigint
        FROM game_pitching_stats gs
        JOIN game g ON gs.game_id = g.game_id
        JOIN kbo_seasons ks ON g.season_id = ks.season_id
        WHERE ks.season_year BETWEEN %s AND %s
          AND gs.team_code = ANY(%s)
    """,
    "team_daily_roster": """
        SELECT COUNT(*)::bigint
        FROM team_daily_roster
        WHERE EXTRACT(YEAR FROM roster_date) BETWEEN %s AND %s
          AND team_code = ANY(%s)
    """,
}


def _safe_int(value: object, default: int = 0) -> int:
    try:
        if value is None:
            return default
        return int(value)
    except (TypeError, ValueError):
        return default


def _dsn_host(dsn: str) -> str | None:
    try:
        parsed = urlsplit(dsn)
    except ValueError:
        return None
    if not parsed.hostname:
        return None
    return parsed.hostname if parsed.port is None else f"{parsed.hostname}:{parsed.port}"


def _normalize_db_url(raw: str) -> str:
    candidate = raw.strip()
    if candidate.startswith("jdbc:"):
        candidate = candidate[len("jdbc:") :]
    if candidate.startswith("postgres://"):
        candidate = "postgresql://" + candidate[len("postgres://") :]
    return candidate


def _append_missing_credentials(dsn: str) -> str:
    try:
        parsed = urlsplit(dsn)
    except ValueError:
        return dsn

    if not parsed.hostname:
        return dsn

    auth_password = os.getenv("CANONICAL_GUARD_DB_PASSWORD") or os.getenv("SPRING_DATASOURCE_PASSWORD") or os.getenv("DB_PASSWORD")
    auth_username = os.getenv("CANONICAL_GUARD_DB_USERNAME") or os.getenv("SPRING_DATASOURCE_USERNAME") or os.getenv("DB_USERNAME")

    query = dict(parse_qsl(parsed.query, keep_blank_values=True))
    has_password_in_dsn = bool(parsed.password or query.get("password"))
    if has_password_in_dsn:
        return dsn

    if parsed.username:
        if not auth_password:
            return dsn
        username = parsed.username
        port = f":{parsed.port}" if parsed.port else ""
        netloc = f"{username}:{auth_password}@{parsed.hostname}{port}"
        return urlunsplit(
            (
                parsed.scheme,
                netloc,
                parsed.path,
                urlencode(query, doseq=True),
                parsed.fragment,
            )
        )

    if query.get("user") and auth_password:
        query["password"] = auth_password
        return urlunsplit(
            (
                parsed.scheme,
                parsed.netloc,
                parsed.path,
                urlencode(query, doseq=True),
                parsed.fragment,
            )
        )

    if auth_username and auth_password:
        port = f":{parsed.port}" if parsed.port else ""
        netloc = f"{auth_username}:{auth_password}@{parsed.hostname}{port}"
        return urlunsplit(
            (
                parsed.scheme,
                netloc,
                parsed.path,
                urlencode(query, doseq=True),
                parsed.fragment,
            )
        )

    return dsn


def evaluate_legacy_residuals(
    legacy_residuals: Dict[str, object],
    *,
    strict_legacy_residual: bool,
    fatal_error: str | None = None,
) -> Dict[str, object]:
    residual_total = sum(_safe_int(v) for v in legacy_residuals.values())
    checks = [
        {
            "name": "legacy_residual_total_zero",
            "passed": residual_total == 0,
            "enforced": strict_legacy_residual,
            "details": f"legacy_residual_total={residual_total}",
        }
    ]
    if fatal_error:
        checks.append(
            {
                "name": "fatal_error_absent",
                "passed": False,
                "enforced": True,
                "details": fatal_error,
            }
        )
    failed_required = [c["name"] for c in checks if c["enforced"] and not c["passed"]]
    failed_optional = [c["name"] for c in checks if (not c["enforced"]) and (not c["passed"])]
    return {
        "passed": len(failed_required) == 0,
        "strict_legacy_residual": strict_legacy_residual,
        "legacy_residual_total": residual_total,
        "failed_required_checks": failed_required,
        "failed_optional_checks": failed_optional,
        "checks": checks,
    }


def _append_github_step_summary(lines: list[str]) -> None:
    summary_path = os.getenv("GITHUB_STEP_SUMMARY")
    if not summary_path:
        return
    path = Path(summary_path)
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as handle:
        handle.write("\n".join(lines))
        handle.write("\n")


def _build_step_summary(output: Dict[str, object]) -> list[str]:
    evaluation = output.get("evaluation")
    if not isinstance(evaluation, dict):
        evaluation = {}

    lines = [
        "## Backend Canonical SQL Guard",
        "",
        "| key | value |",
        "|---|---:|",
        f"| legacy_residual_total | {evaluation.get('legacy_residual_total', 0)} |",
        f"| strict_legacy_residual | {evaluation.get('strict_legacy_residual')} |",
        f"| runtime_seconds | {output.get('runtime_seconds')} |",
        f"| passed | {evaluation.get('passed')} |",
        "",
    ]

    failed_required = evaluation.get("failed_required_checks") or []
    if failed_required:
        lines.append(f"- required_failures: {', '.join(map(str, failed_required))}")
    if output.get("fatal_error"):
        lines.append(f"- fatal_error: {output['fatal_error']}")
    if failed_required or output.get("fatal_error"):
        lines.append("")
    return lines


def _resolve_db_url(env_name: str) -> str:
    for candidate_env in (env_name, "POSTGRES_DB_URL", "DB_URL"):
        value = os.getenv(candidate_env)
        if not value:
            continue
        normalized = _normalize_db_url(value)
        if not normalized or normalized.startswith("***"):
            continue
        resolved = _append_missing_credentials(normalized)
        if resolved and not resolved.startswith("***"):
            return resolved
    raise RuntimeError(f"{env_name} is required for canonical SQL guard")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Verify legacy team-code residuals for canonical window (backend guard)."
    )
    parser.add_argument(
        "--window-start",
        type=int,
        default=2021,
        help="Canonical window start year.",
    )
    parser.add_argument(
        "--window-end",
        type=int,
        default=2025,
        help="Canonical window end year.",
    )
    parser.add_argument(
        "--db-url-env",
        default="CANONICAL_GUARD_DB_URL_RO",
        help="Environment variable name containing the DB URL.",
    )
    parser.add_argument(
        "--strict-legacy-residual",
        action=argparse.BooleanOptionalAction,
        default=True,
        help="Fail when legacy residual rows are greater than 0.",
    )
    parser.add_argument(
        "--output",
        default="",
        help="Optional output JSON path.",
    )
    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()

    output: Dict[str, object] = {
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "input": {
            "window_start": args.window_start,
            "window_end": args.window_end,
            "db_url_env": args.db_url_env,
            "strict_legacy_residual": args.strict_legacy_residual,
        },
        "legacy_residuals": {},
        "runtime_seconds": None,
    }

    started = datetime.now(timezone.utc)
    exit_code = 0

    try:
        if psycopg is None:
            raise RuntimeError("psycopg is not installed")
        db_url = _resolve_db_url(args.db_url_env)
        output["db_host"] = _dsn_host(db_url)
        with psycopg.connect(db_url, autocommit=True) as conn:
            with conn.cursor() as cur:
                for table, query in QUERY_BY_TABLE.items():
                    if table == "game":
                        cur.execute(
                            query,
                            (
                                args.window_start,
                                args.window_end,
                                LEGACY_CODES,
                                LEGACY_CODES,
                                LEGACY_CODES,
                            ),
                        )
                    else:
                        cur.execute(query, (args.window_start, args.window_end, LEGACY_CODES))
                    output["legacy_residuals"][table] = _safe_int(cur.fetchone()[0])
    except Exception as exc:
        output["fatal_error"] = str(exc)
        exit_code = 1

    elapsed = (datetime.now(timezone.utc) - started).total_seconds()
    output["runtime_seconds"] = round(elapsed, 2)

    evaluation = evaluate_legacy_residuals(
        output.get("legacy_residuals", {}),
        strict_legacy_residual=args.strict_legacy_residual,
        fatal_error=output.get("fatal_error"),
    )
    output["evaluation"] = evaluation
    if not evaluation["passed"]:
        exit_code = 1

    rendered = json.dumps(output, ensure_ascii=False, indent=2)
    print(rendered)

    if args.output:
        output_path = Path(args.output).expanduser().resolve()
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(rendered + "\n", encoding="utf-8")
        print(f"Wrote report: {output_path}")

    _append_github_step_summary(_build_step_summary(output))
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
