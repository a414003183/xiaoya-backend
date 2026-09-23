package net.zentao.project.infra.web;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import net.zentao.platform.audit.Audit;
import net.zentao.platform.audit.AuditDiff;
import net.zentao.platform.rbac.RequirePrivilege;
import net.zentao.platform.session.SessionResolver;
import net.zentao.platform.web.CommentRequest;
import net.zentao.platform.web.DataEnvelope;
import net.zentao.project.app.BoardHandlers;
import net.zentao.project.app.BoardHandlers.BoardCreateRequest;
import net.zentao.project.app.BoardHandlers.BoardView;
import net.zentao.project.app.BoardQueryService;
import net.zentao.project.app.BoardSpaceHandlers;
import net.zentao.project.app.BoardSpaceHandlers.BoardSpaceCreateRequest;
import net.zentao.project.app.BoardSpaceHandlers.BoardSpaceUpdateRequest;
import net.zentao.project.app.BoardSpaceHandlers.BoardSpaceView;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 看板空间端点（project 卡 §5 board-spaces 族）。 */
@RestController
@RequestMapping("/api/v1")
public class BoardSpaceController {

  private final BoardQueryService queryService;
  private final BoardSpaceHandlers handlers;
  private final BoardHandlers boardHandlers;
  private final SessionResolver resolver;

  public BoardSpaceController(BoardQueryService queryService, BoardSpaceHandlers handlers,
      BoardHandlers boardHandlers, SessionResolver resolver) {
    this.queryService = queryService;
    this.handlers = handlers;
    this.boardHandlers = boardHandlers;
    this.resolver = resolver;
  }

  @GetMapping("/board-spaces")
  @Operation(operationId = "listBoardSpaces")
  @RequirePrivilege("board-view")
  public DataEnvelope<BoardQueryService.BoardSpaceList> list(HttpServletRequest request) {
    return DataEnvelope.of(queryService.pageSpaces(resolver.resolve(request), request.getParameterMap()));
  }

  @PostMapping("/board-spaces")
  @Operation(operationId = "createBoardSpace")
  @RequirePrivilege("board-space-create")
  @Audit(action = "board-space-create", objectType = "boardSpace")
  public DataEnvelope<BoardSpaceView> create(@RequestBody BoardSpaceCreateRequest body,
      HttpServletRequest request) {
    return DataEnvelope.of(handlers.create(resolver.resolve(request), body));
  }

  @GetMapping("/board-spaces/{boardSpaceId}")
  @Operation(operationId = "getBoardSpace")
  @RequirePrivilege("board-view")
  public DataEnvelope<BoardSpaceView> detail(@PathVariable long boardSpaceId, HttpServletRequest request) {
    return DataEnvelope.of(queryService.spaceDetail(resolver.resolve(request), boardSpaceId));
  }

  @PatchMapping("/board-spaces/{boardSpaceId}")
  @Operation(operationId = "updateBoardSpace")
  @RequirePrivilege("board-space-edit")
  @Audit(action = "board-space-update", objectType = "boardSpace")
  @AuditDiff(objectType = "boardSpace")
  public DataEnvelope<BoardSpaceView> update(@PathVariable long boardSpaceId,
      @RequestBody BoardSpaceUpdateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(handlers.update(resolver.resolve(request), boardSpaceId, body));
  }

  @PostMapping("/board-spaces/{boardSpaceId}/close")
  @Operation(operationId = "closeBoardSpace")
  @RequirePrivilege("board-space-close")
  @Audit(action = "board-space-close", objectType = "boardSpace")
  @AuditDiff(objectType = "boardSpace")
  public DataEnvelope<BoardSpaceView> close(@PathVariable long boardSpaceId,
      @RequestBody(required = false) CommentRequest body, HttpServletRequest request) {
    return DataEnvelope.of(handlers.close(resolver.resolve(request), boardSpaceId, comment(body)));
  }

  @PostMapping("/board-spaces/{boardSpaceId}/activate")
  @Operation(operationId = "activateBoardSpace")
  @RequirePrivilege("board-space-close")
  @Audit(action = "board-space-activate", objectType = "boardSpace")
  @AuditDiff(objectType = "boardSpace")
  public DataEnvelope<BoardSpaceView> activate(@PathVariable long boardSpaceId,
      @RequestBody(required = false) CommentRequest body, HttpServletRequest request) {
    return DataEnvelope.of(handlers.activate(resolver.resolve(request), boardSpaceId, comment(body)));
  }

  @DeleteMapping("/board-spaces/{boardSpaceId}")
  @Operation(operationId = "deleteBoardSpace")
  @RequirePrivilege("board-space-edit")
  @Audit(action = "board-space-delete", objectType = "boardSpace")
  @AuditDiff(objectType = "boardSpace")
  public DataEnvelope<Void> delete(@PathVariable long boardSpaceId, HttpServletRequest request) {
    handlers.delete(resolver.resolve(request), boardSpaceId);
    return DataEnvelope.empty();
  }

  @PostMapping("/board-spaces/{boardSpaceId}/boards")
  @Operation(operationId = "createBoard")
  @RequirePrivilege("board-create")
  @Audit(action = "board-space-board-create", objectType = "boardSpace")
  public DataEnvelope<BoardView> createBoard(@PathVariable long boardSpaceId,
      @RequestBody BoardCreateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(boardHandlers.create(resolver.resolve(request), boardSpaceId, body));
  }

  private static String comment(CommentRequest body) {
    return body == null ? null : body.comment();
  }
}
