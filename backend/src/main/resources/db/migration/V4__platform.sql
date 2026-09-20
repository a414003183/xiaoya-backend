-- platform 域六表（platform 卡 §2/§3 字段级真源）。
-- 流水表（activity/comment/notification）无 updated_by/updated_at/lock_version（02 §5 例外）；软删仅 notification/file。
CREATE TABLE activity (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    object_type VARCHAR(64) NOT NULL,
    object_id   BIGINT      NOT NULL,
    actor       VARCHAR(64) NOT NULL,
    action      VARCHAR(64) NOT NULL,
    detail      TEXT        NULL,
    remark      TEXT        NULL,
    occurred_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_activity_object ON activity (object_type, object_id);

CREATE TABLE comment (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    object_type VARCHAR(64)  NOT NULL,
    object_id   BIGINT       NOT NULL,
    content     TEXT         NOT NULL,
    created_by  VARCHAR(64)  NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_comment_object ON comment (object_type, object_id);

CREATE TABLE notification (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    recipient   VARCHAR(64)  NOT NULL,
    type        VARCHAR(64)  NOT NULL,
    object_type VARCHAR(64)  NULL,
    object_id   BIGINT       NOT NULL DEFAULT 0,
    activity_id BIGINT       NULL,
    title       VARCHAR(255) NOT NULL,
    content     TEXT         NULL,
    read_at     TIMESTAMP    NULL,
    created_by  VARCHAR(64)  NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at  TIMESTAMP    NULL
);
CREATE INDEX idx_notification_recipient ON notification (recipient, read_at);

CREATE TABLE file (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title       VARCHAR(255) NOT NULL,
    path        VARCHAR(255) NOT NULL,
    extension   VARCHAR(30)  NOT NULL DEFAULT '',
    size        BIGINT       NOT NULL,
    object_type VARCHAR(64)  NOT NULL DEFAULT '',
    object_id   BIGINT       NOT NULL DEFAULT 0,
    downloads   BIGINT       NOT NULL DEFAULT 0,
    created_by  VARCHAR(64)  NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at  TIMESTAMP    NULL
);

-- setting：key/value 为 MySQL 保留字故列名 item_key/item_value；owner=system 全局，owner=账号 个人级。
CREATE TABLE setting (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner      VARCHAR(64) NOT NULL DEFAULT 'system',
    domain     VARCHAR(60) NOT NULL,
    section    VARCHAR(60) NOT NULL DEFAULT '',
    item_key   VARCHAR(60) NOT NULL,
    item_value TEXT        NULL,
    CONSTRAINT uq_setting UNIQUE (owner, domain, section, item_key)
);

-- lang_item：仅存服务端覆盖层，读取 = 代码内建默认 + 覆盖合并（platform 卡 §3.8）。
CREATE TABLE lang_item (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    lang       VARCHAR(30) NOT NULL DEFAULT 'zh-cn',
    domain     VARCHAR(60) NOT NULL,
    section    VARCHAR(60) NOT NULL,
    item_key   VARCHAR(60) NOT NULL,
    item_value TEXT        NULL,
    CONSTRAINT uq_lang_item UNIQUE (lang, domain, section, item_key)
);
