-- 菜单管理（T19 P2-1）：DB 菜单行 = 内置菜单基线的**覆盖层**或**新增叶子项**。
-- 内置基线由 tools/route-codegen.mjs 从页面注解生成到 classpath 的 menu/navigation.json（与 routes.tsx 同源），
-- 故本表不存树结构：parent_key 指向基线容器（组 'admin' / 分区 'admin/system'），path 是身份（唯一，命中内置项即覆盖）。
-- 覆盖面：改名/排序/权限码/停用（不在侧栏出现）/把内置隐藏页重新暴露成菜单项。不能新建组或分区。
CREATE TABLE menu (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    parent_key VARCHAR(120) NOT NULL,
    title      VARCHAR(120) NOT NULL,
    path       VARCHAR(255) NOT NULL,
    order_no   INT          NOT NULL DEFAULT 999,
    perm       VARCHAR(64),
    status     VARCHAR(16)  NOT NULL DEFAULT 'active',
    CONSTRAINT uq_menu_path UNIQUE (path)
);
