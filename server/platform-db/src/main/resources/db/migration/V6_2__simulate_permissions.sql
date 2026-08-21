-- V6.2 simulate admin menu + appendix B permissions (design §4.4.2 / R24). Do not edit V1–V6_1.
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

INSERT INTO sys_permission (id, parent_id, type, code, name, route, component, icon, sort, status) VALUES
(50, 0,  'MENU',      NULL,             '任务模拟器', '/simulate', 'simulate/index', NULL, 50, 'ENABLED'),
(51, 50, 'OPERATION', 'simulate:task',  '模拟任务',   NULL, NULL, NULL, 1, 'ENABLED'),
(52, 50, 'OPERATION', 'simulate:flow',  '一键全流程', NULL, NULL, NULL, 2, 'ENABLED');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission WHERE id BETWEEN 50 AND 52;
