-- project 域关联表（project 卡 §3.7/§3.8 字段级真源）：团队成员工时/天数表、干系人表。
-- 审计四件套 + deleted_at（02 §5）；两表均以 (object_type, object_id, account) 唯一——软删行仍占键，
-- 再次添加同 account 由仓储「复活」软删行完成（uk 冲突不可插入新行）。
CREATE TABLE team_member (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    object_type VARCHAR(16)  NOT NULL,
    object_id   BIGINT       NOT NULL,
    account     VARCHAR(64)  NOT NULL,
    role        VARCHAR(30)  NULL,
    join_date   DATE         NULL,
    days        INT          NOT NULL DEFAULT 0,
    hours       DECIMAL(4,1) NOT NULL DEFAULT 0,
    sort        INT          NOT NULL DEFAULT 0,
    created_by  VARCHAR(64)  NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  VARCHAR(64)  NULL,
    updated_at  TIMESTAMP    NULL,
    deleted_at  TIMESTAMP    NULL
);
CREATE UNIQUE INDEX uk_team_member ON team_member (object_type, object_id, account);

CREATE TABLE stakeholder (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    object_type VARCHAR(16) NOT NULL,
    object_id   BIGINT      NOT NULL,
    account     VARCHAR(64) NOT NULL,
    type        VARCHAR(16) NOT NULL DEFAULT 'inside',
    is_key      TINYINT     NOT NULL DEFAULT 0,
    source      VARCHAR(30) NULL,
    created_by  VARCHAR(64) NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  VARCHAR(64) NULL,
    updated_at  TIMESTAMP   NULL,
    deleted_at  TIMESTAMP   NULL
);
CREATE UNIQUE INDEX uk_stakeholder ON stakeholder (object_type, object_id, account);
