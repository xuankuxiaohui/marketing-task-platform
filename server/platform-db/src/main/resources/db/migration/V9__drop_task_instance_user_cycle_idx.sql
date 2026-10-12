-- Portal list uses one-shot (task_id, cycle_key) IN on uk_user_task_cycle.
-- EXPLAIN: idx_user_cycle steals the tuple-IN plan; drop so uk is chosen (rows=N).
ALTER TABLE task_instance
  DROP INDEX idx_user_cycle;
