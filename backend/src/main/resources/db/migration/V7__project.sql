-- project 域四表（project 卡 §2/§3.1/§3.7/§3.8 字段级真源）。
-- 审计四件套 + deleted_at + lock_version（02 §5）；一表三义 type ∈ program|project|sprint|stage|kanban。
CREATE TABLE project (
    id             BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    type           VARCHAR(16)   NOT NULL DEFAULT 'project',
    parent_id      BIGINT        NOT NULL DEFAULT 0,
    path           VARCHAR(255)  NOT NULL DEFAULT '',
    grade          INT           NOT NULL DEFAULT 1,
    name           VARCHAR(90)   NOT NULL,
    code           VARCHAR(45)   NULL,
    model          VARCHAR(16)   NOT NULL DEFAULT 'scrum',
    status         VARCHAR(16)   NOT NULL DEFAULT 'wait',
    priority       INT           NOT NULL DEFAULT 1,
    begin_date     DATE          NULL,
    end_date       DATE          NULL,
    first_end_date DATE          NULL,
    real_began_date DATE         NULL,
    real_end_date  DATE          NULL,
    days           INT           NOT NULL DEFAULT 0,
    budget         DECIMAL(12,2) NULL,
    budget_unit    VARCHAR(8)    NOT NULL DEFAULT 'CNY',
    description    TEXT          NULL,
    pm             VARCHAR(64)   NULL,
    po             VARCHAR(64)   NULL,
    qd             VARCHAR(64)   NULL,
    rd             VARCHAR(64)   NULL,
    progress       INT           NOT NULL DEFAULT 0,
    estimate_hours DECIMAL(10,2) NOT NULL DEFAULT 0,
    consumed_hours DECIMAL(10,2) NOT NULL DEFAULT 0,
    left_hours     DECIMAL(10,2) NOT NULL DEFAULT 0,
    is_milestone   TINYINT       NOT NULL DEFAULT 0,
    acl            VARCHAR(16)   NOT NULL DEFAULT 'open',
    sort           INT           NOT NULL DEFAULT 0,
    custom_fields  TEXT          NULL,
    created_by     VARCHAR(64)   NULL,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64)   NULL,
    updated_at     TIMESTAMP     NULL,
    closed_by      VARCHAR(64)   NULL,
    closed_at      TIMESTAMP     NULL,
    lock_version   INT           NOT NULL DEFAULT 0,
    deleted_at     TIMESTAMP     NULL
);
CREATE INDEX idx_project_parent ON project (parent_id);
CREATE INDEX idx_project_type_status ON project (type, status);

-- 项目↔产品关联（唯一索引；全量替换走 diff）。
CREATE TABLE project_product (
    id         BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL
);
CREATE UNIQUE INDEX uk_project_product ON project_product (project_id, product_id);

-- 项目/执行↔需求关联（关联幂等；project_id 存项目或执行 id）。
CREATE TABLE project_story (
    id         BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    story_id   BIGINT NOT NULL,
    product_id BIGINT NOT NULL DEFAULT 0,
    sort       INT    NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_project_story ON project_story (project_id, story_id);

-- 白名单单源（project 卡 §2：旧 zt_acl + 对象表 CSV 镜像两写收敛为本表）。
CREATE TABLE acl_entry (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    object_type VARCHAR(32) NOT NULL,
    object_id   BIGINT      NOT NULL,
    account     VARCHAR(64) NOT NULL,
    entry_type  VARCHAR(32) NOT NULL DEFAULT 'whitelist'
);
CREATE UNIQUE INDEX uk_acl_entry ON acl_entry (object_type, object_id, account, entry_type);
