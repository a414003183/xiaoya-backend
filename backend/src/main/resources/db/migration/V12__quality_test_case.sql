-- 用例两表（quality 卡 §2/§3.2）：test_case + case_step（desc/expect → description/expects）；
-- 步骤随用例整体替换写；version 恒 1（旧用例版本化砍掉）；stage 多值存 JSON 文本；库用例 product_id=0。
CREATE TABLE test_case (
    id                BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id        BIGINT        NOT NULL DEFAULT 0,
    branch_id         BIGINT        NOT NULL DEFAULT 0,
    library_id        BIGINT        NOT NULL DEFAULT 0,
    category_id       BIGINT        NOT NULL DEFAULT 0,
    story_id          BIGINT        NULL,
    title             VARCHAR(255)  NOT NULL,
    precondition      TEXT          NULL,
    keywords          VARCHAR(255)  NULL,
    priority          INT           NOT NULL DEFAULT 3,
    type              VARCHAR(16)   NOT NULL DEFAULT 'feature',
    stage             TEXT          NULL,
    status            VARCHAR(16)   NOT NULL DEFAULT 'normal',
    from_bug_id       BIGINT        NULL,
    last_run_result   VARCHAR(8)    NULL,
    last_runner       VARCHAR(64)   NULL,
    last_run_at       TIMESTAMP     NULL,
    reviewers         TEXT          NULL,
    reviewed_at       TIMESTAMP     NULL,
    version           INT           NOT NULL DEFAULT 1,
    custom_fields     TEXT          NULL,
    created_by        VARCHAR(64)   NULL,
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(64)   NULL,
    updated_at        TIMESTAMP     NULL,
    lock_version      INT           NOT NULL DEFAULT 0,
    deleted_at        TIMESTAMP     NULL
);
CREATE INDEX idx_case_product ON test_case (product_id);
CREATE INDEX idx_case_library ON test_case (library_id);
CREATE INDEX idx_case_story ON test_case (story_id);

CREATE TABLE case_step (
    id                BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    case_id           BIGINT        NOT NULL,
    sort              INT           NOT NULL,
    description       TEXT          NOT NULL,
    expects           TEXT          NULL,
    CONSTRAINT uk_case_step UNIQUE (case_id, sort)
);
