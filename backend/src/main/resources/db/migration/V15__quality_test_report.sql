-- 测试报告表（quality 卡 §2/§3.6）：content 为不透明富文本；test_run_ids 多值存 JSON 文本；
-- 挂载维度由 execution_id 决定，projectId/productId 由执行/测试单冗余。
CREATE TABLE test_report (
    id                BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    execution_id      BIGINT        NOT NULL,
    project_id        BIGINT        NOT NULL DEFAULT 0,
    product_id        BIGINT        NOT NULL DEFAULT 0,
    title             VARCHAR(255)  NOT NULL,
    test_run_ids      TEXT          NULL,
    begin_date        DATE          NOT NULL,
    end_date          DATE          NOT NULL,
    owner             VARCHAR(64)   NULL,
    content           TEXT          NULL,
    created_by        VARCHAR(64)   NULL,
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(64)   NULL,
    updated_at        TIMESTAMP     NULL,
    lock_version      INT           NOT NULL DEFAULT 0,
    deleted_at        TIMESTAMP     NULL
);
CREATE INDEX idx_report_execution ON test_report (execution_id);
CREATE INDEX idx_report_product ON test_report (product_id);
