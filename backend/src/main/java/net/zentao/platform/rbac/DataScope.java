package net.zentao.platform.rbac;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.row.Row;
import java.util.List;
import net.zentao.platform.session.SessionPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 数据权限（platform 卡 §7.2）：追加可见集 = 对象自身规则 ∪ 所属各角色 acl 并集。
 * 对象级规则在各域卡；本类提供统一判定点：超管全见；acl 为空不追加也不收窄。
 *
 * <p>**acl 是「追加集」，不是限制档位**（T57 实测成文）：角色的 acl 只能让人多看，不能让人少看——
 * 真限制档位（按组织/本人/自定义的可见范围）归 T37/T38。所以 acl 读失败时按空处理是 fail-closed 方向，
 * 缺陷在「沉默」而不在方向：坏数据会让权限悄悄变窄（或悄悄不生效），故 T57/BE-10 起一律 WARN 留痕。
 */
@Component
public class DataScope {

  private static final Logger log = LoggerFactory.getLogger(DataScope.class);

  private final PrivilegeChecker checker;

  public DataScope(PrivilegeChecker checker) {
    this.checker = checker;
  }

  public boolean isSuperAdmin(SessionPrincipal principal) {
    return checker.isSuperAdmin(principal.accountId());
  }

  /** 所属各角色 acl 并集（products/programs/projects/executions 追加可见 id 集）。 */
  public Acl aclUnion(SessionPrincipal principal) {
    if (checker.roleIdsOf(principal.accountId()).isEmpty()) {
      return Acl.EMPTY;
    }
    List<Row> rows = Db.selectListByCondition("role", new QueryColumn("id")
        .in(checker.roleIdsOf(principal.accountId())));
    Acl union = Acl.EMPTY;
    for (Row row : rows) {
      String json = row.getString("acl");
      if (json == null || json.isBlank()) {
        continue;
      }
      union = union.merge(AclParser.parse(row.getLong("id"), json));
    }
    return union;
  }

  /** 角色 acl 白名单（platform 卡 §3.6/§7.2）。 */
  public record Acl(List<Long> products, List<Long> programs, List<Long> projects, List<Long> executions) {
    static final Acl EMPTY = new Acl(List.of(), List.of(), List.of(), List.of());

    Acl merge(Acl other) {
      return new Acl(union(products, other.products), union(programs, other.programs),
          union(projects, other.projects), union(executions, other.executions));
    }

    private static List<Long> union(List<Long> left, List<Long> right) {
      return List.copyOf(java.util.stream.Stream.concat(left.stream(), right.stream()).distinct().toList());
    }
  }

  /** acl JSON 解析（宽容：解析失败视为空 + WARN 留痕，见类注释）。 */
  static final class AclParser {
    private AclParser() {}

    static Acl parse(Object roleId, String json) {
      try {
        var root = tools.jackson.databind.json.JsonMapper.builder().build().readTree(json);
        return new Acl(idList(root, "products"), idList(root, "programs"), idList(root, "projects"),
            idList(root, "executions"));
      } catch (Exception e) {
        log.atWarn().setCause(e).log("role.acl 解析失败，按空 ACL 处理 roleId={}", roleId);
        return Acl.EMPTY;
      }
    }

    private static List<Long> idList(tools.jackson.databind.JsonNode root, String field) {
      var node = root.get(field);
      if (node == null || !node.isArray()) {
        return List.of();
      }
      List<Long> ids = new java.util.ArrayList<>();
      node.forEach(item -> ids.add(item.asLong()));
      return List.copyOf(ids);
    }
  }
}
