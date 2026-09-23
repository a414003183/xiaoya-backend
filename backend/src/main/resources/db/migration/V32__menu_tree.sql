-- T21 菜单链路完善：节点分三类（目录/菜单/按钮）+ 图标 + 可改路由路径。
--
-- T19 用 path 兼作身份（覆盖同路径内置项），于是路径不可改、容器不可新建、菜单里也没有按钮级权限。
-- 本迁移把「身份」与「路由」拆开：
--   node_key   稳定身份。覆盖内置节点 = 该内置节点的自然键（组 key / 分区 key / 页面 path）；
--              新增节点 = 'db-<id>'（服务端插入后按主键生成）。父子关系一律按 key 认。
--   node_type  dir（目录，可一级可分区）/ menu（菜单，指向页面）/ button（按钮，只带权限码）。
--   icon       antd 图标名（目录与菜单可用；侧栏与表单共用一份图标名表）。
--   parent_key 上级节点 key；NULL = 一级模块（只有目录能当一级模块）。
--   path       退化为可编辑的路由字段：不再唯一（同一页面挂两个入口是合法用法），目录/按钮恒为 NULL。
--
-- 存量行只有「覆盖内置叶子项」与「挂在基线容器下的新增项」两种，故 node_key = path 直接回填。
-- 方言政策（T52）：本目录的 SQL 必须**同时**是 MySQL 8 与 H2(MODE=MySQL) 的合法语句——
-- 生产/迁移工具走 MySQL，全部测试走 H2，只有一边合法就是「测试绿、上线断」。
-- 改空可空性一律用 MySQL 的 MODIFY COLUMN：H2 的 `ALTER COLUMN … SET NOT NULL / SET NULL`
-- 在 MySQL 8 报 ERROR 1064（V32 曾在生产迁移链上断在这里）。看护门禁：check-migration-portability.mjs。
ALTER TABLE menu ADD COLUMN node_key VARCHAR(255) NULL;
ALTER TABLE menu ADD COLUMN node_type VARCHAR(16) NOT NULL DEFAULT 'menu';
ALTER TABLE menu ADD COLUMN icon VARCHAR(64) NULL;
UPDATE menu SET node_key = path;
ALTER TABLE menu MODIFY COLUMN node_key VARCHAR(255) NOT NULL;
ALTER TABLE menu ADD CONSTRAINT uq_menu_node_key UNIQUE (node_key);
-- 唯一约束在 MySQL 里就是同名唯一索引，`DROP CONSTRAINT` 要到 MySQL 8.0.19 才认；统一用 DROP INDEX
ALTER TABLE menu DROP INDEX uq_menu_path;
ALTER TABLE menu MODIFY COLUMN parent_key VARCHAR(120) NULL;
ALTER TABLE menu MODIFY COLUMN path VARCHAR(255) NULL;
