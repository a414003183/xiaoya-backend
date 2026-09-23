package net.zentao.platform.rbac;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.row.Db;
import java.util.List;
import net.zentao.platform.session.SessionPrincipal;
import org.springframework.stereotype.Component;

/**
 * 功能权限判定链（platform 卡 §7.1）：未登录 40101（由调用方先 resolve）→
 * 超管（id=1 内置角色成员，T23 起角色统一）全过 → 所属各角色权限码并集含码。每请求实时查，成员变更即时生效。
 */
@Component
public class PrivilegeChecker {

  public static final long SUPER_ROLE_ID = 1L;

  private static final QueryColumn USER_ROLE_ACCOUNT = new QueryColumn("account_id");
  private static final QueryColumn USER_ROLE_ROLE = new QueryColumn("role_id");
  private static final QueryColumn ROLE_PRIV_ROLE = new QueryColumn("role_id");
  private static final QueryColumn ROLE_PRIV_CODE = new QueryColumn("priv_code");

  private final PrivilegeCatalog catalog;

  public PrivilegeChecker(PrivilegeCatalog catalog) {
    this.catalog = catalog;
  }

  public boolean hasPrivilege(SessionPrincipal principal, String code) {
    return isSuperAdmin(principal.accountId()) || privilegesOf(principal).contains(code);
  }

  /** 账号所属各角色权限码并集；超管返回编目全集（供 /me 下发显隐）。 */
  public List<String> privilegesOf(SessionPrincipal principal) {
    if (isSuperAdmin(principal.accountId())) {
      return catalog.allCodes();
    }
    return privilegesOfRoles(roleIdsOf(principal.accountId()));
  }

  public boolean isSuperAdmin(long accountId) {
    return roleIdsOf(accountId).contains(SUPER_ROLE_ID);
  }

  public List<Long> roleIdsOf(long accountId) {
    return Db.selectListByCondition("user_role", USER_ROLE_ACCOUNT.eq(accountId)).stream()
        .map(row -> row.getLong("role_id"))
        .toList();
  }

  private List<String> privilegesOfRoles(List<Long> roleIds) {
    if (roleIds.isEmpty()) {
      return List.of();
    }
    return Db.selectListByCondition("role_priv", ROLE_PRIV_ROLE.in(roleIds)).stream()
        .map(row -> row.getString("priv_code"))
        .filter(code -> code != null && !code.isBlank())
        .toList();
  }
}
