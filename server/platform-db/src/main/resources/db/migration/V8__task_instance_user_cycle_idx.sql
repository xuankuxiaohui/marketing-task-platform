-- List path loads current instances by user_id + current cycle_key set (few keys).
-- Existing uk_user_task_cycle (user_id, task_id, cycle_key) cannot seek cycle_key without task_id;
-- dual task_id IN (~1k published) dominated MySQL CPU (digest avg ~53ms @ 500 QPS).
-- idx_user_cycle enables: WHERE user_id=? AND cycle_key IN (...) then Java pair filter.
ALTER TABLE task_instance
  ADD KEY idx_user_cycle (user_id, cycle_key) COMMENT 'portal list current-instance by cycle_key (capacity)';
