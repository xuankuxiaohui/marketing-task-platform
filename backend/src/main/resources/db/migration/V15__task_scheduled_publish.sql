-- Add scheduled_publish_at column to task table for scheduled publishing
ALTER TABLE task ADD COLUMN scheduled_publish_at DATETIME NULL COMMENT '定时发布时间';
