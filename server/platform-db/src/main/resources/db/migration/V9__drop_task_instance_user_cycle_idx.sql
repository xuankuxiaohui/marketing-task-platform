-- Portal list now uses (task_id, cycle_key) IN (uk_user_task_cycle).
-- EXPLAIN: with idx_user_cycle present, optimizer prefers it over uk for tuple IN
-- (misleading rows≈2); IGNORE INDEX / drop → uk_user_task_cycle rows=N pairs.
-- V8 idx was for cycle_key-only / dual-IN; those paths are not the list hot path.
ALTER TABLE task_instance
  DROP INDEX idx_user_cycle;
