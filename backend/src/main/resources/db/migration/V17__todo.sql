-- workspace 域待办表（workspace 卡 §2/§3.1 字段级真源）。
-- begin_time/end_time 存 HHmm char(4)；周期相关旧列不迁移（§1 不做项）；旧库无创建时间列，created_at 迁移取 date 兜底。
CREATE TABLE todo (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title         VARCHAR(150) NOT NULL,
    type          VARCHAR(15)  NOT NULL DEFAULT 'custom',
    object_id     BIGINT       NOT NULL DEFAULT 0,
    todo_date     DATE         NULL,
    begin_time    CHAR(4)      NULL,
    end_time      CHAR(4)      NULL,
    priority      TINYINT      NOT NULL DEFAULT 3,
    description   TEXT         NULL,
    status        VARCHAR(10)  NOT NULL DEFAULT 'wait',
    is_private    TINYINT      NOT NULL DEFAULT 0,
    assignee      VARCHAR(64)  NOT NULL,
    assigned_by   VARCHAR(64)  NULL,
    assigned_at   TIMESTAMP    NULL,
    finished_by   VARCHAR(64)  NULL,
    finished_at   TIMESTAMP    NULL,
    closed_by     VARCHAR(64)  NULL,
    closed_at     TIMESTAMP    NULL,
    created_by    VARCHAR(64)  NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(64)  NULL,
    updated_at    TIMESTAMP    NULL,
    lock_version  INT          NOT NULL DEFAULT 0,
    deleted_at    TIMESTAMP    NULL
);
CREATE INDEX idx_todo_assignee_status ON todo (assignee, status, deleted_at);
CREATE INDEX idx_todo_date ON todo (todo_date);
