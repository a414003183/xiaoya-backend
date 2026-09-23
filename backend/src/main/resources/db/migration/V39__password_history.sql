-- 口令历史（T62 SEC-11）：只追加的「被替换掉的口令哈希」——改密/重置成功时把换掉的那条追加进来，
-- 按账号保留最新 N 条（N = zentao.security.password.history-count，默认 5；≤0 = 关闭历史校验）。
-- 判据单源在 org/app/PasswordActionHandler：历史里**不含当前口令**——「新 = 当前」由独立比对兜住（不受开关影响），
-- 历史只负责「不得复用最近 N 个用过的」。
-- 只追加表：无 updated_*/deleted_at/lock_version（无 UPDATE、无软删路径，形态同 user_column_pref 的免软删口径）。
-- (account_id, id) 复合索引：校验与修剪都只走「按账号取最新 N 条」这一条路径。
CREATE TABLE password_history (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT       NOT NULL,
    password   VARCHAR(100) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64)  NULL
);

CREATE INDEX idx_password_history_account ON password_history (account_id, id);
