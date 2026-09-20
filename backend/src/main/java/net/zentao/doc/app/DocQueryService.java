package net.zentao.doc.app;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import net.zentao.doc.api.DocList;
import net.zentao.doc.api.DocView;
import net.zentao.doc.domain.Doc;
import net.zentao.doc.domain.DocRepository;
import net.zentao.doc.domain.DocSpace;
import net.zentao.doc.domain.DocVersion;
import net.zentao.doc.domain.DocVersionRepository;
import net.zentao.platform.filters.FieldRegistry;
import net.zentao.platform.filters.FilterPredicate;
import net.zentao.platform.filters.Filters;
import net.zentao.platform.search.SearchResultView;
import net.zentao.platform.session.SessionPrincipal;
import org.springframework.stereotype.Component;

/**
 * 文档查询（doc 卡 §3.2 DSL 白名单 + §7 DataScope：可见库 id 集 AND 文档 ACL 谓词）。
 * 正文关键词命中当前发布正文（doc_content 取 version = doc.version 的快照做 LIKE）。
 */
@Component
public class DocQueryService {

  private static final FieldRegistry GLOBAL_REGISTRY = FieldRegistry.allowing(
      Set.of("docSpaceId", "categoryId", "productId", "projectId", "executionId", "type", "status", "acl",
          "createdBy", "updatedBy", "id"),
      Set.of("id", "sort", "views", "title", "createdAt", "updatedAt"),
      Set.of("title", "keywords", "content"));

  private static final FieldRegistry SPACE_REGISTRY = FieldRegistry.allowing(
      Set.of("categoryId", "parentId", "status", "type", "acl", "createdBy", "id"),
      Set.of("id", "sort", "views", "title", "createdAt", "updatedAt"),
      Set.of("title", "keywords", "content"));

  private static final Map<String, String> COLUMNS = Map.ofEntries(
      Map.entry("docSpaceId", "doc_space_id"),
      Map.entry("categoryId", "category_id"),
      Map.entry("parentId", "parent_id"),
      Map.entry("productId", "product_id"),
      Map.entry("projectId", "project_id"),
      Map.entry("executionId", "execution_id"),
      Map.entry("type", "type"),
      Map.entry("status", "status"),
      Map.entry("acl", "acl"),
      Map.entry("createdBy", "created_by"),
      Map.entry("updatedBy", "updated_by"),
      Map.entry("id", "id"),
      Map.entry("title", "title"),
      Map.entry("sort", "sort"),
      Map.entry("views", "views"),
      Map.entry("createdAt", "created_at"),
      Map.entry("updatedAt", "updated_at"));

  /** q 命中 title/keywords/当前发布正文（doc 卡 §3.2 searchable）。 */
  private static final String KEYWORD_SQL =
      "(title LIKE ? OR keywords LIKE ? OR EXISTS (SELECT 1 FROM doc_content c"
          + " WHERE c.doc_id = doc.id AND c.version = doc.version AND c.content LIKE ?))";

  private final DocRepository repository;
  private final DocVersionRepository versionRepository;
  private final DocAccess access;

  public DocQueryService(DocRepository repository, DocVersionRepository versionRepository, DocAccess access) {
    this.repository = repository;
    this.versionRepository = versionRepository;
    this.access = access;
  }

  /** 跨库文档列表（doc 卡 §5 GET /docs；DataScope 注入可见库 + 文档 ACL）。 */
  public DocList page(SessionPrincipal principal, Map<String, String[]> params) {
    return query(principal, params, GLOBAL_REGISTRY, null, null);
  }

  /** 库内文档列表（doc 卡 §5 GET /doc-spaces/{docSpaceId}/docs；库不可见 → 40302）。 */
  public DocList pageInSpace(long docSpaceId, SessionPrincipal principal, Map<String, String[]> params) {
    DocSpace space = access.requireSpace(principal, docSpaceId);
    return query(principal, params, SPACE_REGISTRY,
        new QueryColumn("doc_space_id").eq(docSpaceId).and(new QueryColumn("deleted_at").isNull()),
        space.docSort());
  }

  /**
   * 文档详情（doc 卡 §5 GET /docs/{docId}）：published 时 views+1（draft 不计）；
   * 正文按可编辑性取 v0 工作副本或最新发布快照（doc 卡 §4 末条）。
   */
  public DocView detail(SessionPrincipal principal, long docId) {
    Doc doc = access.requireReadableDoc(principal, docId);
    if ("published".equals(doc.status())) {
      // 阅读计数：pub 详情 views+1；走免乐观锁 SQL 路径，不影响其他字段的 lockVersion
      doc.countView();
      repository.updateViews(doc.id(), doc.views());
    }
    return viewOf(doc, principal);
  }

