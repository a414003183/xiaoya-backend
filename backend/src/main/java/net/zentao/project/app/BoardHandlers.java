package net.zentao.project.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.zentao.org.api.AccountApi;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.workflow.WorkflowEngine;
import net.zentao.project.domain.AclEntryRepository;
import net.zentao.project.domain.Board;
import net.zentao.project.domain.BoardRepository;
import net.zentao.project.domain.BoardSpace;
import net.zentao.project.domain.CardRepository;
import net.zentao.project.domain.LaneRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 看板命令与整板读（project 卡 §3.4/§5 boards 族）：空间下创建、部分更新、close/activate 二态（§4）；
 * GET /boards/{boardId} 一次下发 board + lanes + cards（K 范式数据源）。
 */
@Component
public class BoardHandlers {

  private static final Set<String> ACLS = Set.of("open", "private", "extend");
  private static final int MAX_NAME_LENGTH = 90;

  private final BoardRepository repository;
  private final LaneRepository laneRepository;
  private final CardRepository cardRepository;
  private final AclEntryRepository aclEntryRepository;
  private final AccountApi accountApi;
  private final WorkflowEngine engine;
  private final BoardQueryService queryService;

  public BoardHandlers(BoardRepository repository, LaneRepository laneRepository, CardRepository cardRepository,
      AclEntryRepository aclEntryRepository, AccountApi accountApi, WorkflowEngine engine,
      BoardQueryService queryService) {
    this.repository = repository;
    this.laneRepository = laneRepository;
    this.cardRepository = cardRepository;
    this.aclEntryRepository = aclEntryRepository;
    this.accountApi = accountApi;
    this.engine = engine;
    this.queryService = queryService;
  }

