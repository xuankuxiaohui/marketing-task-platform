-- V21: Refactor activity_stats to use activity_code instead of activity_id

-- Step 1: Drop old composite primary key
ALTER TABLE activity_stats DROP PRIMARY KEY;

-- Step 2: Add auto-increment id as new primary key
ALTER TABLE activity_stats ADD COLUMN id BIGINT AUTO_INCREMENT PRIMARY KEY FIRST;

-- Step 3: Add activity_code column
ALTER TABLE activity_stats ADD COLUMN activity_code VARCHAR(64) NOT NULL DEFAULT '' COMMENT '关联活动编码';

-- Step 4: Backfill activity_code from activity table
UPDATE activity_stats ast
    INNER JOIN activity a ON a.id = ast.activity_id
SET ast.activity_code = a.code;

-- Step 5: Add unique index on (activity_code, stat_date)
ALTER TABLE activity_stats ADD UNIQUE INDEX uk_activity_code_date (activity_code, stat_date);

-- Step 6: Drop old activity_id column
ALTER TABLE activity_stats DROP COLUMN activity_id;
