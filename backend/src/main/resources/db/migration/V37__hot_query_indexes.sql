-- T53 关键索引补齐（AUDIT DB-02/03/04）：每条索引都对应一条**真实在跑**的查询，行号与判据见 tasks/T53-hot-query-indexes.md §现状。
--
-- 方言政策（T52）：本目录的 SQL 必须同时是 MySQL 8 与 H2(MODE=MySQL) 的合法语句；迁移只跑一次，
-- 故直接 `CREATE INDEX`（不用 MySQL 不认的 `IF NOT EXISTS`）。
-- 回滚 = 反向 `DROP INDEX`（`ALTER TABLE … DROP INDEX <名>`，T52 定的双方言写法）。
--
-- 1) 附件按宿主对象取列表（FileQueryServiceImpl:41 listFiles：object_type + object_id 等值、deleted_at IS NULL、id 倒序）。
--    file 表此前**零索引**，而每个对象详情页都要查一次 → 全表扫。
CREATE INDEX idx_file_object ON file (object_type, object_id);

-- 2) 我的动态流（ActivityRepository:51 cursorByActor：actor 等值 + id 游标倒序）。
--    复合 (actor, id) 同时吃下过滤与排序：既不做全表扫，也不用 filesort。
CREATE INDEX idx_activity_actor ON activity (actor, id);

-- 3) 审计列表默认面（AuditLogQueryService:79-90：无过滤、ORDER BY created_at DESC, id DESC）。
--    原有的 (account, created_at) 只在按账号过滤时用得上——默认列表此前是全表 + filesort。
--    审计表是单调增长的那张，这是本卡最值钱的一条。
CREATE INDEX idx_audit_log_created ON audit_log (created_at, id);

-- 4) 审计按动作过滤（契约 filters[action]，同页筛选器）。
CREATE INDEX idx_audit_log_action ON audit_log (action, created_at);

-- 5) 审计按对象过滤（契约 filters[objectType]+[objectId]；对象详情页的"操作记录"面）。
CREATE INDEX idx_audit_log_object ON audit_log (object_type, object_id, created_at);

-- 6) 角色的反向读（见 V38__user_role_reverse_index.java）——**故意不在本文件**：
--    user_role 是 V33（Java 迁移）建的，而旧库迁移 CLI 的 Flyway 只跑 SQL（Java 迁移无法在那边编译执行），
--    SQL 链里引用它会让旧库导入在 V37 处断链（Table "user_role" not found，实测）。
--    本条不变量：**SQL 迁移链必须自洽，不得引用 Java 迁移建的索引/表**。

-- 未建的（核实过确实没有查询面，索引只增写成本）：
--   * acl_entry 按账号反查：读全部走 (object_type, object_id[, entry_type])，被 uk_acl_entry 前缀覆盖；
--   * role_priv 按 role_id：读全部走 role_id，被 uq_role_priv (role_id, priv_code) 前缀覆盖；
--   * role_priv 按 priv_code 反查：全库无此查询（权限判定是"角色 → 码"方向）。
-- 将来真出现这两个方向的查询，随功能卡一起加索引（见任务卡交接备忘）。
