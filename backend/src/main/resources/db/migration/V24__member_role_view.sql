-- 成员组补授 role-view：账号列表/成员目录要显示「角色」名，而角色名解析依赖字典端点
-- （GET /roles 需 role-view）。与 account-view/department-view 同理——基础组织信息，授予全部登录账号；
-- 写侧 role-manage 仍只给管理员。历史迁移 V3 已冻结，故以新迁移补授（口径见 org 卡 §7）。
INSERT INTO group_priv (group_id, priv_code) VALUES (2, 'role-view');
