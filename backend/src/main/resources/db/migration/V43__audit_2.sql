-- T04 审计 2.0（ADR-004）：audit_log 从「一刀切流水」升级为分级审计表——加 分类/结果/失败原因/批次/
-- UA 与设备摘要/MFA（预留）/三份 JSON（changes·snapshot·extra）/哈希链两列；新增查询聚合表 audit_query_stat。
-- 只追加表：本迁移只加列 + 回填历史行，不改写既有语义；生产授权规约（应用账号只授 INSERT/SELECT）不变。
-- 时间列政策（ADR-011 决策 3）：created_at 是存量 TIMESTAMP（秒精度）列，保持不动——审计哈希取「截断到秒」
-- 的值计算（AuditHasher），使 H2（TIMESTAMP 默认微秒）与 MySQL（TIMESTAMP(0) 秒）两侧一致。
-- 回填口径：V43 之前只有三类行——登录成功（action=login）、登录失败（login-failed）、成功的写请求（其余），
-- 故分类与结果可确定回填；此后由 AuditRecorder 显式写入（category/result 紧随其后收紧为 NOT NULL）。

ALTER TABLE audit_log ADD COLUMN category VARCHAR(16) NULL;
ALTER TABLE audit_log ADD COLUMN result VARCHAR(16) NULL;
ALTER TABLE audit_log ADD COLUMN reason VARCHAR(255) NULL;
ALTER TABLE audit_log ADD COLUMN batch_id BIGINT NULL;
ALTER TABLE audit_log ADD COLUMN ua VARCHAR(255) NULL;
ALTER TABLE audit_log ADD COLUMN device VARCHAR(64) NULL;
ALTER TABLE audit_log ADD COLUMN mfa VARCHAR(32) NULL;
ALTER TABLE audit_log ADD COLUMN changes TEXT NULL;
ALTER TABLE audit_log ADD COLUMN snapshot TEXT NULL;
ALTER TABLE audit_log ADD COLUMN extra TEXT NULL;
ALTER TABLE audit_log ADD COLUMN prev_hash CHAR(64) NULL;
ALTER TABLE audit_log ADD COLUMN hash CHAR(64) NULL;

UPDATE audit_log SET category = CASE WHEN action LIKE 'login%' THEN 'auth' ELSE 'business' END WHERE category IS NULL;
UPDATE audit_log SET result = CASE WHEN action = 'login-failed' THEN 'fail' ELSE 'success' END WHERE result IS NULL;

ALTER TABLE audit_log MODIFY COLUMN category VARCHAR(16) NOT NULL;
ALTER TABLE audit_log MODIFY COLUMN result VARCHAR(16) NOT NULL;

-- 取值集真源 = platform.audit.AuditCatalog（分类）与 AuditResult（结果）；应用层给用户 42201/40001，
-- CHECK 兜住绕过守卫的直写库 bug（违约按 CONVENTIONS §2.3 走 50001 fail-loud）。
ALTER TABLE audit_log ADD CONSTRAINT ck_audit_log_category
    CHECK (category IN ('auth', 'perm', 'config', 'business', 'batch', 'export', 'sensitive', 'query', 'approve'));
ALTER TABLE audit_log ADD CONSTRAINT ck_audit_log_result
    CHECK (result IN ('success', 'fail', 'denied'));

-- 审计页筛选面（分类/结果 + 倒序取数）与详情/校验按 id 扫描；批次视图按 batch_id 拉同批记录。
CREATE INDEX idx_audit_log_category ON audit_log (category, id);
CREATE INDEX idx_audit_log_batch ON audit_log (batch_id);

-- 查询聚合表（ADR-004 决策 3）：普通查询/列表不逐条进主表，按 账号+模块+天 累加次数与耗时。
-- query_count/total_ms 是累加列，写入走「单条 SQL 自增」（CONVENTIONS §2.4，禁读改写）。
-- 聚合日列名 stat_day 而非 day：DAY 在 H2 是关键字（建表直接 42001 语法错，实测），API 字段仍叫 day。
CREATE TABLE audit_query_stat (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    account     VARCHAR(64) NOT NULL,
    resource      VARCHAR(64) NOT NULL,
    stat_day    DATE        NOT NULL,
    query_count BIGINT      NOT NULL DEFAULT 0,
    total_ms    BIGINT      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_audit_query_stat ON audit_query_stat (account, resource, stat_day);
