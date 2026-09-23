package net.zentao.project.app;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryWrapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.filters.FieldRegistry;
import net.zentao.platform.filters.FilterPredicate;
import net.zentao.platform.filters.Filters;
import net.zentao.platform.filters.LikePatterns;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.project.domain.Stage;
import net.zentao.project.domain.StageRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 阶段类型字典命令与列表（project 卡 §3.2/§5 stages 族）：
 * 同 projectModel 下 percent 累计 ≤100，超限 → 42201 且 field=percent；DELETE 为软删。
 */
@Component
public class ManageStageHandler {

  private static final Set<String> TYPES =
      Set.of("mix", "request", "design", "dev", "qa", "release", "review", "other");
  private static final Set<String> PROJECT_MODELS = Set.of("waterfall");
  private static final BigDecimal MAX_PERCENT = new BigDecimal("100");
  private static final int MAX_NAME_LENGTH = 255;

  private static final FieldRegistry REGISTRY = FieldRegistry.allowing(
      Set.of("type", "projectModel"),
      Set.of("id", "sort"),
      Set.of("name"));

  private static final Map<String, String> COLUMNS = Map.ofEntries(
      Map.entry("type", "type"),
      Map.entry("projectModel", "project_model"),
      Map.entry("id", "id"),
      Map.entry("name", "name"),
      Map.entry("sort", "sort"));

  private final StageRepository repository;

  public ManageStageHandler(StageRepository repository) {
    this.repository = repository;
  }

  public record StageCreateRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal percent,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
          allowableValues = {"design", "dev", "mix", "other", "qa", "release", "request", "review"}) String type,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"waterfall"}) String projectModel,
      Integer sort) {}

  public record StageUpdateRequest(String name, BigDecimal percent,
      @Schema(allowableValues = {"design", "dev", "mix", "other", "qa", "release", "request", "review"}) String type,
      Integer sort) {}

  /** StageView（contract：id/name/percent/type/projectModel/sort + 审计四件套）。 */
  public record StageView(long id, String name, BigDecimal percent,
      @Schema(allowableValues = {"design", "dev", "mix", "other", "qa", "release", "request", "review"}) String type,
      @Schema(allowableValues = {"waterfall"}) String projectModel, int sort,
      String createdBy, Instant createdAt, String updatedBy, Instant updatedAt) {

    static StageView of(Stage stage) {
      return new StageView(stage.id(), stage.name(), stage.percent(), stage.type(), stage.projectModel(), stage.sort(),
          stage.createdBy(), stage.createdAt(), stage.updatedBy(), stage.updatedAt());
    }
  }

  /** StageList 载荷（contract：items + total）。 */
  public record StageList(List<StageView> items, long total) {}

  @Transactional
  public StageView create(SessionPrincipal actor, StageCreateRequest command) {
    Map<String, String> errors = new LinkedHashMap<>();
    String name = requireName(command.name(), errors);
    BigDecimal percent = requirePercent(command.percent(), errors);
    if (percent != null && !errors.containsKey("percent")) {
      requireWithinLimit(command.projectModel() == null ? "waterfall" : command.projectModel(), percent, 0, errors);
    }
    String type = requireType(command.type() == null ? "other" : command.type(), errors);
    String projectModel = requireProjectModel(command.projectModel(), errors);
    if (!errors.isEmpty()) {
      throw ApiException.validation(errors);
    }
    Stage stage = repository.insert(new Stage(0, name, percent, type, projectModel,
        command.sort() == null ? 0 : command.sort(), actor.account(), Instant.now(), null, null));
    return StageView.of(stage);
  }

  @Transactional
  public StageView update(SessionPrincipal actor, long stageId, StageUpdateRequest command) {
    Stage stage = repository.findActiveById(stageId).orElseThrow(() -> ApiException.notFound("entity.stage"));
    Map<String, String> errors = new LinkedHashMap<>();
    String name = command.name() == null ? null : requireName(command.name(), errors);
    // PATCH 语义：null = 不修改，故仅在传值时校验
    BigDecimal percent = command.percent() == null ? null : requirePercent(command.percent(), errors);
    String type = command.type() == null ? null : requireType(command.type(), errors);
    if (percent != null && !errors.containsKey("percent")) {
      requireWithinLimit(stage.projectModel(), percent, stage.id(), errors);
    }
    if (!errors.isEmpty()) {
      throw ApiException.validation(errors);
    }
    stage.update(name, percent, type, command.sort());
    stage.markUpdatedBy(actor.account());
    repository.update(stage);
    return StageView.of(stage);
  }

  @Transactional
  public void delete(SessionPrincipal actor, long stageId) {
    repository.findActiveById(stageId).orElseThrow(() -> ApiException.notFound("entity.stage"));
    repository.softDelete(stageId);
  }

  public StageList page(SessionPrincipal principal, Map<String, String[]> params) {
    Filters filters = Filters.parse(params, REGISTRY);
    QueryCondition injected = new QueryColumn("deleted_at").isNull();
    if (filters.q() != null && !filters.q().isBlank()) {
      injected = injected.and(new QueryColumn("name").likeRaw(LikePatterns.contains(filters.q())));
    }
    List<StageView> items = repository
        .queryPage(FilterPredicate.compile(filters, COLUMNS::get, value -> Optional.empty(), injected),
            filters.offset(), filters.limit())
        .stream()
        .map(StageView::of)
        .toList();
    QueryWrapper countQuery = FilterPredicate.compile(filters.forCount(), COLUMNS::get, value -> Optional.empty(), injected);
    return new StageList(items, repository.countByQuery(countQuery));
  }

  private static String requireName(String name, Map<String, String> errors) {
    String trimmed = name == null ? null : name.trim();
    if (trimmed == null || trimmed.isEmpty()) {
      errors.put("name", "required");
      return null;
    }
    if (trimmed.length() > MAX_NAME_LENGTH) {
      errors.put("name", "maxLength");
    }
    return trimmed;
  }

  private static BigDecimal requirePercent(BigDecimal percent, Map<String, String> errors) {
    if (percent == null) {
      errors.put("percent", "required");
      return null;
    }
    if (percent.signum() < 0 || percent.compareTo(MAX_PERCENT) > 0) {
      errors.put("percent", "invalid");
      return null;
    }
    return percent;
  }

  private static String requireType(String type, Map<String, String> errors) {
    if (!TYPES.contains(type)) {
      errors.put("type", "invalid");
    }
    return type;
  }

  private static String requireProjectModel(String projectModel, Map<String, String> errors) {
    String value = projectModel == null ? "waterfall" : projectModel;
    if (!PROJECT_MODELS.contains(value)) {
      errors.put("projectModel", "invalid");
    }
    return value;
  }

  /** 同 projectModel 下 percent 累计 ≤100（编辑时排除自身）。 */
  private void requireWithinLimit(String projectModel, BigDecimal percent, long excludeId,
      Map<String, String> errors) {
    BigDecimal used = repository.findAllActive().stream()
        .filter(stage -> stage.projectModel().equals(projectModel) && stage.id() != excludeId)
        .map(Stage::percent)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (used.add(percent).compareTo(MAX_PERCENT) > 0) {
      errors.put("percent", "overLimit");
    }
  }
}
