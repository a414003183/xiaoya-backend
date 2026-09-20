package net.zentao.platform.rbac;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.row.Row;
import java.util.List;
import net.zentao.platform.session.SessionPrincipal;
import org.springframework.stereotype.Component;

/**
 * 数据权限（platform 卡 §7.2）：追加可见集 = 对象自身规则 ∪ 所属各组 acl 并集。
 * 对象级规则在各域卡；本类提供统一判定点：超管全见；acl 为空不追加也不收窄。
 */
@Component
public class DataScope {

  private final PrivilegeChecker checker;

  public DataScope(PrivilegeChecker checker) {
    this.checker = checker;
  }

  public boolean isSuperAdmin(SessionPrincipal principal) {
    return checker.isSuperAdmin(principal.accountId());
  }

  /** 所属各组 acl 并集（products/programs/projects/executions 追加可见 id 集）。 */
  public Acl aclUnion(SessionPrincipal principal) {
    if (checker.groupsOf(principal.accountId()).isEmpty()) {
      return Acl.EMPTY;
    }
    List<Row> rows = Db.selectListByCondition("auth_group", new QueryColumn("id")
        .in(checker.groupsOf(principal.accountId())));
    return rows.stream()
        .map(row -> row.getString("acl"))
        .filter(acl -> acl != null && !acl.isBlank())
        .map(AclParser::parse)
        .reduce(Acl.EMPTY, Acl::merge);
  }

  /** 组 acl 白名单（platform 卡 §3.6/§7.2）。 */
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

  /** acl JSON 解析（宽容：解析失败视为空）。 */
  static final class AclParser {
    private AclParser() {}

    static Acl parse(String json) {
      try {
        var root = tools.jackson.databind.json.JsonMapper.builder().build().readTree(json);
        return new Acl(idList(root, "products"), idList(root, "programs"), idList(root, "projects"),
            idList(root, "executions"));
      } catch (Exception e) {
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
