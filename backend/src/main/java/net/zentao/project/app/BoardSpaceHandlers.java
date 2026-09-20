package net.zentao.project.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.zentao.org.api.AccountApi;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.workflow.WorkflowEngine;
import net.zentao.project.domain.AclEntryRepository;
import net.zentao.project.domain.BoardRepository;
import net.zentao.project.domain.BoardSpace;
import net.zentao.project.domain.BoardSpaceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 看板空间命令（project 卡 §3.3/§5 board-spaces 族）：创建/部分更新 + close/activate 二态（§4）。
 * team/whitelist 账号必须存在；whitelist 走 acl_entry 单源（§2）。
 */
@Component
public class BoardSpaceHandlers {

  private static final Set<String> TYPES = Set.of("cooperation", "public", "private");
  private static final Set<String> ACLS = Set.of("open", "private");
  private static final int MAX_NAME_LENGTH = 90;

  private final BoardSpaceRepository repository;
  private final BoardRepository boardRepository;
  private final AclEntryRepository aclEntryRepository;
  private final AccountApi accountApi;
  private final WorkflowEngine engine;
  private final BoardQueryService queryService;

  public BoardSpaceHandlers(BoardSpaceRepository repository, BoardRepository boardRepository,
      AclEntryRepository aclEntryRepository, AccountApi accountApi, WorkflowEngine engine,
      BoardQueryService queryService) {
    this.repository = repository;
    this.boardRepository = boardRepository;
    this.aclEntryRepository = aclEntryRepository;
    this.accountApi = accountApi;
    this.engine = engine;
    this.queryService = queryService;
  }

  public record BoardSpaceCreateRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
          allowableValues = {"cooperation", "private", "public"}) String type,
      String owner, List<String> team, String description,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"open", "private"}) String acl,
      List<String> whitelist, Integer sort) {}

  public record BoardSpaceUpdateRequest(String name,
      @Schema(allowableValues = {"cooperation", "private", "public"}) String type, String owner, List<String> team,
      @Schema(allowableValues = {"open", "private"}) String acl,
      List<String> whitelist, String description, Integer sort,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer lockVersion) {}

  /** BoardSpaceView（contract §3.3；boards 仅详情端点填充，列表恒空数组）。 */
  public record BoardSpaceView(long id, String name,
      @Schema(allowableValues = {"cooperation", "private", "public"}) String type, String owner, List<String> team,
      String description, @Schema(allowableValues = {"open", "private"}) String acl, List<String> whitelist,
      @Schema(allowableValues = {"active", "closed"}) String status, int sort, String createdBy, Instant createdAt,
      String updatedBy, Instant updatedAt, String closedBy, Instant closedAt, List<BoardHandlers.BoardView> boards,
      int lockVersion) {

    static BoardSpaceView of(BoardSpace space, List<BoardHandlers.BoardView> boards) {
      return new BoardSpaceView(space.id(), space.name(), space.type(), space.owner(), space.team(),
          space.description(), space.acl(), space.whitelist(), space.status(), space.sort(), space.createdBy(),
          space.createdAt(), space.updatedBy(), space.updatedAt(), space.closedBy(), space.closedAt(), boards,
          space.lockVersion());
    }
  }

  @Transactional
  public BoardSpaceView create(SessionPrincipal actor, BoardSpaceCreateRequest command) {
    Map<String, String> errors = new LinkedHashMap<>();
    String name = requireName(command.name(), errors);
    String type = command.type() == null ? "cooperation" : command.type();
    if (!TYPES.contains(type)) {
      errors.put("type", "invalid");
    }
    String acl = command.acl() == null ? "open" : command.acl();
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
    BoardSpace space = repository.insert(new BoardSpace(0, name, type, owner, team, command.description(), acl,
        whitelist, "active", command.sort() == null ? 0 : command.sort(), actor.account(), Instant.now(), null, null,
        null, null, 0));
    aclEntryRepository.replace("board_space", space.id(), whitelist);
    return BoardSpaceView.of(space, List.of());
  }

  @Transactional
  public BoardSpaceView update(SessionPrincipal actor, long spaceId, BoardSpaceUpdateRequest command) {
    BoardSpace space = require(spaceId);
    if (command.lockVersion() == null || command.lockVersion() != space.lockVersion()) {
      throw ApiException.lockConflict("数据已被他人修改，请刷新后重试。");
    }
    Map<String, String> errors = new LinkedHashMap<>();
    String name = command.name() == null ? null : requireName(command.name(), errors);
    if (command.type() != null && !TYPES.contains(command.type())) {
      errors.put("type", "invalid");
    }
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
    space.update(name, command.type(), command.owner(), team, command.acl(), whitelist, command.description(),
        command.sort());
    space.markUpdatedBy(actor.account());
    BoardSpace saved = save(space);
    if (whitelist != null) {
      aclEntryRepository.replace("board_space", saved.id(), saved.whitelist());
    }
    return BoardSpaceView.of(saved, List.of());
  }

  @Transactional
  public BoardSpaceView close(SessionPrincipal actor, long spaceId, String comment) {
    BoardSpace space = require(spaceId);
    engine.fire(new BoardWorkflowTargets.BoardSpaceTarget(space, actor.account()), "close", comment);
    space.markUpdatedBy(actor.account());
    return BoardSpaceView.of(save(space), List.of());
  }  @Transactional
  public BoardSpaceView activate(SessionPrincipal actor, long spaceId, String comment) {
    BoardSpace space = require(spaceId);
    engine.fire(new BoardWorkflowTargets.BoardSpaceTarget(space, actor.account()), "activate", comment);
    space.markUpdatedBy(actor.account());
    return BoardSpaceView.of(save(space), List.of());
  }

  /** 软删（A-07）：存在未删看板 → 42203；可见性闸门与读侧同源。 */
  @Transactional
  public void delete(SessionPrincipal actor, long spaceId) {
    queryService.requireVisibleSpace(actor, spaceId);
    if (boardRepository.countActiveBySpaceId(spaceId) > 0) {
      throw ApiException.guardNotSatisfied("空间内仍有看板，不能删除。");
    }
    repository.softDelete(spaceId);
  }

  private BoardSpace require(long spaceId) {
    return repository.findActiveById(spaceId).orElseThrow(() -> ApiException.notFound("看板空间"));
  }

  private BoardSpace save(BoardSpace space) {
    return repository.update(space)
        .orElseThrow(() -> ApiException.lockConflict("数据已被他人修改，请刷新后重试。"));
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

  /** §3.3：owner/team/whitelist 必须是存在账号。 */
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
