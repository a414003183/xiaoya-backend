package net.zentao.doc.app;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.zentao.doc.api.DocSpaceList;
import net.zentao.doc.api.DocSpaceView;
import net.zentao.doc.domain.DocSpace;
import net.zentao.doc.domain.DocSpaceRepository;
import net.zentao.platform.filters.FieldRegistry;
import net.zentao.platform.filters.FilterPredicate;
import net.zentao.platform.filters.Filters;
import net.zentao.platform.filters.LikePatterns;
import net.zentao.platform.session.SessionPrincipal;
import org.springframework.stereotype.Component;

/**
 * 文档库查询（doc 卡 §3.1 DSL 白名单 + §7 DataScope 注入 `id IN 可见库`，A6）。
 * 可见库含 mine 库（超管也不可见），故超管同样走 id 集注入而非豁免。
 */
@Component
public class DocSpaceQueryService {

  private static final FieldRegistry REGISTRY = FieldRegistry.allowing(
      Set.of("type", "acl", "productId", "projectId", "executionId", "createdBy", "id"),
      Set.of("id", "name", "sort", "createdAt"),
      Set.of("name", "description"));

  private static final Map<String, String> COLUMNS = Map.ofEntries(
      Map.entry("type", "type"),
      Map.entry("acl", "acl"),
      Map.entry("productId", "product_id"),
      Map.entry("projectId", "project_id"),
      Map.entry("executionId", "execution_id"),
      Map.entry("createdBy", "created_by"),
      Map.entry("id", "id"),
      Map.entry("name", "name"),
      Map.entry("sort", "sort"),
      Map.entry("createdAt", "created_at"));

  private final DocSpaceRepository repository;
  private final DocAccess access;

  public DocSpaceQueryService(DocSpaceRepository repository, DocAccess access) {
    this.repository = repository;
    this.access = access;
  }

  /** 库列表（doc 卡 §5 GET /doc-spaces；仅可见库）。 */
  public DocSpaceList page(SessionPrincipal principal, Map<String, String[]> params) {
    Filters filters = Filters.parse(params, REGISTRY);
    List<Long> visible = access.visibleSpaceIds(principal);
    QueryCondition injected = new QueryColumn("deleted_at").isNull()
        .and(new QueryColumn("id").in(visible.isEmpty() ? List.of(-1L) : visible));
    QueryCondition keyword = keywordCondition(filters.q());
    if (keyword != null) {
      injected = injected.and(keyword);
    }
    QueryWrapper query = FilterPredicate.compile(filters, COLUMNS::get, value -> java.util.Optional.empty(), injected);
    List<DocSpace> spaces = repository.queryPage(query, filters.offset(), filters.limit());
    QueryWrapper countQuery = FilterPredicate.compile(filters.forCount(), COLUMNS::get,
        value -> java.util.Optional.empty(), injected);
    return new DocSpaceList(withCounts(spaces), repository.countByQuery(countQuery));
  }

  /** 库视图（派生 docCount 现算）。 */
  public DocSpaceView viewOf(DocSpace space) {
    return DocSpaceView.of(space, repository.countLiveDocsBySpaces(List.of(space.id()))
        .getOrDefault(space.id(), 0L));
  }

  private List<DocSpaceView> withCounts(List<DocSpace> spaces) {
    if (spaces.isEmpty()) {
      return List.of();
    }
    Map<Long, Long> counts = repository.countLiveDocsBySpaces(spaces.stream().map(DocSpace::id).toList());
    return spaces.stream()
        .map(space -> DocSpaceView.of(space, counts.getOrDefault(space.id(), 0L)))
        .toList();
  }

  private QueryCondition keywordCondition(String q) {
    if (q == null || q.isBlank()) {
      return null;
    }
    String like = LikePatterns.contains(q);
    return new QueryColumn("name").likeRaw(like).or(new QueryColumn("description").likeRaw(like));
  }
}
