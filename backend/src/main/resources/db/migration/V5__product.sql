-- product 域六表（product 卡 §2/§3 字段级真源）。
-- 审计四件套 + deleted_at + lock_version（02 §5）；product_release 表名固定（release 为 MySQL 保留字，02 §5）。
CREATE TABLE product (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    program_id    BIGINT       NOT NULL DEFAULT 0,
    name          VARCHAR(90)  NOT NULL,
    code          VARCHAR(45)  NULL,
    type          VARCHAR(16)  NOT NULL DEFAULT 'normal',
    status        VARCHAR(16)  NOT NULL DEFAULT 'normal',
    description   TEXT         NULL,
    po            VARCHAR(64)  NULL,
    qd            VARCHAR(64)  NULL,
    rd            VARCHAR(64)  NULL,
    acl           VARCHAR(16)  NOT NULL DEFAULT 'public',
    whitelist     TEXT         NULL,
    sort          INT          NOT NULL DEFAULT 0,
    custom_fields TEXT         NULL,
    created_by    VARCHAR(64)  NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(64)  NULL,
    updated_at    TIMESTAMP    NULL,
    closed_at     TIMESTAMP    NULL,
    lock_version  INT          NOT NULL DEFAULT 0,
    deleted_at    TIMESTAMP    NULL
);
CREATE INDEX idx_product_status ON product (status);

CREATE TABLE branch (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id   BIGINT       NOT NULL,
    name         VARCHAR(255) NOT NULL,
    is_default   TINYINT      NOT NULL DEFAULT 0,
    status       VARCHAR(16)  NOT NULL DEFAULT 'active',
    description  VARCHAR(255) NULL,
    sort         INT          NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   VARCHAR(64)  NULL,
    updated_at   TIMESTAMP    NULL,
    closed_at    TIMESTAMP    NULL,
    lock_version INT          NOT NULL DEFAULT 0,
    deleted_at   TIMESTAMP    NULL
);
CREATE INDEX idx_branch_product ON branch (product_id);

CREATE TABLE category (
    id           BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id   BIGINT      NOT NULL,
    branch_id    BIGINT      NOT NULL DEFAULT 0,
    parent_id    BIGINT      NOT NULL DEFAULT 0,
    type         VARCHAR(16) NOT NULL DEFAULT 'story',
    name         VARCHAR(60) NOT NULL,
    owner        VARCHAR(64) NULL,
    sort         INT         NOT NULL DEFAULT 0,
    created_by   VARCHAR(64) NULL,
    created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   VARCHAR(64) NULL,
    updated_at   TIMESTAMP   NULL,
    lock_version INT         NOT NULL DEFAULT 0,
    deleted_at   TIMESTAMP   NULL
);
CREATE INDEX idx_category_product_type ON category (product_id, type);

CREATE TABLE plan (
    id            BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id    BIGINT      NOT NULL,
    branch_id     BIGINT      NOT NULL DEFAULT 0,
    parent_id     BIGINT      NOT NULL DEFAULT 0,
    title         VARCHAR(90) NOT NULL,
    status        VARCHAR(16) NOT NULL DEFAULT 'wait',
    description   TEXT        NULL,
    begin_date    DATE        NULL,
    end_date      DATE        NULL,
    finished_at   TIMESTAMP   NULL,
    closed_at     TIMESTAMP   NULL,
    closed_reason VARCHAR(16) NULL,
    custom_fields TEXT        NULL,
    created_by    VARCHAR(64) NULL,
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(64) NULL,
    updated_at    TIMESTAMP   NULL,
    lock_version  INT         NOT NULL DEFAULT 0,
    deleted_at    TIMESTAMP   NULL
);
CREATE INDEX idx_plan_product ON plan (product_id);
CREATE INDEX idx_plan_parent ON plan (parent_id);

CREATE TABLE product_release (
    id              BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id      BIGINT      NOT NULL,
    branch_id       BIGINT      NOT NULL DEFAULT 0,
    build_id        BIGINT      NULL,
    project_id      BIGINT      NOT NULL DEFAULT 0,
    name            VARCHAR(90) NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'normal',
    release_date    DATE        NOT NULL,
    published_at    TIMESTAMP   NULL,
    is_milestone    TINYINT     NOT NULL DEFAULT 0,
    story_ids       TEXT        NULL,
    bug_ids         TEXT        NULL,
    notify_accounts TEXT        NULL,
    description     TEXT        NULL,
    created_by      VARCHAR(64) NULL,
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64) NULL,
    updated_at      TIMESTAMP   NULL,
    lock_version    INT         NOT NULL DEFAULT 0,
    deleted_at      TIMESTAMP   NULL
);
CREATE INDEX idx_release_product ON product_release (product_id);
CREATE INDEX idx_release_build ON product_release (build_id);

CREATE TABLE build (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id   BIGINT       NOT NULL,
    branch_id    BIGINT       NOT NULL DEFAULT 0,
    execution_id BIGINT       NOT NULL DEFAULT 0,
    project_id   BIGINT       NOT NULL DEFAULT 0,
    name         VARCHAR(150) NOT NULL,
    scm_path     VARCHAR(255) NULL,
    file_path    VARCHAR(255) NULL,
    build_date   DATE         NOT NULL,
    builder      VARCHAR(64)  NOT NULL,
    story_ids    TEXT         NULL,
    bug_ids      TEXT         NULL,
    description  TEXT         NULL,
    created_by   VARCHAR(64)  NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   VARCHAR(64)  NULL,
    updated_at   TIMESTAMP    NULL,
    lock_version INT          NOT NULL DEFAULT 0,
    deleted_at   TIMESTAMP    NULL
);
CREATE INDEX idx_build_product ON build (product_id);
