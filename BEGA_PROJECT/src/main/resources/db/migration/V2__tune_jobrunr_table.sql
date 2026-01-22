-- JobRunr creates high churn (inserts/deletes).
-- Aggressive autovacuum prevents table bloat (dead tuples).
-- Vacuum when 1% of rows change (default is usually 20%).

ALTER TABLE jobrunr_jobs SET (
  autovacuum_vacuum_scale_factor = 0.01,
  autovacuum_vacuum_threshold = 100,
  autovacuum_analyze_scale_factor = 0.02
);
