-- ============================================================
-- V3__seed_demo_data.sql
-- Seed data demonstrating Sprint 2 features (MONTHLY period,
-- CALLBACK / PROGRESS step types, multi-platform config).
-- All IDs are explicitly assigned (< 100) to avoid collision
-- with Snowflake-generated IDs in production.
-- ============================================================

-- ------------------------------------------------------------
-- Task 1: 日签到 (daily_checkin)
-- DAILY period, PASSIVE -> CLICK -> REWARD, with filter
-- ------------------------------------------------------------
INSERT INTO task (id, code, name, description, period_type, start_time, end_time, status, version, created_by, updated_by)
VALUES (1, 'daily_checkin', '日签到', '每天签到，领取积分奖励',
        'DAILY', '2026-05-01 00:00:00', '2026-12-31 23:59:59',
        'PUBLISHED', 1, 'system', 'system');

INSERT INTO task_step (id, task_id, seq, code, name, type, flow_desc)
VALUES (10, 1, 1, 'enter', '进入任务', 'PASSIVE', '进入任务页面');

INSERT INTO task_step (id, task_id, seq, code, name, type, flow_desc)
VALUES (11, 1, 2, 'sign', '点击签到', 'CLICK', '点击签到按钮');

INSERT INTO task_step (id, task_id, seq, code, name, type, reward_config_json, flow_desc)
VALUES (12, 1, 3, 'reward', '领取积分', 'REWARD', '{"type":"point","amount":10}', '领取+10积分奖励');

-- Filter: only BJ users with level >= 3
INSERT INTO task_filter (id, task_id, seq, expression, logic_op, description, enabled)
VALUES (20, 1, 1, 'inProvince([''BJ'']) && levelGte(3)', 'AND', '仅BJ用户且等级>=3', 1);

-- Platform configs
INSERT INTO task_platform (id, task_id, platform, flow_desc, button_text, enabled)
VALUES (30, 1, 'WEB', '在网页端签到', '去签到', 1);

INSERT INTO task_platform (id, task_id, platform, flow_desc, button_text, enabled)
VALUES (31, 1, 'ADMIN', '在管理后台签到', '去签到', 1);


-- ------------------------------------------------------------
-- Task 2: 月度调研 (monthly_survey)
-- MONTHLY period, CALLBACK -> REWARD, no filter, WEB only
-- ------------------------------------------------------------
INSERT INTO task (id, code, name, description, period_type, start_time, end_time, status, version, created_by, updated_by)
VALUES (2, 'monthly_survey', '月度调研', '每月填写调研问卷，领取奖励',
        'MONTHLY', '2026-05-01 00:00:00', '2026-12-31 23:59:59',
        'PUBLISHED', 1, 'system', 'system');

-- CALLBACK step: waits for external survey_completed event
INSERT INTO task_step (id, task_id, seq, code, name, type, callback_event_key, flow_desc)
VALUES (13, 2, 1, 'survey', '填写问卷', 'CALLBACK', 'survey_completed', '完成调研问卷');

INSERT INTO task_step (id, task_id, seq, code, name, type, reward_config_json, flow_desc)
VALUES (14, 2, 2, 'reward', '领取奖励', 'REWARD', '{"type":"coupon","amount":1}', '领取一张优惠券');

-- No filters — visible to all users

INSERT INTO task_platform (id, task_id, platform, flow_desc, button_text, enabled)
VALUES (32, 2, 'WEB', '完成月度调研', '去填写', 1);


-- ------------------------------------------------------------
-- Task 3: 阅读挑战 (reading_challenge)
-- ONCE period, PROGRESS -> REWARD, no filter, WEB + ADMIN
-- ------------------------------------------------------------
INSERT INTO task (id, code, name, description, period_type, start_time, end_time, status, version, created_by, updated_by)
VALUES (3, 'reading_challenge', '阅读挑战', '累计阅读3篇文章，获得读者勋章',
        'ONCE', '2026-05-01 00:00:00', '2026-12-31 23:59:59',
        'PUBLISHED', 1, 'system', 'system');

-- PROGRESS step: requires 3 articles read (target_value = 3)
INSERT INTO task_step (id, task_id, seq, code, name, type, target_value, flow_desc)
VALUES (15, 3, 1, 'read', '阅读文章', 'PROGRESS', 3, '阅读文章，累计3篇');

INSERT INTO task_step (id, task_id, seq, code, name, type, reward_config_json, flow_desc)
VALUES (16, 3, 2, 'reward', '领取奖励', 'REWARD', '{"type":"badge","name":"reader"}', '领取读者勋章');

-- No filters — visible to all users

INSERT INTO task_platform (id, task_id, platform, flow_desc, button_text, enabled)
VALUES (33, 3, 'WEB', '阅读文章完成任务', '去阅读', 1);

INSERT INTO task_platform (id, task_id, platform, flow_desc, button_text, enabled)
VALUES (34, 3, 'ADMIN', '阅读文章完成任务', '去阅读', 1);
