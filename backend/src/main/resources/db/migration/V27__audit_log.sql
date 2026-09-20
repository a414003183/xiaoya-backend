-- B1 可观测性地基：业务审计日志（H3）。登录与每次成功的写请求各留一行，
-- 含操作人（account）/动作（action）/来源 IP（ip）/链路 id（trace_id），供事故复盘与合规查询。
-- account 可空：登录前失败等场景无主体，但行仍要留（谁在什么 IP 试过什么）。
CREATE TABLE audit_log (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    account     VARCHAR(64) NULL,
    action      VARCHAR(64) NOT NULL,
    object_type VARCHAR(64) NULL,
    object_id   BIGINT      NULL,
    detail      TEXT        NULL,
    ip          VARCHAR(64) NULL,
    trace_id    VARCHAR(64) NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_audit_log_account ON audit_log (account, created_at);
