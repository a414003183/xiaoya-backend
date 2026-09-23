package net.zentao.org.infra;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.zentao.org.domain.AccountRepository;
import net.zentao.platform.activity.ObjectVisibilityRegistry;
import net.zentao.platform.audit.AuditCatalog;
import net.zentao.platform.audit.AuditCategory;
import net.zentao.platform.audit.AuditLevel;
import net.zentao.platform.audit.AuditSnapshotRegistry;
import net.zentao.platform.meta.DictProvider;
import net.zentao.platform.meta.DictRegistry;
import net.zentao.platform.meta.MetaRegistry;
import net.zentao.platform.meta.MetaView;
import net.zentao.platform.rbac.PrivilegeCatalog;
import net.zentao.platform.workflow.WorkflowRegistry;
import org.springframework.context.annotation.Configuration;

/**
 * org → platform 反向注册（A3 不破坏：org 主动调用 platform 注册口）。
 * 字典：departments/accounts；meta：department/account；权限码：org 域全集（org 卡 §5）；
 * 对象可见性：account（头像等 account 绑定附件，platform 卡 §7.2）。
 */
@Configuration
public class OrgRegistrar {

  public OrgRegistrar(MetaRegistry metaRegistry, DictRegistry dictRegistry, PrivilegeCatalog privilegeCatalog,
      WorkflowRegistry workflowRegistry, AccountRepository accountRepository,
      net.zentao.org.domain.DepartmentRepository departmentRepository,
      net.zentao.org.domain.RoleRepository roleRepository, ObjectVisibilityRegistry visibilityRegistry,
      AuditCatalog auditCatalog, AuditSnapshotRegistry auditSnapshots) {
    // ── 权限码（org 卡 §5 全集）──
    privilegeCatalog.register("department", List.of(
        "department-view", "department-create", "department-edit", "department-delete"));
    privilegeCatalog.register("account", List.of(
        "account-view", "account-create", "account-edit", "account-password", "account-reset-password",
        "account-disable", "account-enable", "account-unlock", "account-delete"));
    privilegeCatalog.register("personnel", List.of("personnel-view"));
    // 角色（T23 统一实体：权限码 + 成员 + 数据权限一套；读写与矩阵/成员分开授权）
    privilegeCatalog.register("role", List.of(
        "role-view", "role-create", "role-edit", "role-delete", "role-copy", "role-priv-edit",
        "role-member-edit"));

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
            // 角色筛选（列表）与角色选择（表单）共用角色字典：meta 只声明来源 source=roles（字典名），
            // 不内置清单——角色是数据、会随管理端增删而变，且 meta 在上下文启动期装配（此时迁移可能尚未建表）。
            new MetaView.MetaField("roleId", "select", null, null, "account.field.role", "roles", null, null),
            new MetaView.MetaField("departmentId", "select", null, null, "account.field.department", "departments", null, null),
            // 列表筛选值域（org 卡 §3.1 filterable：status/gender）——前端下拉只认这里的选项。
            new MetaView.MetaField("status", "select", null, null, "org.account.field.status", null, null, List.of(
                Map.of("value", "active", "i18n", "org.account.status.active"),
                Map.of("value", "disabled", "i18n", "org.account.status.disabled"))),
            new MetaView.MetaField("gender", "select", null, null, "org.account.field.gender", null, null, List.of(
                Map.of("value", "m", "i18n", "org.account.gender.m"),
                Map.of("value", "f", "i18n", "org.account.gender.f"))),
            new MetaView.MetaField("email", "text", null, 90, "account.field.email", null, null, null),
            new MetaView.MetaField("roleIds", "multiselect", null, null, "account.field.roles", "roles", true, null)),
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

