import sys
from pathlib import Path
import unittest
from unittest.mock import patch


SCRIPT_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

import verify_canonical_window_sql as guard  # noqa: E402


class FakeCursor:
    def __init__(self, psycopg_stub: "FakePsycopg") -> None:
        self.psycopg_stub = psycopg_stub
        self.result = 0

    def __enter__(self) -> "FakeCursor":
        return self

    def __exit__(self, exc_type, exc, traceback) -> None:
        return None

    def execute(self, query: str, params: tuple[object, ...]) -> None:
        self.psycopg_stub.executions.append((query, params))
        outcome = self.psycopg_stub.outcomes.pop(0)
        if isinstance(outcome, Exception):
            raise outcome
        self.result = outcome

    def fetchone(self) -> tuple[int]:
        return (self.result,)


class FakeConnection:
    def __init__(self, psycopg_stub: "FakePsycopg") -> None:
        self.psycopg_stub = psycopg_stub

    def __enter__(self) -> "FakeConnection":
        return self

    def __exit__(self, exc_type, exc, traceback) -> None:
        return None

    def cursor(self) -> FakeCursor:
        return FakeCursor(self.psycopg_stub)


class FakePsycopg:
    def __init__(self, outcomes: list[int | Exception]) -> None:
        self.outcomes = outcomes
        self.connections: list[tuple[str, bool]] = []
        self.executions: list[tuple[str, tuple[object, ...]]] = []

    def connect(self, db_url: str, autocommit: bool) -> FakeConnection:
        self.connections.append((db_url, autocommit))
        return FakeConnection(self)


class VerifyCanonicalWindowSqlTest(unittest.TestCase):
    def test_evaluate_legacy_residuals_passes_with_zero(self) -> None:
        evaluation = guard.evaluate_legacy_residuals(
            {
                "game": 0,
                "player_season_batting": 0,
                "team_daily_roster": 0,
            },
            strict_legacy_residual=True,
        )
        self.assertTrue(evaluation["passed"])
        self.assertEqual(evaluation["legacy_residual_total"], 0)
        self.assertEqual(evaluation["failed_required_checks"], [])

    def test_evaluate_legacy_residuals_fails_when_residual_exists(self) -> None:
        evaluation = guard.evaluate_legacy_residuals(
            {
                "game": 10,
                "player_season_batting": 0,
            },
            strict_legacy_residual=True,
        )
        self.assertFalse(evaluation["passed"])
        self.assertIn("legacy_residual_total_zero", evaluation["failed_required_checks"])

    def test_evaluate_legacy_residuals_report_only_mode(self) -> None:
        evaluation = guard.evaluate_legacy_residuals(
            {
                "game": 3,
                "player_season_batting": 2,
            },
            strict_legacy_residual=False,
        )
        self.assertTrue(evaluation["passed"])
        self.assertIn("legacy_residual_total_zero", evaluation["failed_optional_checks"])

    def test_evaluate_legacy_residuals_fails_when_fatal_exists(self) -> None:
        evaluation = guard.evaluate_legacy_residuals(
            {"game": 0},
            strict_legacy_residual=True,
            fatal_error="db_connection_failed",
        )
        self.assertFalse(evaluation["passed"])
        self.assertIn("fatal_error_absent", evaluation["failed_required_checks"])

    def test_heavy_stat_tables_use_existence_queries(self) -> None:
        batting_query = guard.QUERY_BY_TABLE["game_batting_stats"].upper()
        pitching_query = guard.QUERY_BY_TABLE["game_pitching_stats"].upper()

        self.assertIn("EXISTS", batting_query)
        self.assertIn("LIMIT 1", batting_query)
        self.assertIn("EXISTS", pitching_query)
        self.assertIn("LIMIT 1", pitching_query)
        self.assertIn("game_batting_stats", guard.EXISTENCE_QUERY_TABLES)
        self.assertIn("game_pitching_stats", guard.EXISTENCE_QUERY_TABLES)

    def test_collect_legacy_residuals_retries_transient_table_failure(self) -> None:
        fake_psycopg = FakePsycopg(
            [
                RuntimeError("server closed the connection unexpectedly"),
                0,
            ]
        )

        with patch.object(guard, "psycopg", fake_psycopg):
            residuals, diagnostics = guard.collect_legacy_residuals(
                "postgresql://example/db",
                window_start=2021,
                window_end=2025,
                max_attempts=2,
                retry_delay_seconds=0,
                query_by_table={"game": "SELECT 1"},
            )

        self.assertEqual(residuals, {"game": 0})
        self.assertEqual(diagnostics["game"]["attempts"], 2)
        self.assertEqual(len(fake_psycopg.connections), 2)
        self.assertTrue(all(autocommit for _, autocommit in fake_psycopg.connections))

    def test_collect_legacy_residuals_does_not_retry_non_transient_failure(self) -> None:
        fake_psycopg = FakePsycopg([RuntimeError("syntax error at or near FROM")])

        with patch.object(guard, "psycopg", fake_psycopg):
            with self.assertRaisesRegex(RuntimeError, "game: syntax error"):
                guard.collect_legacy_residuals(
                    "postgresql://example/db",
                    window_start=2021,
                    window_end=2025,
                    max_attempts=3,
                    retry_delay_seconds=0,
                    query_by_table={"game": "SELECT 1"},
                )

        self.assertEqual(len(fake_psycopg.connections), 1)


if __name__ == "__main__":
    unittest.main()
