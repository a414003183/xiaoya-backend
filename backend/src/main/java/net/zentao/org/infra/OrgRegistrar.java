package net.zentao.org.infra;

import java.util.List;
import java.util.Map;
import net.zentao.org.domain.AccountRepository;
import net.zentao.platform.meta.DictProvider;
import net.zentao.platform.meta.DictRegistry;
import net.zentao.platform.meta.MetaRegistry;
import net.zentao.platform.meta.MetaView;
import net.zentao.platform.rbac.PrivilegeCatalog;
import net.zentao.platform.workflow.WorkflowRegistry;
import org.springframework.context.annotation.Configuration;

/**
 * org → platform 反向注册（A3 不破坏：org 主动调用 platform 注册口）。
 * 字典：departments/accounts；meta：department/account；权限码：org 域全集（org 卡 §5）。
 */
@Configuration
public class OrgRegistrar {

  public OrgRegistrar(MetaRegistry metaRegistry, DictRegistry dictRegistry, PrivilegeCatalog privilegeCatalog,
      WorkflowRegistry workflowRegistry, AccountRepository accountRepository,
      net.zentao.org.domain.DepartmentRepository departmentRepository,
      net.zentao.org.domain.AccountRoleRepository accountRoleRepository) {
    // ── 权限码（org 卡 §5 全集）──
    privilegeCatalog.register("department", List.of(
        "department-view", "department-create", "department-edit", "department-delete"));
    privilegeCatalog.register("account", List.of(
        "account-view", "account-create", "account-edit", "account-password", "account-reset-password",
        "account-disable", "account-enable", "account-unlock", "account-delete"));
    privilegeCatalog.register("group", List.of(
        "group-view", "group-create", "group-edit", "group-copy", "group-delete",
        "group-priv-edit", "group-member-edit"));
    privilegeCatalog.register("personnel", List.of("personnel-view"));
    // 角色字典（org 卡 §3.4：旧禅道「后台→自定义→用户→角色列表」；读/写分离，与 lang-manage 同粒度）
    privilegeCatalog.register("role", List.of("role-view", "role-manage"));

    // ── meta：department ──
    metaRegistry.register("department", new MetaView(
        "department",
        List.of(
            new MetaView.MetaField("name", "text", true, 60, "department.field.name", null, null, null),
            new MetaView.MetaField("parentId", "select", null, null, "department.field.parent", "departments", null, null),
            new MetaView.MetaField("sort", "number", null, null, "department.field.sort", null, null, null),
            new MetaView.MetaField("manager", "account", null, null, "department.field.manager", "accounts", null, null)),
        new MetaView.MetaList(List.of("id", "name", "sort"), "sort"),
        workflowRegistry.actionsOf("department"),
        Map.of()));

    // ── meta：account（actions[].allowedStatus 由 workflow/account.yml 导出，03 §6 同源）──
    metaRegistry.register("account", new MetaView(
        "account",
        List.of(
            new MetaView.MetaField("account", "text", true, 30, "account.field.account", null, null, null),
            new MetaView.MetaField("password", "text", true, 64, "account.field.password", null, null, null),
            new MetaView.MetaField("realName", "text", true, 100, "account.field.realName", null, null, null),
            new MetaView.MetaField("nickname", "text", null, 60, "account.field.nickname", null, null, null),
            // role 的选项来自角色字典（org 卡 §3.4）：meta 只声明来源 source=roles（字典名），
            // 不再内置清单——字典是数据、会随管理端增删而变，且 meta 在上下文启动期装配（此时迁移可能尚未建表）。
            new MetaView.MetaField("role", "select", null, null, "account.field.role", "roles", null, null),
            new MetaView.MetaField("departmentId", "select", null, null, "account.field.department", "departments", null, null),
            // 列表筛选值域（org 卡 §3.1 filterable：status/gender）——前端下拉只认这里的选项。
            new MetaView.MetaField("status", "select", null, null, "org.account.field.status", null, null, List.of(
                Map.of("value", "active", "i18n", "org.account.status.active"),
                Map.of("value", "disabled", "i18n", "org.account.status.disabled"))),
            new MetaView.MetaField("gender", "select", null, null, "org.account.field.gender", null, null, List.of(
                Map.of("value", "m", "i18n", "org.account.gender.m"),
                Map.of("value", "f", "i18n", "org.account.gender.f"))),
            new MetaView.MetaField("email", "text", null, 90, "account.field.email", null, null, null),
            new MetaView.MetaField("groupIds", "multiselect", null, null, "account.field.groups", "groups", true, null)),
        new MetaView.MetaList(List.of("id", "account", "realName", "status", "lastActiveAt"), "-id"),
        workflowRegistry.actionsOf("account"),
        Map.of(
            "active", new MetaView.MetaStatusVisual("active", "account.status.active"),
            "disabled", new MetaView.MetaStatusVisual("closed", "account.status.disabled"))));

    // ── 字典：departments（部门树 {id, name, parentId}）──
    dictRegistry.register(new DictProvider() {
      @Override
      public String name() {
        return "departments";
      }

      @Override
      public List<Map<String, Object>> items() {
        return departmentRepository.findAll().stream()
            .map(department -> Map.<String, Object>of(
                "id", department.id(),
                "name", department.name(),
                "parentId", department.parentId() == null ? 0 : department.parentId()))
            .toList();
      }
    });

    // ── 字典：roles（角色字典 {code, labels}；labels 是「语言码 → 角色名」映射，前端按当前语言取）──
    dictRegistry.register(new DictProvider() {
      @Override
      public String name() {
        return "roles";
      }

      @Override
      public List<Map<String, Object>> items() {
        return accountRoleRepository.findAll().stream()
            .map(role -> Map.<String, Object>of(
                "code", role.code(),
                "labels", role.labels(),
                "sort", role.sort(),
                "builtin", role.builtin()))
            .toList();
      }
    });

    // ── 字典：accounts（启用账号 {account, realName}；停用/软删不供给，org 卡 §7）──
    dictRegistry.register(new DictProvider() {
      @Override
      public String name() {
        return "accounts";
      }

      @Override
      public List<Map<String, Object>> items() {
        return accountRepository.findAllVisible().stream()
            .filter(account -> "active".equals(account.status()))
            .map(account -> Map.<String, Object>of(
                "account", account.account(),
                "realName", account.realName()))
            .toList();
      }
    });
  }
}
