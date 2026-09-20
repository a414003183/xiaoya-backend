-- 个人列设置（platform 卡「列设置」：列表页工具栏齿轮的模态设置，服务端持久化）。
-- 个人级偏好：只存账号自己的行（account_id + resource 唯一），读写恒按会话账号过滤，无权限码。
-- columns 为 JSON 文本列（有序数组 [{"key","visible","fixed"}]，顺序即展示顺序；范式同 account_role.labels）。
-- UNIQUE(account_id, resource) 即幂等写路径的定位键：保存 = 有则整表覆盖、无则插入；重置 = 删行回默认。
CREATE TABLE user_column_pref (
    id           BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    account_id   BIGINT      NOT NULL,
    resource     VARCHAR(64) NOT NULL,
    columns      TEXT        NOT NULL,
    created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP   NULL,
    lock_version INT         NOT NULL DEFAULT 0,
    CONSTRAINT uq_user_column_pref UNIQUE (account_id, resource)
);