    // ── 字典：roles（{value: 角色 id, label: 角色名, code}；账号表单与筛选的选项唯一来源）──
    // 条目形与 DB 字典一致（value/label 字面文案）：角色名是管理员维护的数据，不是 i18n 键。
    dictRegistry.register(new DictProvider() {
      @Override
      public String name() {
        return "roles";
      }

      @Override
      public List<Map<String, Object>> items() {
        return roleRepository.findAll().stream()
            .map(role -> {
              Map<String, Object> item = new java.util.LinkedHashMap<>();
              item.put("value", role.id());
              item.put("label", role.name());
              if (role.code() != null) {
                item.put("code", role.code());
              }
              item.put("sort", role.sort());
              item.put("builtin", role.builtin());
              return item;
            })
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

    // 对象可见性（T49 / platform 卡 §7.2）：账号无行级 ACL，登录即可见（头像等 account 绑定附件）
    visibilityRegistry.register("account", (principal, accountId) -> true);

    // ── 审计分级（T10 / VISION 事项 4 第 2 行「权限/角色/账号变更 → 字段级 diff」）──
    // 分类 + 关键字段（@AuditDiff 留空时回落这里）；create 类不登记 diff 字段（没有旧值可比）
    auditCatalog.register("account-update", AuditCategory.PERM, AuditLevel.FULL,
        List.of("account", "realName", "nickname", "departmentId", "email", "mobile", "phone", "gender", "roleIds"),
        false);
    auditCatalog.register("account-disable", AuditCategory.PERM, AuditLevel.FULL, List.of("status"), false);
    auditCatalog.register("account-enable", AuditCategory.PERM, AuditLevel.FULL, List.of("status"), false);
    auditCatalog.register("account-unlock", AuditCategory.PERM, AuditLevel.FULL, List.of("status", "locked"), false);
    auditCatalog.register("account-delete", AuditCategory.PERM, AuditLevel.FULL,
        List.of("account", "realName", "status"), false);
    // 口令动作只记「谁改了谁的口令」：快照里根本没有 passwordHash（见下面的 provider），无 diff 可采
    auditCatalog.register("account-password", AuditCategory.PERM, AuditLevel.SUMMARY, List.of(), false);
    auditCatalog.register("account-reset-password", AuditCategory.PERM, AuditLevel.SUMMARY, List.of(), false);
    auditCatalog.register("role-update", AuditCategory.PERM, AuditLevel.FULL,
        List.of("name", "description", "acl", "sort"), false);
    auditCatalog.register("role-grant", AuditCategory.PERM, AuditLevel.FULL, List.of("privCodes"), false);
    auditCatalog.register("role-member-update", AuditCategory.PERM, AuditLevel.FULL, List.of("memberIds"), false);
    auditCatalog.register("role-copy", AuditCategory.PERM, AuditLevel.SUMMARY, List.of(), false);
    auditCatalog.register("department-create", AuditCategory.PERM, AuditLevel.SUMMARY, List.of(), false);
    auditCatalog.register("department-update", AuditCategory.PERM, AuditLevel.FULL,
        List.of("name", "parentId", "sort", "manager"), false);
    auditCatalog.register("department-tree-update", AuditCategory.PERM, AuditLevel.SUMMARY, List.of(), false);
    auditCatalog.register("department-delete", AuditCategory.PERM, AuditLevel.FULL, List.of("name", "parentId"), false);

    // 快照 provider：**每次返回新 Map**（框架留着 before 再取 after 比对，同一个可变 Map 会让 diff 恒为空）；
    // 字段用 LinkedHashMap 装（Map.of 不收 null，而 parentId/manager 这类字段可以为空）
    auditSnapshots.register("account", accountId -> accountRepository.findActiveById(accountId)
        .map(account -> {
          Map<String, Object> snapshot = new LinkedHashMap<>();
          // 口令散列永不进快照：审计表只追加且留 180 天，敏感字段「干脆不取」比「取了再掩码」更稳
          snapshot.put("account", account.account());
          snapshot.put("realName", account.realName());
          snapshot.put("nickname", account.nickname());
          snapshot.put("departmentId", account.departmentId());
          snapshot.put("email", account.email());
          snapshot.put("mobile", account.mobile());
          snapshot.put("phone", account.phone());
          snapshot.put("gender", account.gender());
          snapshot.put("status", account.status());
          snapshot.put("locked", account.lockedAt() != null);
          snapshot.put("roleIds", accountRepository.roleIdsOf(accountId));
          return snapshot;
        })
        .orElse(null));
    auditSnapshots.register("role", roleId -> roleRepository.findById(roleId)
        .map(role -> {
          Map<String, Object> snapshot = new LinkedHashMap<>();
          snapshot.put("code", role.code());
          snapshot.put("name", role.name());
          snapshot.put("description", role.description());
          snapshot.put("acl", role.acl());
          snapshot.put("sort", role.sort());
          snapshot.put("privCodes", roleRepository.privCodesOf(roleId));
          snapshot.put("memberIds", roleRepository.memberIdsOf(roleId));
          return snapshot;
        })
        .orElse(null));
    auditSnapshots.register("department", departmentId -> departmentRepository.findById(departmentId)
        .map(department -> {
          Map<String, Object> snapshot = new LinkedHashMap<>();
          snapshot.put("name", department.name());
          snapshot.put("parentId", department.parentId());
          snapshot.put("sort", department.sort());
          snapshot.put("manager", department.manager());
          return snapshot;
        })
        .orElse(null));
  }
}
