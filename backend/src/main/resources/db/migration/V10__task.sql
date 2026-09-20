-- task 域两表（task 卡 §2/§3/§3b 字段级真源）。
-- 审计四件套 + deleted_at + lock_version（02 §5）；工时列 DECIMAL(10,2)；父子仅一层（无 path 列）。
CREATE TABLE task (
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    execution_id    BIGINT        NOT NULL,
    project_id      BIGINT        NOT NULL DEFAULT 0,
    story_id        BIGINT        NOT NULL DEFAULT 0,
    parent_id       BIGINT        NOT NULL DEFAULT 0,
    category_id     BIGINT        NOT NULL DEFAULT 0,
    title           VARCHAR(255)  NOT NULL,
    type            VARCHAR(16)   NOT NULL DEFAULT 'devel',
    status          VARCHAR(16)   NOT NULL DEFAULT 'wait',
    priority        INT           NOT NULL DEFAULT 3,
    estimate_hours  DECIMAL(10,2) NULL,
    consumed_hours  DECIMAL(10,2) NOT NULL DEFAULT 0,
    left_hours      DECIMAL(10,2) NULL,
    est_started_date DATE         NULL,
    deadline        DATE          NULL,
    assignee        VARCHAR(64)   NULL,
    assigned_at     TIMESTAMP     NULL,
    started_at      TIMESTAMP     NULL,
    activated_at    TIMESTAMP     NULL,
    finished_by     VARCHAR(64)   NULL,
    finished_at     TIMESTAMP     NULL,
    canceled_by     VARCHAR(64)   NULL,
    canceled_at     TIMESTAMP     NULL,
    closed_by       VARCHAR(64)   NULL,
    closed_at       TIMESTAMP     NULL,
    closed_reason   VARCHAR(32)   NULL,
    keywords        VARCHAR(255)  NULL,
    description     TEXT          NULL,
    is_parent       TINYINT       NOT NULL DEFAULT 0,
    notify_accounts TEXT          NULL,
    custom_fields   TEXT          NULL,
    created_by      VARCHAR(64)   NULL,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64)   NULL,
    updated_at      TIMESTAMP     NULL,
    lock_version    INT           NOT NULL DEFAULT 0,
    deleted_at      TIMESTAMP     NULL
);
CREATE INDEX idx_task_execution_status ON task (execution_id, status, deleted_at);
CREATE INDEX idx_task_assignee ON task (assignee);
CREATE INDEX idx_task_story ON task (story_id);
CREATE INDEX idx_task_parent ON task (parent_id);

-- 工时流水：仅挂任务（旧多态 objectType 砍掉），登记粒度=天，同人同日可多条（无唯一约束）。
CREATE TABLE effort (
    id             BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_id        BIGINT        NOT NULL,
    execution_id   BIGINT        NOT NULL DEFAULT 0,
    project_id     BIGINT        NOT NULL DEFAULT 0,
    account        VARCHAR(64)   NOT NULL,
    work_date      DATE          NOT NULL,
    consumed_hours DECIMAL(10,2) NOT NULL,
    left_hours     DECIMAL(10,2) NULL,
    work           VARCHAR(255)  NULL,
    created_by     VARCHAR(64)   NULL,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64)   NULL,
    updated_at     TIMESTAMP     NULL,
    deleted_at     TIMESTAMP     NULL
);
CREATE INDEX idx_effort_task ON effort (task_id, deleted_at);
CREATE INDEX idx_effort_account_date ON effort (account, work_date);
