-- Hot-path EXPLAIN for task 49 slow-query review (design §7.8 / §2.6).
-- Run against capacity seed (1M portal users / 500k instances / 5M event rows).

EXPLAIN SELECT id, code, name, status, sort_weight
FROM task_definition
WHERE deleted = 0 AND status = 'PUBLISHED'
ORDER BY sort_weight ASC, id ASC;

EXPLAIN SELECT id, task_id, user_id, status, started_at
FROM task_instance
ORDER BY id DESC
LIMIT 20 OFFSET 0;

EXPLAIN SELECT COUNT(*) FROM task_instance;

EXPLAIN SELECT id, code, name, status
FROM task_definition
WHERE deleted = 0
ORDER BY id DESC
LIMIT 20 OFFSET 0;

EXPLAIN SELECT id, user_id, type, amount, created_at
FROM pnt_transaction
ORDER BY created_at DESC, id DESC
LIMIT 20 OFFSET 0;

EXPLAIN SELECT id, code, form, status
FROM ad_position
WHERE code = 'home_banner' AND deleted = 0;

EXPLAIN SELECT id, position_id, material_id, weight, status
FROM ad_position_material
WHERE position_id = 1
ORDER BY weight DESC, material_id ASC;

EXPLAIN SELECT id, event_code, server_time
FROM evt_event_log
WHERE server_time >= UTC_TIMESTAMP() - INTERVAL 1 DAY
  AND server_time < UTC_TIMESTAMP()
ORDER BY server_time DESC, id DESC
LIMIT 20 OFFSET 0;

EXPLAIN SELECT id, event_code, server_time
FROM evt_event_log
WHERE user_id = 1
ORDER BY server_time DESC, id DESC
LIMIT 20 OFFSET 0;

EXPLAIN SELECT COUNT(*)
FROM evt_event_log
WHERE event_code = 'page.view'
   OR JSON_CONTAINS(events, JSON_OBJECT('code', 'page.view'));

EXPLAIN SELECT id, user_id, status, created_at
FROM task_instance
WHERE user_id = 1 AND status = 'IN_PROGRESS';

EXPLAIN SELECT id, instance_id, step_code, report_id
FROM task_progress_report
WHERE instance_id = 1 AND step_code = 'pg' AND report_id = 'k6-1';

EXPLAIN SELECT id, user_id, status, created_at
FROM rwd_grant_record
WHERE user_id = 1
ORDER BY created_at DESC
LIMIT 20;
