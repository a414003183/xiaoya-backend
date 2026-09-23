-- T67 / AUDIT DB-17（精度半）：工时家族精度统一到 DECIMAL(12,2)。
--
-- 清点现状（工时/预算家族）：`DECIMAL(12,2)`（burn/weekly_report 的聚合值、project.budget、story_point）、
-- `DECIMAL(10,2)`（task/effort/project/story/board_card 的工时列）、`DECIMAL(4,1)`（team_member.hours）三种口径并存。
-- 统一口径 = **DECIMAL(12,2)**：家族里既有最宽值，且「只放宽不收窄」（收窄会截断存量）——
-- (10,2)→(12,2) 与 (4,1)→(12,2) 都是纯放宽（整数位与小数位同时变宽），已是 (12,2) 的列不动。
-- 语义：小时数保留两位小数、整数位最大 10 位；应用层的业务上限不动（TaskFields.HOURS_MAX=999.99、
-- StoryFields.ESTIMATE_MAX=999.99 等仍是用户可见的上限），本迁移只统一**存储口径**，不放宽业务校验。
-- `percent`（DECIMAL(5,2)）与 pv/ev/ac/sv/cv（EVM 值，已 12,2）不属于工时口径，不动。
-- 方言（T52 口径）：`MODIFY COLUMN` 重述类型 + 可空性 + DEFAULT（H2 MODE=MySQL 与 MySQL 8 双双合法，
-- DEFAULT 重述不许省——MODIFY 是全量重定义，省了会静默丢默认值）。
-- 回滚 = 反向 MODIFY COLUMN 回原类型（放宽可逆，只是回窄会截断新写入的超宽值）。

ALTER TABLE task MODIFY COLUMN estimate_hours DECIMAL(12,2) NULL;
ALTER TABLE task MODIFY COLUMN consumed_hours DECIMAL(12,2) NOT NULL DEFAULT 0;
ALTER TABLE task MODIFY COLUMN left_hours DECIMAL(12,2) NULL;
ALTER TABLE effort MODIFY COLUMN consumed_hours DECIMAL(12,2) NOT NULL;
ALTER TABLE effort MODIFY COLUMN left_hours DECIMAL(12,2) NULL;
ALTER TABLE project MODIFY COLUMN estimate_hours DECIMAL(12,2) NOT NULL DEFAULT 0;
ALTER TABLE project MODIFY COLUMN consumed_hours DECIMAL(12,2) NOT NULL DEFAULT 0;
ALTER TABLE project MODIFY COLUMN left_hours DECIMAL(12,2) NOT NULL DEFAULT 0;
ALTER TABLE story MODIFY COLUMN estimate_hours DECIMAL(12,2) NULL;
ALTER TABLE board_card MODIFY COLUMN estimate_hours DECIMAL(12,2) NULL;
ALTER TABLE team_member MODIFY COLUMN hours DECIMAL(12,2) NOT NULL DEFAULT 0;
