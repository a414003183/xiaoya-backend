package net.zentao.doc.app;

import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryColumn;
import java.util.List;
import net.zentao.doc.domain.Doc;
import net.zentao.doc.domain.DocAclPolicy;
import net.zentao.doc.domain.DocRepository;
import net.zentao.doc.domain.DocSpace;
import net.zentao.doc.domain.DocSpaceRepository;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
import net.zentao.platform.filters.LikePatterns;
import net.zentao.platform.rbac.PrivilegeChecker;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.product.api.ProductApi;
import net.zentao.project.api.ExecutionApi;
import net.zentao.project.api.ProjectApi;
import org.springframework.stereotype.Component;

/**
 * doc 双层数据权限取数/守卫（doc 卡 §7）：先库门禁，再文档 ACL；功能权限码与数据权限独立（40301 / 40302）。
 * 归属对象可见性经 product/project 域的 api 包跨域只读（A2），判定纯函数在 {@link DocAclPolicy}。
 *
 * <p>错误语义：库不可见 → 40302（库自身即被寻址资源）；库不可见或文档 ACL 不可见 → 40401（按不存在处理）；
 * 可见但不可写（readers）→ 40302。
 */
@Component
public class DocAccess {

  private final DocSpaceRepository spaceRepository;
  private final DocRepository docRepository;
  private final ProductApi productApi;
  private final ProjectApi projectApi;
  private final ExecutionApi executionApi;
  private final PrivilegeChecker checker;

  public DocAccess(DocSpaceRepository spaceRepository, DocRepository docRepository, ProductApi productApi,
      ProjectApi projectApi, ExecutionApi executionApi, PrivilegeChecker checker) {
    this.spaceRepository = spaceRepository;
    this.docRepository = docRepository;
    this.productApi = productApi;
    this.projectApi = projectApi;
    this.executionApi = executionApi;
    this.checker = checker;
  }

  public boolean isSuperAdmin(SessionPrincipal principal) {
    return checker.isSuperAdmin(principal.accountId());
  }

  public List<Long> roleIdsOf(SessionPrincipal principal) {
    return checker.roleIdsOf(principal.accountId());
  }

  /** 可见库 id 集（doc 卡 §7；mine 库超管也不可见，故超管同样需要逐库判定）。 */
  public List<Long> visibleSpaceIds(SessionPrincipal principal) {
    boolean superAdmin = isSuperAdmin(principal);
    List<Long> groups = roleIdsOf(principal);
    DocAclPolicy.ObjectVisibility products = products(principal);
    DocAclPolicy.ObjectVisibility projects = projects(principal);
    DocAclPolicy.ObjectVisibility executions = executions(principal);
    // ponytail: 全量小集合内存判定（同 product/story 口径）；升级路径 = 可见库闭包落表或 SQL 下推
    return spaceRepository.findAllActive().stream()
        .filter(space -> DocAclPolicy.isSpaceVisible(space, principal.account(), superAdmin, groups, products,
            projects, executions))
        .map(DocSpace::id)
        .toList();
  }

  public boolean canSee(DocSpace space, SessionPrincipal principal) {
    return DocAclPolicy.isSpaceVisible(space, principal.account(), isSuperAdmin(principal), roleIdsOf(principal),
        products(principal), projects(principal), executions(principal));
  }

  public boolean canEdit(Doc doc, SessionPrincipal principal) {
    return DocAclPolicy.isDocEditable(doc, principal.account(), isSuperAdmin(principal), roleIdsOf(principal));
  }

  /** 库详情/动作前置：不存在 → 40401；存在但不可见 → 40302。 */
  public DocSpace requireSpace(SessionPrincipal principal, long spaceId) {
    DocSpace space = spaceRepository.findActiveById(spaceId).orElseThrow(() -> ApiException.notFound("entity.docSpace"));
    if (!canSee(space, principal)) {
      throw ApiException.keyed(ErrorCode.DATA_FORBIDDEN, "docSpace.guard.forbidden");
    }
    return space;
  }

