-- requirement 域单表（requirement 卡 §2/§3 字段级真源）：type 区分 story|epic|requirement；version 恒 1（编辑不生成新版本）。
CREATE TABLE story (
    id                BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id        BIGINT        NOT NULL,
    branch_id         BIGINT        NOT NULL DEFAULT 0,
    category_id       BIGINT        NOT NULL DEFAULT 0,
    plan_id           BIGINT        NULL,
    parent_id         BIGINT        NULL,
    title             VARCHAR(255)  NOT NULL,
    keywords          VARCHAR(255)  NULL,
    type              VARCHAR(16)   NOT NULL DEFAULT 'story',
    status            VARCHAR(16)   NOT NULL DEFAULT 'draft',
    priority          INT           NOT NULL DEFAULT 3,
    estimate_hours    DECIMAL(10,2) NULL,
    source            VARCHAR(16)   NOT NULL DEFAULT 'manual',
    description       TEXT          NULL,
    stage             VARCHAR(16)   NOT NULL DEFAULT 'wait',
    assignee          VARCHAR(64)   NULL,
    assigned_at       TIMESTAMP     NULL,
    reviewers         TEXT          NULL,
    need_not_review   TINYINT       NOT NULL DEFAULT 0,
    notify_accounts   TEXT          NULL,
    linked_story_ids  TEXT          NULL,
    duplicate_of_id   BIGINT        NULL,
    version           INT           NOT NULL DEFAULT 1,
    custom_fields     TEXT          NULL,
    created_by        VARCHAR(64)   NULL,
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(64)   NULL,
    updated_at        TIMESTAMP     NULL,
    closed_by         VARCHAR(64)   NULL,
    closed_at         TIMESTAMP     NULL,
    closed_reason     VARCHAR(16)   NULL,
    lock_version      INT           NOT NULL DEFAULT 0,
    deleted_at        TIMESTAMP     NULL
);
CREATE INDEX idx_story_product ON story (product_id);
CREATE INDEX idx_story_plan ON story (plan_id);
CREATE INDEX idx_story_parent ON story (parent_id);
