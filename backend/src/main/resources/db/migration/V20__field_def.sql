-- field_def：自定义字段定义（platform 卡 §3.10 / A-05 补口 2026-09-19）。
-- 无在线管理 UI（04 横切裁决：SQL/种子维护），无软删；options/visible_when 为 JSON 文本列（H2/MySQL 双方言通吃）。
CREATE TABLE field_def (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    domain       VARCHAR(60)  NOT NULL,
    item_key     VARCHAR(60)  NOT NULL,
    type         VARCHAR(20)  NOT NULL,
    required     TINYINT      NOT NULL DEFAULT 0,
    options      TEXT         NULL,
    visible_when TEXT         NULL,
    sort         INT          NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   VARCHAR(64)  NULL,
    updated_at   TIMESTAMP    NULL,
    CONSTRAINT uq_field_def UNIQUE (domain, item_key)
);