  /** 写路径响应视图（create/publish/save-draft/move/update 共用）：不发阅读计数，正文按可编辑性取。 */
  public DocView viewOf(Doc doc, SessionPrincipal principal) {
    boolean editable = access.canEdit(doc, principal);
    DocVersion working = versionRepository.findByDocAndVersion(doc.id(), DocVersion.DRAFT_VERSION).orElse(null);
    DocVersion snapshot = doc.version() > 0
        ? versionRepository.findByDocAndVersion(doc.id(), doc.version()).orElse(null)
        : null;
    DocVersion visible = editable ? working : snapshot;
    return DocView.detail(doc, hasDraft(doc, working, snapshot),
        visible == null ? null : visible.content(),
        visible == null ? null : visible.files(),
        visible == null ? null : visible.digest());
  }

  /** 全局搜索（platform 卡 §5.2）：可见库 + 文档 ACL 先于 LIKE，命中 title/keywords/当前发布正文。 */
  public List<SearchResultView> search(String q, int limit, SessionPrincipal principal) {
    String like = "%" + q + "%";
    QueryCondition injected = scoped(principal, null).and(KEYWORD_SQL, like, like, like);
    QueryWrapper query = QueryWrapper.create().where(injected)
        .orderBy(new QueryColumn("updated_at").desc(), new QueryColumn("id").desc()) // banned-words-ok：MyBatis-Flex 构造器方法名
        .limit(limit);
    return repository.queryPage(query, 0, limit).stream()
        .map(doc -> new SearchResultView("doc", doc.id(), doc.title(), doc.keywords(),
            doc.updatedAt() == null ? doc.createdAt() : doc.updatedAt()))
        .toList();
  }

  private DocList query(SessionPrincipal principal, Map<String, String[]> params, FieldRegistry registry,
      QueryCondition extra, String defaultSort) {
    Filters filters = Filters.parse(params, registry);
    QueryCondition injected = scoped(principal, extra);
    if (filters.q() != null) {
      String like = "%" + filters.q() + "%";
      injected = injected.and(KEYWORD_SQL, like, like, like);
    }
    QueryWrapper query = FilterPredicate.compile(filters, COLUMNS::get, specialOf(principal), injected);
    if (filters.sortKeys().isEmpty() && defaultSort != null) {
      // 库内列表缺省排序取库 docSort（doc 卡 §3.1/§5）
      query = "id_desc".equals(defaultSort)
          ? query.orderBy(new QueryColumn("id").desc()) // banned-words-ok：MyBatis-Flex 构造器方法名
          : query.orderBy(new QueryColumn("id").asc()); // banned-words-ok：MyBatis-Flex 构造器方法名
    }
    List<Doc> docs = repository.queryPage(query, filters.offset(), filters.limit());
    List<DocView> items = summaryViews(docs);
    Filters countFilters = new Filters(filters.clauses(), List.of(), 1, 1, filters.q());
    QueryWrapper countQuery = FilterPredicate.compile(countFilters, COLUMNS::get, specialOf(principal), injected);
    return new DocList(items, repository.countByQuery(countQuery));
  }

  /** 列表视图：一次批量取 v0 与最新快照算 hasDraft（避免逐行 N+1）。 */
  private List<DocView> summaryViews(List<Doc> docs) {
    if (docs.isEmpty()) {
      return List.of();
    }
    List<Long> ids = docs.stream().map(Doc::id).toList();
    Map<Long, DocVersion> drafts = new HashMap<>();
    versionRepository.findDrafts(ids).forEach(version -> drafts.put(version.docId(), version));
    Map<Long, Integer> versionByDoc = new HashMap<>();
    docs.forEach(doc -> versionByDoc.put(doc.id(), doc.version()));
    Map<Long, DocVersion> snapshots = new HashMap<>();
    for (DocVersion version : versionRepository.findSnapshotsOf(ids)) {
      if (versionByDoc.getOrDefault(version.docId(), -1) == version.version()) {
        snapshots.put(version.docId(), version);
      }
    }
    return docs.stream()
        .map(doc -> DocView.summary(doc, hasDraft(doc, drafts.get(doc.id()), snapshots.get(doc.id()))))
        .toList();
  }

  /** DataScope：可见库 id 集 AND 文档 ACL 谓词（超管免 ACL 谓词，mine 库仍被 id 集收窄）。 */
  private QueryCondition scoped(SessionPrincipal principal, QueryCondition extra) {
    List<Long> visible = access.visibleSpaceIds(principal);
    QueryCondition condition = new QueryColumn("deleted_at").isNull()
        .and(new QueryColumn("doc_space_id").in(visible.isEmpty() ? List.of(-1L) : visible));
    if (extra != null) {
      condition = condition.and(extra);
    }
    QueryCondition acl = access.aclCondition(principal);
    return acl == null ? condition : condition.and(acl);
  }

  /** hasDraft 派生：v0 工作副本与最新快照有差异（从未发布 → false）。 */
  private static boolean hasDraft(Doc doc, DocVersion draft, DocVersion snapshot) {
    return doc.version() > 0 && draft != null && snapshot != null && !draft.sameContentAs(snapshot);
  }

  /** 特殊量：@me = 当前账号（@null/@notNull 已在解析层处理）。 */
  private static Function<String, Optional<String>> specialOf(SessionPrincipal principal) {
    return value -> "@me".equals(value) ? Optional.of(principal.account()) : Optional.empty();
  }
}
