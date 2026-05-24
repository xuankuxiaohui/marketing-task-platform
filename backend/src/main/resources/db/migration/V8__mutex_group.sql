CREATE TABLE mutex_group (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(64)  NOT NULL,
    description VARCHAR(256),
    scope       VARCHAR(32)  NOT NULL DEFAULT 'SAME_CYCLE',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE task ADD COLUMN mutex_group_id BIGINT NULL;
ALTER TABLE task ADD INDEX idx_task_mutex_group_id (mutex_group_id);

-- Migrate existing mutex_group_key data: create a group per unique key
INSERT INTO mutex_group (name, scope)
SELECT DISTINCT CONCAT('互斥组-', mutex_group_key), 'SAME_CYCLE'
FROM task WHERE mutex_group_key IS NOT NULL AND mutex_group_key != '';

UPDATE task t
JOIN mutex_group mg ON mg.name = CONCAT('互斥组-', t.mutex_group_key)
SET t.mutex_group_id = mg.id
WHERE t.mutex_group_key IS NOT NULL AND t.mutex_group_key != '';

ALTER TABLE task DROP INDEX idx_task_mutex_group;
ALTER TABLE task DROP COLUMN mutex_group_key;
