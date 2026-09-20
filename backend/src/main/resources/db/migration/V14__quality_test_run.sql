-- 测试单两表（quality 卡 §2/§3.4/§3.5）：test_run + test_run_case（执行结果，UNIQUE 幂等 upsert，仅存最新结果）。
CREATE TABLE test_run (
    id                BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id        BIGINT        NOT NULL,
    project_id        BIGINT        NOT NULL DEFAULT 0,
    execution_id      BIGINT        NOT NULL,
    build_id          BIGINT        NOT NULL DEFAULT 0,
    name              VARCHAR(90)   NOT NULL,
    owner             VARCHAR(64)   NULL,
    priority          INT           NOT NULL DEFAULT 3,
    type              VARCHAR(16)   NULL,
    begin_date        DATE          NOT NULL,
    end_date          DATE          NOT NULL,
    real_began_at     TIMESTAMP     NULL,
    real_finished_at  TIMESTAMP     NULL,
    description       TEXT          NULL,
    members           TEXT          NULL,
    notify_accounts   TEXT          NULL,
    status            VARCHAR(16)   NOT NULL DEFAULT 'wait',
    report_id         BIGINT        NULL,
    custom_fields     TEXT          NULL,
    created_by        VARCHAR(64)   NULL,
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(64)   NULL,
    updated_at        TIMESTAMP     NULL,
    lock_version      INT           NOT NULL DEFAULT 0,
    deleted_at        TIMESTAMP     NULL
);
CREATE INDEX idx_run_product ON test_run (product_id);
CREATE INDEX idx_run_execution ON test_run (execution_id);

CREATE TABLE test_run_case (
    id                BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    test_run_id       BIGINT        NOT NULL,
    test_case_id      BIGINT        NOT NULL,
    version           INT           NOT NULL DEFAULT 1,
    assignee          VARCHAR(64)   NULL,
    result            VARCHAR(8)    NULL,
    runner            VARCHAR(64)   NULL,
    run_at            TIMESTAMP     NULL,
    CONSTRAINT uk_run_case UNIQUE (test_run_id, test_case_id)
);
CREATE INDEX idx_run_case_run ON test_run_case (test_run_id);
CREATE INDEX idx_run_case_case ON test_run_case (test_case_id);
