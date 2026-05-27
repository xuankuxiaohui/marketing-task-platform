-- Add deleted column to task table for soft delete
ALTER TABLE task ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0=正常 1=已删除';
