-- V19__activity_code_unify.sql

-- 1. activity_display_rule: add activity_code, backfill, restructure PK
-- Step 1: Add activity_code column
ALTER TABLE activity_display_rule ADD COLUMN activity_code VARCHAR(64) NOT NULL DEFAULT '' COMMENT '关联活动编码';

-- Step 2: Backfill from activity table
UPDATE activity_display_rule adr
    INNER JOIN activity a ON a.id = adr.activity_id
    SET adr.activity_code = a.code;

-- Step 3: Drop old PK
ALTER TABLE activity_display_rule DROP PRIMARY KEY;

-- Step 4: Add auto-increment id as new PK
ALTER TABLE activity_display_rule ADD COLUMN id BIGINT AUTO_INCREMENT PRIMARY KEY FIRST;

-- Step 5: Add unique index on activity_code
ALTER TABLE activity_display_rule ADD UNIQUE INDEX uk_activity_code (activity_code);

-- Step 6: Drop old activity_id column
ALTER TABLE activity_display_rule DROP COLUMN activity_id;
