package net.zentao.project.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.LinkedHashMap;
import java.util.Map;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.project.domain.CardRepository;
import net.zentao.project.domain.Lane;
import net.zentao.project.domain.LaneRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 看板列命令（project 卡 §3.5/§5 lanes 族）：新建/列配置/软删；
 * 列内有卡删除 → 42203（§5），归档只是 archived 标记。
 */
@Component
public class LaneHandlers {

  private static final int MIN_WIP_LIMIT = -1;
  private static final int MAX_WIP_LIMIT = 999;
  private static final int MAX_NAME_LENGTH = 90;
  private static final int MAX_COLOR_LENGTH = 32;

  private final LaneRepository laneRepository;
  private final CardRepository cardRepository;
  private final BoardQueryService queryService;

  public LaneHandlers(LaneRepository laneRepository, CardRepository cardRepository,
      BoardQueryService queryService) {
    this.laneRepository = laneRepository;
    this.cardRepository = cardRepository;
    this.queryService = queryService;
  }

  public record LaneCreateRequest(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name, String color,
      Integer wipLimit, Boolean archived, Integer sort) {}

  public record LaneUpdateRequest(String name, String color, Integer wipLimit, Boolean archived, Integer sort) {}

  /** LaneView（contract §3.5）。 */
  public record LaneView(long id, long boardId, String name, String color, int wipLimit, boolean archived, int sort) {

    static LaneView of(Lane lane) {
      return new LaneView(lane.id(), lane.boardId(), lane.name(), lane.color(), lane.wipLimit(), lane.archived(),
          lane.sort());
    }
  }

  @Transactional
  public LaneView create(SessionPrincipal actor, long boardId, LaneCreateRequest command) {
    queryService.requireVisibleBoard(actor, boardId);
    Map<String, String> errors = new LinkedHashMap<>();
    String name = requireName(command.name(), errors);
    Integer wipLimit = command.wipLimit() == null ? -1 : command.wipLimit();
    requireWipLimit(wipLimit, errors);
    requireColor(command.color(), errors);
    if (!errors.isEmpty()) {
      throw ApiException.validation(errors);
    }
    Lane lane = laneRepository.insert(new Lane(0, boardId, name, command.color(), wipLimit,
        Boolean.TRUE.equals(command.archived()), command.sort() == null ? 0 : command.sort()));
    return LaneView.of(lane);
  }

  @Transactional
  public LaneView update(SessionPrincipal actor, long boardId, long laneId, LaneUpdateRequest command) {
    Lane lane = require(laneId, boardId);
    Map<String, String> errors = new LinkedHashMap<>();
    String name = command.name() == null ? null : requireName(command.name(), errors);
    if (command.wipLimit() != null) {
      requireWipLimit(command.wipLimit(), errors);
    }
    requireColor(command.color(), errors);
    if (!errors.isEmpty()) {
      throw ApiException.validation(errors);
    }
    lane.update(name, command.color(), command.wipLimit(), command.archived(), command.sort());
    laneRepository.update(lane);
    return LaneView.of(lane);
  }

  /** DELETE：列内有卡 → 42203（§5/§8）。 */
  @Transactional
  public void delete(SessionPrincipal actor, long boardId, long laneId) {
    require(laneId, boardId);
    if (cardRepository.countActiveInLane(laneId) > 0) {
      throw ApiException.guardNotSatisfied("列内仍有卡片，不能删除。");
    }
    laneRepository.softDelete(laneId);
  }

  /** 路径 boardId 与列归属不一致（含不存在）→ 40401，与「错挂」同口径。 */
  private Lane require(long laneId, long boardId) {
    return laneRepository.findActiveById(laneId)
        .filter(lane -> lane.boardId() == boardId)
        .orElseThrow(() -> ApiException.notFound("看板列"));
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

  private static void requireWipLimit(int wipLimit, Map<String, String> errors) {
    if (wipLimit < MIN_WIP_LIMIT || wipLimit > MAX_WIP_LIMIT) {
      errors.put("wipLimit", "invalid");
    }
  }

  /** 颜色为自由文本（契约无 pattern），只挡超长以免撞 DB 列宽。 */
  private static void requireColor(String color, Map<String, String> errors) {
    if (color != null && color.length() > MAX_COLOR_LENGTH) {
      errors.put("color", "maxLength");
    }
  }
}
