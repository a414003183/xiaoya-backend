-- org 域五表（org 卡 §2/§3、platform 卡 §3.6 字段级真源）。
-- 真实删除语义：department/auth_group 无 deleted_at（org 卡 §2）；软删仅 account（deleted_at）。
CREATE TABLE account (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    account       VARCHAR(64)  NOT NULL,
    password      VARCHAR(100) NOT NULL,
    real_name     VARCHAR(100) NOT NULL,
    nickname      VARCHAR(60)  NULL,
    role          VARCHAR(16)  NULL,
    department_id BIGINT       NULL,
    email         VARCHAR(90)  NULL,
    mobile        VARCHAR(20)  NULL,
    phone         VARCHAR(20)  NULL,
    gender        VARCHAR(2)   NOT NULL DEFAULT 'm',
    birthday      DATE         NULL,
    joined_at     DATE         NULL,
    avatar_file_id BIGINT      NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'active',
    fails         INT          NOT NULL DEFAULT 0,
    locked_at     TIMESTAMP    NULL,
    last_active_at TIMESTAMP   NULL,
    created_by    VARCHAR(64)  NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(64)  NULL,
    updated_at    TIMESTAMP    NULL,
    deleted_at    TIMESTAMP    NULL,
    lock_version  INT          NOT NULL DEFAULT 0,
    CONSTRAINT uq_account UNIQUE (account)
);

CREATE TABLE department (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(60)  NOT NULL,
    parent_id   BIGINT       NULL,
    path        VARCHAR(255) NOT NULL DEFAULT ',',
    grade       INT          NOT NULL DEFAULT 1,
    sort        INT          NOT NULL DEFAULT 0,
    manager     VARCHAR(64)  NULL,
    created_by  VARCHAR(64)  NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  VARCHAR(64)  NULL,
    updated_at  TIMESTAMP    NULL,
    lock_version INT         NOT NULL DEFAULT 0
);

CREATE TABLE auth_group (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id  BIGINT       NOT NULL DEFAULT 0,
    name        VARCHAR(60)  NOT NULL,
    role        VARCHAR(30)  NOT NULL DEFAULT '',
    description VARCHAR(255) NOT NULL DEFAULT '',
    acl         TEXT         NULL,
    created_by  VARCHAR(64)  NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  VARCHAR(64)  NULL,
    updated_at  TIMESTAMP    NULL,
    lock_version INT         NOT NULL DEFAULT 0,
    CONSTRAINT uq_auth_group_name UNIQUE (name)
);

CREATE TABLE group_priv (
    id        BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    group_id  BIGINT      NOT NULL,
    priv_code VARCHAR(64) NOT NULL,
    CONSTRAINT uq_group_priv UNIQUE (group_id, priv_code)
);

CREATE TABLE user_group (
    id         BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    group_id   BIGINT NOT NULL,
    CONSTRAINT uq_user_group UNIQUE (account_id, group_id)
);