  public record BoardCreateRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
      String owner, List<String> team, String description,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"extend", "open", "private"}) String acl,
      List<String> whitelist, Integer sort) {}

  public record BoardUpdateRequest(String name, String owner, List<String> team,
      @Schema(allowableValues = {"extend", "open", "private"}) String acl, List<String> whitelist,
      String description, Integer sort,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer lockVersion) {}

  /** BoardView（contract §3.4；lanes/cards 仅整板端点填充）。 */
  public record BoardView(long id, long spaceId, String name, String owner, List<String> team, String description,
      @Schema(allowableValues = {"extend", "open", "private"}) String acl, List<String> whitelist,
      @Schema(allowableValues = {"active", "closed"}) String status, int sort, String createdBy, Instant createdAt,
      String updatedBy, Instant updatedAt, String closedBy, Instant closedAt, List<LaneHandlers.LaneView> lanes,
      List<CardHandlers.CardView> cards, int lockVersion) {

    static BoardView of(Board board) {
      return of(board, List.of(), List.of());
    }

    static BoardView of(Board board, List<LaneHandlers.LaneView> lanes, List<CardHandlers.CardView> cards) {
      return new BoardView(board.id(), board.spaceId(), board.name(), board.owner(), board.team(),
          board.description(), board.acl(), board.whitelist(), board.status(), board.sort(), board.createdBy(),
          board.createdAt(), board.updatedBy(), board.updatedAt(), board.closedBy(), board.closedAt(), lanes, cards,
          board.lockVersion());
    }
  }

  @Transactional
  public BoardView create(SessionPrincipal actor, long spaceId, BoardCreateRequest command) {
    BoardSpace space = queryService.requireVisibleSpace(actor, spaceId);
    Map<String, String> errors = new LinkedHashMap<>();
    String name = requireName(command.name(), errors);
    String acl = command.acl() == null ? "extend" : command.acl();
    if (!ACLS.contains(acl)) {
      errors.put("acl", "invalid");
    }
    String owner = command.owner() == null ? actor.account() : command.owner();
    List<String> team = command.team() == null ? List.of() : List.copyOf(command.team());
    List<String> whitelist = command.whitelist() == null ? List.of() : List.copyOf(command.whitelist());
    requireAccounts(owner, team, whitelist, errors);
    if (!errors.isEmpty()) {
      throw ApiException.validation(errors);
    }
    Board board = repository.insert(new Board(0, space.id(), name, owner, team, command.description(), acl, whitelist,
        "active", command.sort() == null ? 0 : command.sort(), actor.account(), Instant.now(), null, null, null, null,
        0));
    aclEntryRepository.replace("board", board.id(), whitelist);
    return BoardView.of(board);
  }

  @Transactional
  public BoardView update(SessionPrincipal actor, long boardId, BoardUpdateRequest command) {
    Board board = require(boardId);
    if (command.lockVersion() == null || command.lockVersion() != board.lockVersion()) {
      throw ApiException.lockConflict();
    }
    Map<String, String> errors = new LinkedHashMap<>();
    String name = command.name() == null ? null : requireName(command.name(), errors);
    if (command.acl() != null && !ACLS.contains(command.acl())) {
      errors.put("acl", "invalid");
    }
    List<String> team = command.team() == null ? null : List.copyOf(command.team());
    List<String> whitelist = command.whitelist() == null ? null : List.copyOf(command.whitelist());
    requireAccounts(command.owner(), team == null ? List.of() : team, whitelist == null ? List.of() : whitelist,
        errors);
    if (!errors.isEmpty()) {
      throw ApiException.validation(errors);
    }
    board.update(name, command.owner(), team, command.acl(), whitelist, command.description(), command.sort());
    board.markUpdatedBy(actor.account());
    Board saved = save(board);
    if (whitelist != null) {
      aclEntryRepository.replace("board", saved.id(), saved.whitelist());
    }
    return BoardView.of(saved);
  }

  @Transactional
  public BoardView close(SessionPrincipal actor, long boardId, String comment) {
    Board board = require(boardId);
    engine.fire(new BoardWorkflowTargets.BoardTarget(board, actor.account()), "close", comment);
    board.markUpdatedBy(actor.account());
    return BoardView.of(save(board));
  }

  @Transactional
  public BoardView activate(SessionPrincipal actor, long boardId, String comment) {
    Board board = require(boardId);
    engine.fire(new BoardWorkflowTargets.BoardTarget(board, actor.account()), "activate", comment);
    board.markUpdatedBy(actor.account());
    return BoardView.of(save(board));
  }

  /** 整板读（§5）：未删列按 sort 升序 + 未删卡片（含 archived 标记，前端按需折叠）。 */
  public BoardView detail(SessionPrincipal principal, long boardId) {
    Board board = queryService.requireVisibleBoard(principal, boardId);
    List<LaneHandlers.LaneView> lanes = laneRepository.findActiveByBoardId(boardId).stream()
        .map(LaneHandlers.LaneView::of)
        .toList();
    List<CardHandlers.CardView> cards = cardRepository.findActiveByBoardId(boardId).stream()
        .map(CardHandlers.CardView::of)
        .toList();
    return BoardView.of(board, lanes, cards);
  }

  /** 软删（A-07）：存在未删卡片 → 42203（列不拦截，随板一同不可达）。 */
  @Transactional
  public void delete(SessionPrincipal actor, long boardId) {
    queryService.requireVisibleBoard(actor, boardId);
    if (cardRepository.countActiveByBoardId(boardId) > 0) {
      throw ApiException.keyed(ErrorCode.GUARD_NOT_SATISFIED, "board.guard.hasCards");
    }
    repository.softDelete(boardId);
  }

  private Board require(long boardId) {
    return repository.findActiveById(boardId).orElseThrow(() -> ApiException.notFound("entity.board"));
  }

  private Board save(Board board) {
    return repository.update(board)
        .orElseThrow(() -> ApiException.lockConflict());
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

  /** §3.4：owner/team/whitelist 必须是存在账号。 */
  private void requireAccounts(String owner, List<String> team, List<String> whitelist, Map<String, String> errors) {
    if (owner != null && !owner.isBlank() && !accountApi.missingAccounts(List.of(owner)).isEmpty()) {
      errors.put("owner", "notFound");
    }
    if (!team.isEmpty() && !accountApi.missingAccounts(team).isEmpty()) {
      errors.put("team", "notFound");
    }
    if (!whitelist.isEmpty() && !accountApi.missingAccounts(whitelist).isEmpty()) {
      errors.put("whitelist", "notFound");
    }
  }
}
