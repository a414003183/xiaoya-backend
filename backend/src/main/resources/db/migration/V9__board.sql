-- 阶段类型字典 + 通用看板四表（project 卡 §3.2–§3.6 字段级真源）。
-- 审计四件套 + deleted_at + lock_version（02 §5）；卡片归属单源 lane_id + sort（§2），白名单不入表（acl_entry 承载）。
CREATE TABLE stage (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    percent       DECIMAL(5,2) NOT NULL DEFAULT 0,
    type          VARCHAR(16)  NOT NULL DEFAULT 'other',
    project_model VARCHAR(16)  NOT NULL DEFAULT 'waterfall',
    sort          INT          NOT NULL DEFAULT 0,
    created_by    VARCHAR(64)  NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(64)  NULL,
    updated_at    TIMESTAMP    NULL,
    lock_version  INT          NOT NULL DEFAULT 0,
    deleted_at    TIMESTAMP    NULL
);
CREATE INDEX idx_stage_project_model ON stage (project_model);

CREATE TABLE board_space (
    id           BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name         VARCHAR(90) NOT NULL,
    type         VARCHAR(16) NOT NULL DEFAULT 'cooperation',
    owner        VARCHAR(64) NULL,
    team         TEXT        NULL,
    description  TEXT        NULL,
    acl          VARCHAR(16) NOT NULL DEFAULT 'open',
    status       VARCHAR(16) NOT NULL DEFAULT 'active',
    sort         INT         NOT NULL DEFAULT 0,
    created_by   VARCHAR(64) NULL,
    created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   VARCHAR(64) NULL,
    updated_at   TIMESTAMP   NULL,
    closed_by    VARCHAR(64) NULL,
    closed_at    TIMESTAMP   NULL,
    lock_version INT         NOT NULL DEFAULT 0,
    deleted_at   TIMESTAMP   NULL
);
CREATE INDEX idx_board_space_status ON board_space (status);

CREATE TABLE board (
    id           BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    space_id     BIGINT      NOT NULL,
    name         VARCHAR(90) NOT NULL,
    owner        VARCHAR(64) NULL,
    team         TEXT        NULL,
    description  TEXT        NULL,
    acl          VARCHAR(16) NOT NULL DEFAULT 'extend',
    status       VARCHAR(16) NOT NULL DEFAULT 'active',
    sort         INT         NOT NULL DEFAULT 0,
    created_by   VARCHAR(64) NULL,
    created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   VARCHAR(64) NULL,
    updated_at   TIMESTAMP   NULL,
    closed_by    VARCHAR(64) NULL,
    closed_at    TIMESTAMP   NULL,
    lock_version INT         NOT NULL DEFAULT 0,
    deleted_at   TIMESTAMP   NULL
);
CREATE INDEX idx_board_space_id ON board (space_id);

-- 看板列：LaneView 不暴露审计列，列按 02 §5 保留（值由库默认填充）。
CREATE TABLE board_lane (
    id           BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    board_id     BIGINT      NOT NULL,
    name         VARCHAR(90) NOT NULL,
    color        VARCHAR(32) NULL,
    wip_limit    INT         NOT NULL DEFAULT -1,
    archived     TINYINT     NOT NULL DEFAULT 0,
    sort         INT         NOT NULL DEFAULT 0,
    created_by   VARCHAR(64) NULL,
    created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   VARCHAR(64) NULL,
    updated_at   TIMESTAMP   NULL,
    lock_version INT         NOT NULL DEFAULT 0,
    deleted_at   TIMESTAMP   NULL
);
CREATE INDEX idx_board_lane_board ON board_lane (board_id);

CREATE TABLE board_card (
    id             BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    board_id       BIGINT        NOT NULL,
    lane_id        BIGINT        NOT NULL,
    name           VARCHAR(255)  NOT NULL,
    description    TEXT          NULL,
    status         VARCHAR(16)   NOT NULL DEFAULT 'doing',
    priority       INT           NOT NULL DEFAULT 3,
    assignee       VARCHAR(64)   NULL,
    begin_date     DATE          NULL,
    end_date       DATE          NULL,
    estimate_hours DECIMAL(10,2) NULL,
    progress       INT           NOT NULL DEFAULT 0,
    color          VARCHAR(32)   NULL,
    archived       TINYINT       NOT NULL DEFAULT 0,
    sort           INT           NOT NULL DEFAULT 0,
    created_by     VARCHAR(64)   NULL,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64)   NULL,
    updated_at     TIMESTAMP     NULL,
    lock_version   INT           NOT NULL DEFAULT 0,
    deleted_at     TIMESTAMP     NULL
);
CREATE INDEX idx_board_card_board ON board_card (board_id);
CREATE INDEX idx_board_card_lane ON board_card (lane_id);
