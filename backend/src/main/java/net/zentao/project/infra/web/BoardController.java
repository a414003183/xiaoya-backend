package net.zentao.project.infra.web;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import net.zentao.platform.rbac.RequirePrivilege;
import net.zentao.platform.session.SessionResolver;
import net.zentao.platform.web.CommentRequest;
import net.zentao.platform.web.DataEnvelope;
import net.zentao.project.app.BoardHandlers;
import net.zentao.project.app.BoardHandlers.BoardUpdateRequest;
import net.zentao.project.app.BoardHandlers.BoardView;
import net.zentao.project.app.BoardQueryService;
import net.zentao.project.app.CardHandlers;
import net.zentao.project.app.CardHandlers.CardCreateRequest;
import net.zentao.project.app.CardHandlers.CardView;
import net.zentao.project.app.LaneHandlers;
import net.zentao.project.app.LaneHandlers.LaneCreateRequest;
import net.zentao.project.app.LaneHandlers.LaneUpdateRequest;
import net.zentao.project.app.LaneHandlers.LaneView;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 看板 / 列 / 卡片列表端点（project 卡 §5 boards 族）。 */
@RestController
@RequestMapping("/api/v1")
public class BoardController {

  private final BoardHandlers boardHandlers;
  private final BoardQueryService queryService;
  private final LaneHandlers laneHandlers;
  private final CardHandlers cardHandlers;
  private final SessionResolver resolver;

  public BoardController(BoardHandlers boardHandlers, BoardQueryService queryService, LaneHandlers laneHandlers,
      CardHandlers cardHandlers, SessionResolver resolver) {
    this.boardHandlers = boardHandlers;
    this.queryService = queryService;
    this.laneHandlers = laneHandlers;
    this.cardHandlers = cardHandlers;
    this.resolver = resolver;
  }

  @GetMapping("/boards/{boardId}")
  @Operation(operationId = "getBoard")
  @RequirePrivilege("board-view")
  public DataEnvelope<BoardView> detail(@PathVariable long boardId, HttpServletRequest request) {
    return DataEnvelope.of(boardHandlers.detail(resolver.resolve(request), boardId));
  }

  @PatchMapping("/boards/{boardId}")
  @Operation(operationId = "updateBoard")
  @RequirePrivilege("board-edit")
  public DataEnvelope<BoardView> update(@PathVariable long boardId, @RequestBody BoardUpdateRequest body,
      HttpServletRequest request) {
    return DataEnvelope.of(boardHandlers.update(resolver.resolve(request), boardId, body));
  }

  @PostMapping("/boards/{boardId}/close")
  @Operation(operationId = "closeBoard")
  @RequirePrivilege("board-close")
  public DataEnvelope<BoardView> close(@PathVariable long boardId,
      @RequestBody(required = false) CommentRequest body, HttpServletRequest request) {
    return DataEnvelope.of(boardHandlers.close(resolver.resolve(request), boardId, comment(body)));
  }

  @PostMapping("/boards/{boardId}/activate")
  @Operation(operationId = "activateBoard")
  @RequirePrivilege("board-close")
  public DataEnvelope<BoardView> activate(@PathVariable long boardId,
      @RequestBody(required = false) CommentRequest body, HttpServletRequest request) {
    return DataEnvelope.of(boardHandlers.activate(resolver.resolve(request), boardId, comment(body)));
  }

  @DeleteMapping("/boards/{boardId}")
  @Operation(operationId = "deleteBoard")
  @RequirePrivilege("board-edit")
  public DataEnvelope<Void> delete(@PathVariable long boardId, HttpServletRequest request) {
    boardHandlers.delete(resolver.resolve(request), boardId);
    return DataEnvelope.empty();
  }

  @PostMapping("/boards/{boardId}/lanes")
  @Operation(operationId = "createLane")
  @RequirePrivilege("board-edit")
  public DataEnvelope<LaneView> createLane(@PathVariable long boardId, @RequestBody LaneCreateRequest body,
      HttpServletRequest request) {
    return DataEnvelope.of(laneHandlers.create(resolver.resolve(request), boardId, body));
  }

  @PatchMapping("/boards/{boardId}/lanes/{laneId}")
  @Operation(operationId = "updateLane")
  @RequirePrivilege("board-edit")
  public DataEnvelope<LaneView> updateLane(@PathVariable long boardId, @PathVariable long laneId,
      @RequestBody LaneUpdateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(laneHandlers.update(resolver.resolve(request), boardId, laneId, body));
  }

  @DeleteMapping("/boards/{boardId}/lanes/{laneId}")
  @Operation(operationId = "deleteLane")
  @RequirePrivilege("board-edit")
  public DataEnvelope<Void> deleteLane(@PathVariable long boardId, @PathVariable long laneId,
      HttpServletRequest request) {
    laneHandlers.delete(resolver.resolve(request), boardId, laneId);
    return DataEnvelope.empty();
  }

  @GetMapping("/boards/{boardId}/cards")
  @Operation(operationId = "listBoardCards")
  @RequirePrivilege("board-view")
  public DataEnvelope<BoardQueryService.CardList> listCards(@PathVariable long boardId, HttpServletRequest request) {
    return DataEnvelope.of(
        queryService.pageCards(resolver.resolve(request), boardId, request.getParameterMap()));
  }

  @PostMapping("/boards/{boardId}/cards")
  @Operation(operationId = "createCard")
  @RequirePrivilege("board-card-create")
  public DataEnvelope<CardView> createCard(@PathVariable long boardId, @RequestBody CardCreateRequest body,
      HttpServletRequest request) {
    return DataEnvelope.of(cardHandlers.create(resolver.resolve(request), boardId, body));
  }

  private static String comment(CommentRequest body) {
    return body == null ? null : body.comment();
  }
}