  /** 文档读前置：库不可见或文档 ACL 不可见 → 40401（均按不存在处理，防探测）。 */
  public Doc requireReadableDoc(SessionPrincipal principal, long docId) {
    Doc doc = docRepository.findActiveById(docId).orElseThrow(() -> ApiException.notFound("entity.doc"));
    if (!readable(doc, principal)) {
      throw ApiException.notFound("entity.doc");
    }
    return doc;
  }

  /** 文档是否可读（按 id、不抛）：platform 可见性注册表按 docId 判定用，语义同 {@link #requireReadableDoc}。 */
  public boolean canRead(SessionPrincipal principal, long docId) {
    return docRepository.findActiveById(docId).filter(doc -> readable(doc, principal)).isPresent();
  }

  /** 文档可读判定（纯布尔，不抛）：库不存在同样按不可读处理（与 requireReadableDoc 的 40401 同口径）。 */
  private boolean readable(Doc doc, SessionPrincipal principal) {
    return spaceRepository.findActiveById(doc.docSpaceId())
        .map(space -> canSee(space, principal)
            && DocAclPolicy.isDocReadable(doc, principal.account(), isSuperAdmin(principal), roleIdsOf(principal)))
        .orElse(false);
  }

  /** 文档写前置：私有文档 readers 命中者写 → 40302。 */
  public Doc requireEditableDoc(SessionPrincipal principal, long docId) {
    Doc doc = requireReadableDoc(principal, docId);
    if (!canEdit(doc, principal)) {
      throw ApiException.keyed(ErrorCode.DATA_FORBIDDEN, "doc.guard.editForbidden");
    }
    return doc;
  }

  /**
   * 文档 ACL 的 SQL 谓词（列表 DataScope）：{@code acl='open' OR created_by=@me OR editors/readers 命中 @me/@myGroups}。
   * 超管豁免（mine 库由库 id 集收窄，见 {@link #visibleSpaceIds}）→ 返回 null 表示不加条件。
   *
   * <p>白名单 JSON 落库为字符串数组（见 infra.DocAclJson），故按 `%"值"%` 精确匹配元素边界。
   * ponytail: 账号名含 `_` 时 LIKE 的 `_` 通配会带来列表级误命中（详情判定仍精确）；升级路径 = normalize 白名单表。
   */
  public QueryCondition aclCondition(SessionPrincipal principal) {
    if (isSuperAdmin(principal)) {
      return null;
    }
    QueryCondition condition = new QueryColumn("acl").eq("open")
        .or(new QueryColumn("created_by").eq(principal.account()));
    for (String value : hitValues(principal)) {
      String pattern = LikePatterns.jsonElement(value);
      condition = condition.or(new QueryColumn("editors").likeRaw(pattern))
          .or(new QueryColumn("readers").likeRaw(pattern));
    }
    return condition;
  }

  /** 命中值 = 我的账号 + 我所属的组 id（doc 卡 §7：组关系按判定时刻生效，不做快照）。 */
  private List<String> hitValues(SessionPrincipal principal) {
    List<String> values = new java.util.ArrayList<>();
    values.add(principal.account());
    roleIdsOf(principal).forEach(groupId -> values.add(String.valueOf(groupId)));
    return List.copyOf(values);
  }

  public DocAclPolicy.ObjectVisibility products(SessionPrincipal principal) {
    ProductApi.ProductScope scope = productApi.visibleScope(principal);
    return new DocAclPolicy.ObjectVisibility(scope.visibleToAll(), scope.productIds());
  }

  public DocAclPolicy.ObjectVisibility projects(SessionPrincipal principal) {
    ProjectApi.VisibleScope scope = projectApi.visibleScope(principal, "project");
    return new DocAclPolicy.ObjectVisibility(scope.visibleToAll(), scope.ids());
  }

  public DocAclPolicy.ObjectVisibility executions(SessionPrincipal principal) {
    ExecutionApi.ExecutionScope scope = executionApi.executionScope(principal);
    return new DocAclPolicy.ObjectVisibility(scope.visibleToAll(), scope.executionIds());
  }
}
