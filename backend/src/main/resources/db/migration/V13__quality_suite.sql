-- 套件/用例库两表（quality 卡 §2/§3.3）：suite 一表两义（type=library 且 product_id=0 即用例库）；
-- suite_case 关联表 UNIQUE(suite_id, case_id)。
CREATE TABLE suite (
    id                BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id        BIGINT        NOT NULL DEFAULT 0,
    name              VARCHAR(255)  NOT NULL,
    description       TEXT          NULL,
    type              VARCHAR(16)   NOT NULL DEFAULT 'public',
    sort              INT           NOT NULL DEFAULT 0,
    created_by        VARCHAR(64)   NULL,
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(64)   NULL,
    updated_at        TIMESTAMP     NULL,
    lock_version      INT           NOT NULL DEFAULT 0,
    deleted_at        TIMESTAMP     NULL
);
CREATE INDEX idx_suite_product ON suite (product_id);
CREATE INDEX idx_suite_type ON suite (type);

CREATE TABLE suite_case (
    id                BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    suite_id          BIGINT        NOT NULL,
    case_id           BIGINT        NOT NULL,
    CONSTRAINT uk_suite_case UNIQUE (suite_id, case_id)
);
CREATE INDEX idx_suite_case_suite ON suite_case (suite_id);
CREATE INDEX idx_suite_case_case ON suite_case (case_id);
