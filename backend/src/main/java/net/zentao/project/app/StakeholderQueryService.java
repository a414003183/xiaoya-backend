package net.zentao.project.app;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryWrapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.zentao.platform.filters.FieldRegistry;
import net.zentao.platform.filters.FilterPredicate;
import net.zentao.platform.filters.Filters;
import net.zentao.platform.filters.LikePatterns;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.project.domain.Stakeholder;
import net.zentao.project.domain.StakeholderRepository;
import org.springframework.stereotype.Component;

/** 干系人列表查询（project 卡 §3.8 DSL 白名单 + §5 stakeholders 族读侧，A6）。 */
@Component
public class StakeholderQueryService {

  private static final FieldRegistry REGISTRY = FieldRegistry.allowing(
      Set.of("objectType", "objectId", "account", "type", "isKey"),
      Set.of("id", "createdAt"),
      Set.of());

  private static final Map<String, String> COLUMNS = Map.ofEntries(
      Map.entry("objectType", "object_type"),
      Map.entry("objectId", "object_id"),
      Map.entry("account", "account"),
      Map.entry("type", "type"),
      Map.entry("isKey", "is_key"),
      Map.entry("id", "id"),
      Map.entry("createdAt", "created_at"));

  private final StakeholderRepository repository;
  private final ProjectQueryService projectQueryService;

  public StakeholderQueryService(StakeholderRepository repository, ProjectQueryService projectQueryService) {
    this.repository = repository;
    this.projectQueryService = projectQueryService;
  }

  /** StakeholderView（contract §3.8：旧保留字 user/key/from 已规避）。 */
  public record StakeholderView(long id,
      @Schema(allowableValues = {"program", "project"}) String objectType, long objectId, String account,
      @Schema(allowableValues = {"inside", "outside"}) String type,
      boolean isKey, String source, String createdBy, Instant createdAt, String updatedBy, Instant updatedAt) {

    static StakeholderView of(Stakeholder stakeholder) {
      return new StakeholderView(stakeholder.id(), stakeholder.objectType(), stakeholder.objectId(),
          stakeholder.account(), stakeholder.type(), stakeholder.isKey(), stakeholder.source(),
          stakeholder.createdBy(), stakeholder.createdAt(), stakeholder.updatedBy(), stakeholder.updatedAt());
    }
  }

  /** StakeholderList 载荷（contract：items + total）。 */
  public record StakeholderList(List<StakeholderView> items, long total) {}

  public StakeholderList page(SessionPrincipal actor, String objectType, long objectId,
      Map<String, String[]> params) {
    projectQueryService.requireVisible(actor, objectType, objectId);
    Filters filters = Filters.parse(params, REGISTRY);
    QueryCondition injected = new QueryColumn("object_type").eq(objectType)
        .and(new QueryColumn("object_id").eq(objectId))
        .and(new QueryColumn("deleted_at").isNull());
    QueryWrapper query = FilterPredicate.compile(filters, COLUMNS::get, value -> Optional.empty(), injected);
    List<StakeholderView> items = repository.queryPage(query, filters.offset(), filters.limit()).stream()
        .map(StakeholderView::of)
        .toList();
    QueryWrapper countQuery = FilterPredicate.compile(filters.forCount(), COLUMNS::get, value -> Optional.empty(), injected);
    return new StakeholderList(items, repository.countByQuery(countQuery));
  }
}
