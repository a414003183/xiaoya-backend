-- doc 域四表（doc 卡 §2/§3 字段级真源）。
-- 审计四件套 + deleted_at + lock_version（02 §5）；JSON 列（whitelist/editors/readers/notify_accounts/files）为 TEXT。
-- 软删不级联物理删：快照随主表软删隐藏（doc 卡 §2）。
CREATE TABLE doc_space (
    id            BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(60) NOT NULL,
    type          VARCHAR(16) NOT NULL DEFAULT 'custom',
    product_id    BIGINT      NOT NULL DEFAULT 0,
    project_id    BIGINT      NOT NULL DEFAULT 0,
    execution_id  BIGINT      NOT NULL DEFAULT 0,
    acl           VARCHAR(16) NOT NULL DEFAULT 'open',
    whitelist     TEXT        NULL,
    description   TEXT        NULL,
    doc_sort      VARCHAR(16) NOT NULL DEFAULT 'id_asc',
    is_default    TINYINT     NOT NULL DEFAULT 0,
    sort          INT         NOT NULL DEFAULT 0,
    created_by    VARCHAR(64) NULL,
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(64) NULL,
    updated_at    TIMESTAMP   NULL,
    lock_version  INT         NOT NULL DEFAULT 0,
    deleted_at    TIMESTAMP   NULL
);
CREATE INDEX idx_doc_space_type ON doc_space (type, deleted_at);
CREATE INDEX idx_doc_space_product ON doc_space (product_id, deleted_at);
CREATE INDEX idx_doc_space_project ON doc_space (project_id, deleted_at);
CREATE INDEX idx_doc_space_execution ON doc_space (execution_id, deleted_at);

CREATE TABLE doc (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    doc_space_id    BIGINT       NOT NULL,
    product_id      BIGINT       NOT NULL DEFAULT 0,
    project_id      BIGINT       NOT NULL DEFAULT 0,
    execution_id    BIGINT       NOT NULL DEFAULT 0,
    category_id     BIGINT       NOT NULL DEFAULT 0,
    parent_id       BIGINT       NOT NULL DEFAULT 0,
    path            VARCHAR(255) NOT NULL DEFAULT '',
    title           VARCHAR(255) NOT NULL,
    keywords        VARCHAR(255) NULL,
    type            VARCHAR(16)  NOT NULL DEFAULT 'markdown',
    status          VARCHAR(16)  NOT NULL DEFAULT 'draft',
    acl             VARCHAR(16)  NOT NULL DEFAULT 'open',
    editors         TEXT         NULL,
    readers         TEXT         NULL,
    notify_accounts TEXT         NULL,
    views           INT          NOT NULL DEFAULT 0,
    version         INT          NOT NULL DEFAULT 0,
    sort            INT          NOT NULL DEFAULT 0,
    created_by      VARCHAR(64)  NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64)  NULL,
    updated_at      TIMESTAMP    NULL,
    lock_version    INT          NOT NULL DEFAULT 0,
    deleted_at      TIMESTAMP    NULL
);
CREATE INDEX idx_doc_space_status ON doc (doc_space_id, status, deleted_at);
CREATE INDEX idx_doc_product ON doc (product_id, deleted_at);
CREATE INDEX idx_doc_parent ON doc (parent_id);
CREATE INDEX idx_doc_path ON doc (path);
CREATE INDEX idx_doc_category ON doc (category_id);

CREATE TABLE doc_content (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    doc_id      BIGINT       NOT NULL,
    version     INT          NOT NULL,
    title       VARCHAR(255) NOT NULL,
    content     TEXT         NULL,
    digest      VARCHAR(255) NULL,
    files       TEXT         NULL,
    created_by  VARCHAR(64)  NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  VARCHAR(64)  NULL,
    updated_at  TIMESTAMP    NULL
);
-- UNIQUE(doc_id, version) 兼作查询索引：version=0 为草稿工作副本，version>=1 为不可变发布快照（doc 卡 §2/§3.3）。
CREATE UNIQUE INDEX uk_doc_content_doc_version ON doc_content (doc_id, version);

CREATE TABLE doc_category (
    id           BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    doc_space_id BIGINT      NOT NULL,
    parent_id    BIGINT      NOT NULL DEFAULT 0,
    name         VARCHAR(60) NOT NULL,
    sort         INT         NOT NULL DEFAULT 0,
    created_by   VARCHAR(64) NULL,
    created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   VARCHAR(64) NULL,
    updated_at   TIMESTAMP   NULL,
    lock_version INT         NOT NULL DEFAULT 0,
    deleted_at   TIMESTAMP   NULL
);
CREATE INDEX idx_doc_category_space ON doc_category (doc_space_id, parent_id);
