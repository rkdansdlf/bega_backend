import sys
from pathlib import Path
import unittest


SCRIPT_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

import verify_canonical_window_sql as guard  # noqa: E402


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


if __name__ == "__main__":
    unittest.main()
