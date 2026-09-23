package net.zentao.platform.rbac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.zentao.platform.session.SessionPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** 权限判定链（platform 卡 §7.1/§8）：超管全过 / 并集放行 / 无码拒绝 / 组变更即时生效。 */
@SpringBootTest
class PrivilegeCheckerTest {

  @Autowired
  PrivilegeChecker checker;

  @Autowired
  PrivilegeCatalog catalog;

  @Autowired
  javax.sql.DataSource dataSource;

  @Test
  @DisplayName("超管（id=1 组成员）全过；/me privileges = 编目全集")
  void superAdminPassesAll() throws Exception {
    catalog.register("test", java.util.List.of("test-code"));
    long accountId = seedAccount();
    grant(accountId, 1);
    SessionPrincipal principal = new SessionPrincipal(accountId, "rbac-admin");
    assertTrue(checker.isSuperAdmin(accountId));
    assertTrue(checker.hasPrivilege(principal, "test-code"));
    assertTrue(checker.hasPrivilege(principal, "anything-unregistered"));
    assertTrue(checker.privilegesOf(principal).containsAll(catalog.allCodes()));
  }

  @Test
  @DisplayName("普通组并集放行；撤销后下次请求即失效")
  void unionGrantAndImmediateRevoke() throws Exception {
    catalog.register("test", java.util.List.of("story-create"));
    long accountId = seedAccount();
    long groupId = seedGroup();
    grant(accountId, groupId);
    grantCode(groupId, "story-create");
    SessionPrincipal principal = new SessionPrincipal(accountId, "rbac-user");
    assertTrue(checker.hasPrivilege(principal, "story-create"), "并集含码应放行");
    assertFalse(checker.hasPrivilege(principal, "setting-manage"), "无码应拒绝");
    assertEquals(java.util.List.of("story-create"), checker.privilegesOf(principal));

    revokeCode(groupId, "story-create");
    assertFalse(checker.hasPrivilege(principal, "story-create"), "撤销后即时失效（每请求实时查）");
  }

  @Test
  @DisplayName("无组账号权限为空集")
  void noGroupMeansNoPrivilege() throws Exception {
    long accountId = seedAccount();
    SessionPrincipal principal = new SessionPrincipal(accountId, "rbac-nobody");
    assertTrue(checker.privilegesOf(principal).isEmpty());
    assertFalse(checker.hasPrivilege(principal, "file-upload"));
  }

  private long seedAccount() throws Exception {
    try (var connection = dataSource.getConnection();
        var insert = connection.prepareStatement(
            "INSERT INTO account (account, password, real_name) VALUES (?, 'x', 'rbac测试')",
            java.sql.Statement.RETURN_GENERATED_KEYS)) {
      insert.setString(1, "rbac-" + java.util.UUID.randomUUID());
      insert.executeUpdate();
      var keys = insert.getGeneratedKeys();
      keys.next();
      return keys.getLong(1);
    }
  }

  private long seedGroup() throws Exception {
    try (var connection = dataSource.getConnection();
        var insert = connection.prepareStatement(
            "INSERT INTO role (name) VALUES (?)", java.sql.Statement.RETURN_GENERATED_KEYS)) {
      insert.setString(1, "rbac-group-" + java.util.UUID.randomUUID());
      insert.executeUpdate();
      var keys = insert.getGeneratedKeys();
      keys.next();
      return keys.getLong(1);
    }
  }

  private void grant(long accountId, long groupId) throws Exception {
    try (var connection = dataSource.getConnection();
        var insert = connection.prepareStatement("INSERT INTO user_role (account_id, role_id) VALUES (?, ?)")) {
      insert.setLong(1, accountId);
      insert.setLong(2, groupId);
      insert.executeUpdate();
    }
  }

  private void grantCode(long groupId, String code) throws Exception {
    try (var connection = dataSource.getConnection();
        var insert = connection.prepareStatement("INSERT INTO role_priv (role_id, priv_code) VALUES (?, ?)")) {
      insert.setLong(1, groupId);
      insert.setString(2, code);
      insert.executeUpdate();
    }
  }

  private void revokeCode(long groupId, String code) throws Exception {
    try (var connection = dataSource.getConnection();
        var delete = connection.prepareStatement("DELETE FROM role_priv WHERE role_id = ? AND priv_code = ?")) {
      delete.setLong(1, groupId);
      delete.setString(2, code);
      delete.executeUpdate();
    }
  }
}
