package net.zentao.project.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.zentao.org.api.AccountApi;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.project.domain.Card;
import net.zentao.project.domain.CardRepository;
import net.zentao.project.domain.Lane;
import net.zentao.project.domain.LaneRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 看板卡片命令（project 卡 §3.6/§5 cards 族）：创建/部分更新/拖拽 move/归档。
 * move 归属单源 laneId+sort 同事务改写；目标列不属本看板 → 42201，wipLimit 超限 → 42203；
 * status 只有 doing/done 且由 PATCH 直改（§5 唯一状态直改例外），归档只置 archived。
 */
@Component
public class CardHandlers {

  private static final Set<String> STATUSES = Set.of("doing", "done");
  private static final int MAX_NAME_LENGTH = 255;
  private static final int MAX_COLOR_LENGTH = 32;

  private final CardRepository cardRepository;
  private final LaneRepository laneRepository;
  private final BoardQueryService queryService;
  private final AccountApi accountApi;

  public CardHandlers(CardRepository cardRepository, LaneRepository laneRepository, BoardQueryService queryService,
      AccountApi accountApi) {
    this.cardRepository = cardRepository;
    this.laneRepository = laneRepository;
    this.queryService = queryService;
    this.accountApi = accountApi;
  }

  public record CardCreateRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long laneId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
      String description,
      @Schema(allowableValues = {"doing", "done"}) String status, Integer priority,
      String assignee, LocalDate beginDate, LocalDate endDate, BigDecimal estimateHours, Integer progress,
      String color, Integer sort) {}

  public record CardUpdateRequest(String name, String description, Integer priority, String assignee,
      LocalDate beginDate, LocalDate endDate, BigDecimal estimateHours, Integer progress, String color,
      @Schema(allowableValues = {"doing", "done"}) String status,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer lockVersion) {}

  public record CardMoveRequest(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long laneId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer sort) {}

  /** CardView（contract §3.6）。 */
  public record CardView(long id, long boardId, long laneId, String name, String description,
      @Schema(allowableValues = {"doing", "done"}) String status,
      int priority, String assignee, LocalDate beginDate, LocalDate endDate, BigDecimal estimateHours, int progress,
      String color, boolean archived, int sort, String createdBy, Instant createdAt, String updatedBy,
      Instant updatedAt, int lockVersion) {

    static CardView of(Card card) {
      return new CardView(card.id(), card.boardId(), card.laneId(), card.name(), card.description(), card.status(),
          card.priority(), card.assignee(), card.beginDate(), card.endDate(), card.estimateHours(), card.progress(),
          card.color(), card.archived(), card.sort(), card.createdBy(), card.createdAt(), card.updatedBy(),
          card.updatedAt(), card.lockVersion());
    }
  }

  /** 卡片详情（看板页内抽屉数据源）：不存在 → 40401，看板不可见 → 40302。 */
  public CardView detail(SessionPrincipal actor, long cardId) {
    return CardView.of(require(actor, cardId));
  }

  @Transactional
  public CardView create(SessionPrincipal actor, long boardId, CardCreateRequest command) {
    queryService.requireVisibleBoard(actor, boardId);
    Map<String, String> errors = new LinkedHashMap<>();
    if (command.laneId() == null || requireLane(boardId, command.laneId()) == null) {
      errors.put("laneId", "invalid");
    }
    String name = requireName(command.name(), errors);
    String status = command.status() == null ? "doing" : command.status();
    if (!STATUSES.contains(status)) {
      errors.put("status", "invalid");
    }
    int priority = command.priority() == null ? 3 : command.priority();
    if (priority < 1 || priority > 4) {
      errors.put("priority", "invalid");
    }
    int progress = command.progress() == null ? 0 : command.progress();
    if (progress < 0 || progress > 100) {
      errors.put("progress", "invalid");
    }
    requireEstimateHours(command.estimateHours(), errors);
    requireColor(command.color(), errors);
    requireAssignee(command.assignee(), errors);
    requireDates(command.beginDate(), command.endDate(), errors);
    if (!errors.isEmpty()) {
      throw ApiException.validation(errors);
    }
    Card card = cardRepository.insert(new Card(0, boardId, command.laneId(), name, command.description(), status,
        priority, command.assignee(), command.beginDate(), command.endDate(), command.estimateHours(), progress,
        command.color(), false, command.sort() == null ? 0 : command.sort(), actor.account(), Instant.now(), null,
        null, 0));
    return CardView.of(card);
  }

  @Transactional
  public CardView update(SessionPrincipal actor, long cardId, CardUpdateRequest command) {
    Card card = require(actor, cardId);
    if (command.lockVersion() == null || command.lockVersion() != card.lockVersion()) {
      throw ApiException.lockConflict("数据已被他人修改，请刷新后重试。");
    }
    Map<String, String> errors = new LinkedHashMap<>();
    String name = command.name() == null ? null : requireName(command.name(), errors);
    if (command.priority() != null && (command.priority() < 1 || command.priority() > 4)) {
      errors.put("priority", "invalid");
    }
    if (command.progress() != null && (command.progress() < 0 || command.progress() > 100)) {
      errors.put("progress", "invalid");
    }
    if (command.status() != null && !STATUSES.contains(command.status())) {
      errors.put("status", "invalid");
    }
    requireEstimateHours(command.estimateHours(), errors);
    requireColor(command.color(), errors);
    requireAssignee(command.assignee(), errors);
    if (!errors.isEmpty()) {
      throw ApiException.validation(errors);
    }
    card.update(name, command.description(), command.priority(), command.assignee(), command.beginDate(),
        command.endDate(), command.estimateHours(), command.progress(), command.color(), command.status());
    requireDates(card.beginDate(), card.endDate(), errors);
    if (!errors.isEmpty()) {
      throw ApiException.validation(errors);
    }
    card.markUpdatedBy(actor.account());
    return CardView.of(repositoryUpdate(card));
  }

  /** 拖拽：改 laneId+sort（同事务）；目标 lane 不属本看板 → 42201，wipLimit 超限 → 42203。 */
  @Transactional
  public CardView move(SessionPrincipal actor, long cardId, CardMoveRequest command) {
    Card card = require(actor, cardId);
    if (command == null || command.laneId() == null || command.sort() == null) {
      throw ApiException.validation(Map.of(command == null || command.laneId() == null ? "laneId" : "sort",
          "required"));
    }
    Lane target = requireLane(card.boardId(), command.laneId());
    if (target == null) {
      throw ApiException.validation(Map.of("laneId", "invalid"));
    }
    long others = cardRepository.countActiveInLaneExcluding(target.id(), card.id());
    if (target.limitsWip() && others >= target.wipLimit()) {
      throw ApiException.guardNotSatisfied("目标列已达 WIP 上限。");
    }
    card.move(target.id(), command.sort());
    card.markUpdatedBy(actor.account());
    return CardView.of(repositoryUpdate(card));
  }

  /** 归档：archived=true，不改 status（§5）。 */
  @Transactional
  public CardView archive(SessionPrincipal actor, long cardId) {
    Card card = require(actor, cardId);
    card.archive();
    card.markUpdatedBy(actor.account());
    return CardView.of(repositoryUpdate(card));
  }

  /** 取消归档（B-PRJ-13）：archived=false，不改 status；与 archive 同形。 */
  @Transactional
  public CardView unarchive(SessionPrincipal actor, long cardId) {
    Card card = require(actor, cardId);
    card.unarchive();
    card.markUpdatedBy(actor.account());
    return CardView.of(repositoryUpdate(card));
  }

  /** 软删（A-07）：卡片无子对象，无守卫。 */
  @Transactional
  public void delete(SessionPrincipal actor, long cardId) {
    require(actor, cardId);
    cardRepository.softDelete(cardId);
  }

  private Card require(SessionPrincipal actor, long cardId) {
    Card card = cardRepository.findActiveById(cardId).orElseThrow(() -> ApiException.notFound("卡片"));
    queryService.requireVisibleBoard(actor, card.boardId());
    return card;
  }

  private Card repositoryUpdate(Card card) {
    return cardRepository.update(card)
        .orElseThrow(() -> ApiException.lockConflict("数据已被他人修改，请刷新后重试。"));
  }

  private Lane requireLane(long boardId, long laneId) {
    return laneRepository.findActiveById(laneId).filter(lane -> lane.boardId() == boardId).orElse(null);
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

  private static void requireEstimateHours(BigDecimal estimateHours, Map<String, String> errors) {
    if (estimateHours != null && estimateHours.signum() < 0) {
      errors.put("estimateHours", "invalid");
    }
  }

  private static void requireColor(String color, Map<String, String> errors) {
    if (color != null && color.length() > MAX_COLOR_LENGTH) {
      errors.put("color", "maxLength");
    }
  }

  private void requireAssignee(String assignee, Map<String, String> errors) {
    if (assignee != null && !assignee.isBlank() && !accountApi.missingAccounts(List.of(assignee)).isEmpty()) {
      errors.put("assignee", "notFound");
    }
  }

  private static void requireDates(LocalDate beginDate, LocalDate endDate, Map<String, String> errors) {
    if (beginDate != null && endDate != null && beginDate.isAfter(endDate)) {
      errors.put("endDate", "beforeBegin");
    }
  }
}
