package net.zentao.project.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.workflow.StateMachine;
import net.zentao.platform.workflow.WorkflowRegistry;
import net.zentao.requirement.api.StoryApi;
import net.zentao.requirement.api.StoryView;
import org.springframework.stereotype.Component;

/**
 * 执行需求看板（project 卡 §5 kanban 两行，K 范式数据源）：整板读 + 拖拽落列。
 * 列 key 与拖拽动作同源于 workflow/story.yml（列 = story 状态集，动作 = 目标列的 from→to 反查，
 * 见 project 卡 §5 lanes[].key 映射表）——本类不复制 story 状态机：守卫/副作用由 requirement 域裁决，
 * 42202（非法迁移）/42203（守卫未满足）原样透传。cardId 即 storyId。
 */
@Component
public class ExecutionKanbanHandler {

  private final StoryApi storyApi;
  private final ProjectQueryService projectQueryService;
  private final ProjectStoryQueryService storyQueryService;
  private final WorkflowRegistry workflowRegistry;

  public ExecutionKanbanHandler(StoryApi storyApi, ProjectQueryService projectQueryService,
      ProjectStoryQueryService storyQueryService, WorkflowRegistry workflowRegistry) {
    this.storyApi = storyApi;
    this.projectQueryService = projectQueryService;
    this.storyQueryService = storyQueryService;
    this.workflowRegistry = workflowRegistry;
  }

  /** ExecutionKanbanLane（contract：key + items[StoryView]）。 */
  public record ExecutionKanbanLane(
      @Schema(allowableValues = {"active", "changing", "changed", "closed", "draft", "reviewing"}) String key,
      List<StoryView> items) {}

  /** ExecutionKanbanView（contract：lanes）。空列保留——列结构由状态机决定，不随数据抖动。 */
  public record ExecutionKanbanView(List<ExecutionKanbanLane> lanes) {}

  /** ExecutionKanbanMoveRequest（contract：column = 目标列 key，comment 可选）。 */
  public record ExecutionKanbanMoveRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String column, String comment) {}

  /** 整板读取：执行可见性为唯一数据权限闸门（关联即入板，需求侧不再按产品收窄，避免整板缺列）。 */
  public ExecutionKanbanView board(SessionPrincipal actor, long executionId) {
    projectQueryService.requireVisible(actor, "execution", executionId);
    Map<String, List<StoryView>> byStatus = new LinkedHashMap<>();
    for (StoryView story : storyApi.findByIds(storyQueryService.storyIds("execution", executionId))) {
      byStatus.computeIfAbsent(story.status(), status -> new ArrayList<>()).add(story);
    }
    List<ExecutionKanbanLane> lanes = storyMachine().states().stream()
        .map(status -> new ExecutionKanbanLane(status, List.copyOf(byStatus.getOrDefault(status, List.of()))))
        .toList();
    return new ExecutionKanbanView(lanes);
  }

  /** 拖拽落列：非本执行关联需求（执行自身或所属项目均未关联）→ 40401；列 key 非法 → 42201；同列重拖幂等。 */
  public StoryView move(SessionPrincipal actor, long executionId, long cardId, ExecutionKanbanMoveRequest command) {
    projectQueryService.requireVisible(actor, "execution", executionId);
    if (!storyQueryService.storyIds("execution", executionId).contains(cardId)) {
      throw ApiException.notFound("entity.boardCard");
    }
    String column = command == null ? null : command.column();
    if (column == null || !storyMachine().states().contains(column)) {
      throw ApiException.validation(Map.of("column", "invalid"));
    }
    StoryView card = storyApi.findById(cardId).orElseThrow(() -> ApiException.notFound("entity.boardCard"));
    if (column.equals(card.status())) {
      return card;
    }
    return storyApi.fireAction(actor, cardId, actionOf(card.status(), column), command.comment());
  }

  /** 目标列动作 = story.yml 中 from=卡片当前状态、to=目标列的迁移（多候选取声明顺序首个）。 */
  private String actionOf(String status, String target) {
    return storyMachine().transitions().stream()
        .filter(transition -> target.equals(transition.to()) && transition.from().contains(status))
        .map(StateMachine.Transition::action)
        .findFirst()
        .orElseThrow(() -> ApiException.keyed(ErrorCode.STATE_ACTION_NOT_ALLOWED, "boardCard.state.columnDenied", target));
  }

  private StateMachine storyMachine() {
    return workflowRegistry.get("story").orElseThrow(() -> ApiException.keyed(ErrorCode.INTERNAL_ERROR, "workflow.machine.unregistered", "story"));
  }
}
