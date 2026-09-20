-- session 表（platform 卡 §2/§3.1 字段级真源；主键 = cookie ZT_SESSION 值，64 位 hex）。
-- 流水表例外：无审计四件套（02 §5）；登出物理删行；过期行惰性清理。
CREATE TABLE session (
    id           VARCHAR(64)  NOT NULL PRIMARY KEY,
    account_id   BIGINT       NOT NULL,
    account      VARCHAR(64)  NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at   TIMESTAMP    NOT NULL,
    last_seen_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip           VARCHAR(45)  NULL,
    user_agent   VARCHAR(255) NULL
);
