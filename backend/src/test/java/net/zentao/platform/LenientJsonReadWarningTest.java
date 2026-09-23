package net.zentao.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import net.zentao.ApiTestSupport;
import net.zentao.org.domain.Role;
import net.zentao.org.domain.RoleRepository;
import net.zentao.org.infra.RoleRepositoryImpl;
import net.zentao.platform.columnpref.ColumnPrefRepository;
import net.zentao.platform.columnpref.ColumnPrefRepositoryImpl;
import net.zentao.platform.meta.FieldDefRegistry;
import net.zentao.platform.rbac.DataScope;
import net.zentao.platform.session.SessionPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * T57 / BE-10：4 处宽容 JSON 解析（坏数据 → 空/null）**不许再静默**——行为不变，但要留一条带对象标识的 WARN。
 *
 * <p>为什么值得测：acl 是「追加可见集」，读失败只会让人**少看**（fail-closed），所以没有安全口子；
 * 真正的代价是沉默——坏数据让权限悄悄变窄、列设置悄悄丢失、字段定义悄悄消失，运维与排障无从下手。
 * 用例同时钉住「行为没变」（仍返回空值/未设置），避免把「留痕」顺手做成「抛异常」。
 */
class LenientJsonReadWarningTest extends ApiTestSupport {

  @Autowired RoleRepository roles;

  @Autowired DataScope dataScope;

  @Autowired ColumnPrefRepository columnPrefs;

  @Autowired FieldDefRegistry fieldDefs;

  @Autowired JdbcTemplate jdbc;

  @Test
  @DisplayName("role.acl 坏 JSON：读角色与 DataScope 并集各留一条 WARN，值仍是空 ACL")
  void corruptRoleAclWarns() throws Exception {
    String admin = login("admin", "admin123");
    long roleId = dataId(send("POST", "/api/v1/roles", "{\"name\":\"坏ACL角色-" + System.nanoTime() + "\"}", admin));
    long accountId = dataId(send("POST", "/api/v1/accounts",
        "{\"account\":\"bad-acl-" + System.nanoTime() + "\",\"password\":\"secret123\",\"realName\":\"坏ACL成员\","
            + "\"roleIds\":[" + roleId + "]}",
        admin));
    String account = jdbc.queryForObject("SELECT account FROM account WHERE id = ?", String.class, accountId);
    jdbc.update("UPDATE role SET acl = ? WHERE id = ?", "{not-json", roleId);

    ListAppender<ILoggingEvent> roleLog = attach(RoleRepositoryImpl.class);
    Role role = roles.findById(roleId).orElseThrow();
    detach(RoleRepositoryImpl.class, roleLog);
    assertTrue(role.acl().products().isEmpty() && role.acl().executions().isEmpty(),
        "坏 ACL 仍读成空（行为不变，fail-closed 方向）：" + role.acl());    assertTrue(warned(roleLog, "role.acl 解析失败"), "读角色留痕：" + render(roleLog));

    ListAppender<ILoggingEvent> scopeLog = attach(DataScope.class);
    DataScope.Acl union = dataScope.aclUnion(new SessionPrincipal(accountId, account));
    detach(DataScope.class, scopeLog);
    assertTrue(union.products().isEmpty() && union.projects().isEmpty(), "并集仍是空（不炸读路径）");
    assertTrue(warned(scopeLog, "role.acl 解析失败"), "DataScope 留痕：" + render(scopeLog));
    assertTrue(render(scopeLog).contains(String.valueOf(roleId)), "WARN 必须带角色 id：" + render(scopeLog));
  }

  @Test
  @DisplayName("user_column_pref.columns 坏 JSON：留 WARN 且读成未设置")
  void corruptColumnPrefWarns() throws Exception {
    String admin = login("admin", "admin123");
    long accountId = dataId(send("POST", "/api/v1/accounts",
        "{\"account\":\"bad-pref-" + System.nanoTime() + "\",\"password\":\"secret123\",\"realName\":\"坏列设置\"}",
        admin));
    String resource = "quality-bugs";
    jdbc.update("INSERT INTO user_column_pref (account_id, resource, columns) VALUES (?,?,?)",
        accountId, resource, "{not-json");

    ListAppender<ILoggingEvent> appender = attach(ColumnPrefRepositoryImpl.class);
    assertTrue(columnPrefs.find(accountId, resource).isEmpty(), "坏数据视为未设置（行为不变）");
    detach(ColumnPrefRepositoryImpl.class, appender);
    assertTrue(warned(appender, "column_pref.columns 解析失败"), render(appender));
  }

  @Test
  @DisplayName("field_def.options 坏 JSON：留 WARN 且按空处理")
  void corruptFieldDefWarns() {
    String domain = "t57domain" + System.nanoTime() % 100000;
    jdbc.update("INSERT INTO field_def (domain, item_key, type, options) VALUES (?,?,?,?)",
        domain, "t57key", "select", "{not-json");

    ListAppender<ILoggingEvent> appender = attach(FieldDefRegistry.class);
    fieldDefs.reload();
    List<net.zentao.platform.meta.FieldDef> defs = fieldDefs.byDomain(domain);
    detach(FieldDefRegistry.class, appender);

    assertEquals(1, defs.size(), "坏 options 不影响该行仍被登记");
    assertTrue(defs.getFirst().options().isEmpty(), "坏 options 按空处理（行为不变）");
    assertTrue(warned(appender, "field_def.options 解析失败"), render(appender));
  }

  private static ListAppender<ILoggingEvent> attach(Class<?> type) {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger(type).addAppender(appender);
    return appender;
  }

  private static void detach(Class<?> type, ListAppender<ILoggingEvent> appender) {
    logger(type).detachAppender(appender);
  }

  private static Logger logger(Class<?> type) {
    return (Logger) LoggerFactory.getLogger(type);
  }

  private static boolean warned(ListAppender<ILoggingEvent> appender, String fragment) {
    return appender.list.stream().anyMatch(event -> event.getLevel() == Level.WARN
        && event.getFormattedMessage().contains(fragment));
  }

  private static String render(ListAppender<ILoggingEvent> appender) {
    return appender.list.stream().map(event -> event.getLevel() + " " + event.getFormattedMessage()).toList()
        .toString();
  }
}
