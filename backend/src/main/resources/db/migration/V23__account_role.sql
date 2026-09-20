-- 账号角色字典（org 卡 §3.4「角色列表」，旧禅道 后台→自定义→用户→角色列表 roleList）。
-- 与 auth_group 的区别：auth_group 是「权限角色」（能做什么），本表是「岗位角色」（账号资料上的角色标签）。
-- labels 为 JSON 文本列（语言码 → 角色名，范式同 auth_group.acl）；code 与 account.role 同宽 16。
-- 内置角色迁移自旧禅道 $lang->user->roleList 九项（label 取自其 zh-cn/en 语言包），builtin=1 者不可删除（可改名）。
CREATE TABLE account_role (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(16)  NOT NULL,
    labels      TEXT         NOT NULL,
    sort        INT          NOT NULL DEFAULT 0,
    builtin     TINYINT      NOT NULL DEFAULT 0,
    created_by  VARCHAR(64)  NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  VARCHAR(64)  NULL,
    updated_at  TIMESTAMP    NULL,
    lock_version INT         NOT NULL DEFAULT 0,
    CONSTRAINT uq_account_role_code UNIQUE (code)
);

INSERT INTO account_role (code, labels, sort, builtin) VALUES
('dev',    '{"zh-CN":"研发","en":"Developer"}',          10, 1),
('qa',     '{"zh-CN":"测试","en":"Tester"}',             20, 1),
('pm',     '{"zh-CN":"项目经理","en":"Scrum Master"}',    30, 1),
('po',     '{"zh-CN":"产品经理","en":"Product Owner"}',   40, 1),
('td',     '{"zh-CN":"研发主管","en":"Technical Manager"}', 50, 1),
('pd',     '{"zh-CN":"产品主管","en":"Product Manager"}',  60, 1),
('qd',     '{"zh-CN":"测试主管","en":"QA Manager"}',       70, 1),
('top',    '{"zh-CN":"高层管理","en":"Senior Manager"}',   80, 1),
('others', '{"zh-CN":"其他","en":"Others"}',              90, 1);
