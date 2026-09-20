-- 06 A7-5 种子口令外置 + 首登强制改密：账号表加只读标记列。
-- 历史迁移不可改（V2__org.sql 已执行过），故以新迁移补列；既有库全部行落默认 0（不追溯强制改密）。
-- 标记语义：初始口令（种子随机口令 / 迁移工具随机口令）置 1，本人改密成功后清 0（org 卡 §3.1/§4）。
ALTER TABLE account ADD COLUMN must_change_password TINYINT NOT NULL DEFAULT 0;
