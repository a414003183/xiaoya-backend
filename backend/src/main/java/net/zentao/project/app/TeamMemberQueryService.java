package net.zentao.project.app;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryWrapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.zentao.platform.filters.FieldRegistry;
import net.zentao.platform.filters.FilterPredicate;
import net.zentao.platform.filters.Filters;
import net.zentao.platform.filters.LikePatterns;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.project.api.ProjectApi;
import net.zentao.project.api.ProjectView;
import net.zentao.project.domain.TeamMember;
import net.zentao.project.domain.TeamMemberRepository;
import org.springframework.stereotype.Component;

/** 团队成员列表查询与「成员可用天数」口径（project 卡 §3.7 DSL 白名单 + §5 members 族读侧，A6）。 */
@Component
public class TeamMemberQueryService {

  private static final FieldRegistry REGISTRY = FieldRegistry.allowing(
      Set.of("objectType", "objectId", "account"),
      Set.of("id", "sort", "joinDate"),
      Set.of());

  private static final Map<String, String> COLUMNS = Map.ofEntries(
      Map.entry("objectType", "object_type"),
      Map.entry("objectId", "object_id"),
      Map.entry("account", "account"),
      Map.entry("id", "id"),
      Map.entry("sort", "sort"),
      Map.entry("joinDate", "join_date"));

  private final TeamMemberRepository repository;
  private final ProjectApi projectApi;
  private final ProjectQueryService projectQueryService;

  public TeamMemberQueryService(TeamMemberRepository repository, ProjectApi projectApi,
      ProjectQueryService projectQueryService) {
    this.repository = repository;
    this.projectApi = projectApi;
    this.projectQueryService = projectQueryService;
  }

  /** TeamMemberView（contract §3.7：id/objectType/objectId/account/role/joinDate/days/hours/sort）。 */
  public record TeamMemberView(long id,
      @Schema(allowableValues = {"execution", "project"}) String objectType, long objectId, String account,
      String role, LocalDate joinDate, int days, BigDecimal hours, int sort) {

    static TeamMemberView of(TeamMember member) {
      return new TeamMemberView(member.id(), member.objectType(), member.objectId(), member.account(),
          member.role(), member.joinDate(), member.days(), member.hours(), member.sort());
    }
  }

  /** TeamMemberList 载荷（contract：items + total）。 */
  public record TeamMemberList(List<TeamMemberView> items, long total) {}

  public TeamMemberList page(SessionPrincipal actor, String objectType, long objectId,
      Map<String, String[]> params) {
    projectQueryService.requireVisible(actor, objectType, objectId);
    Filters filters = Filters.parse(params, REGISTRY);
    QueryCondition injected = ownRows(objectType, objectId);
    QueryWrapper query = FilterPredicate.compile(filters, COLUMNS::get, value -> Optional.empty(), injected);
    List<TeamMemberView> items = repository.queryPage(query, filters.offset(), filters.limit()).stream()
        .map(TeamMemberView::of)
        .toList();
    QueryWrapper countQuery = FilterPredicate.compile(filters.forCount(), COLUMNS::get, value -> Optional.empty(), injected);
    return new TeamMemberList(items, repository.countByQuery(countQuery));
  }

  /**
   * 成员 days 上限（§3.7「≤所在项目 days」）：执行成员以所属项目的 days 为准。
   * 项目 days ≤0（未设置/脏数据）返回 -1 = 不设上限，避免整表成员行被 42201 卡死。
   */
  public int daysLimit(String objectType, long objectId) {
    return projectViewOf(objectType, objectId).map(ProjectView::days).filter(days -> days > 0).orElse(-1);
  }

  private Optional<ProjectView> projectViewOf(String objectType, long objectId) {
    if (!"execution".equals(objectType)) {
      return projectApi.findById(objectId);
    }
    return projectApi.findById(objectId).map(ProjectView::parentId).flatMap(projectApi::findById);
  }

  private static QueryCondition ownRows(String objectType, long objectId) {
    return new QueryColumn("object_type").eq(objectType)
        .and(new QueryColumn("object_id").eq(objectId))
        .and(new QueryColumn("deleted_at").isNull());
  }
}
