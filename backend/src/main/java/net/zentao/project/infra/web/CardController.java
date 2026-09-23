package net.zentao.project.infra.web;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import net.zentao.platform.audit.Audit;
import net.zentao.platform.audit.AuditDiff;
import net.zentao.platform.rbac.RequirePrivilege;
import net.zentao.platform.session.SessionResolver;
import net.zentao.platform.web.CommentRequest;
import net.zentao.platform.web.DataEnvelope;
import net.zentao.project.app.CardHandlers;
import net.zentao.project.app.CardHandlers.CardMoveRequest;
import net.zentao.project.app.CardHandlers.CardUpdateRequest;
import net.zentao.project.app.CardHandlers.CardView;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 看板卡片端点（project 卡 §5 cards 族）。 */
@RestController
@RequestMapping("/api/v1")
public class CardController {

  private final CardHandlers handlers;
  private final SessionResolver resolver;

  public CardController(CardHandlers handlers, SessionResolver resolver) {
    this.handlers = handlers;
    this.resolver = resolver;
  }

  @GetMapping("/cards/{cardId}")
  @Operation(operationId = "getCard")
  @RequirePrivilege("board-view")
  public DataEnvelope<CardView> detail(@PathVariable long cardId, HttpServletRequest request) {
    return DataEnvelope.of(handlers.detail(resolver.resolve(request), cardId));
  }

  @PatchMapping("/cards/{cardId}")
  @Operation(operationId = "updateCard")
  @RequirePrivilege("board-card-edit")
  @Audit(action = "card-update", objectType = "card")
  @AuditDiff(objectType = "card")
  public DataEnvelope<CardView> update(@PathVariable long cardId, @RequestBody CardUpdateRequest body,
      HttpServletRequest request) {
    return DataEnvelope.of(handlers.update(resolver.resolve(request), cardId, body));
  }

  @PostMapping("/cards/{cardId}/move")
  @Operation(operationId = "moveCard")
  @RequirePrivilege("board-card-edit")
  @Audit(action = "card-move", objectType = "card")
  public DataEnvelope<CardView> move(@PathVariable long cardId, @RequestBody CardMoveRequest body,
      HttpServletRequest request) {
    return DataEnvelope.of(handlers.move(resolver.resolve(request), cardId, body));
  }

  /** 归档体只为契约形状保留（archive 无 workflow，评论不产生副作用）。 */
  @PostMapping("/cards/{cardId}/archive")
  @Operation(operationId = "archiveCard")
  @RequirePrivilege("board-card-edit")
  @Audit(action = "card-archive", objectType = "card")
  @AuditDiff(objectType = "card")
  public DataEnvelope<CardView> archive(@PathVariable long cardId,
      @RequestBody(required = false) CommentRequest body, HttpServletRequest request) {
    return DataEnvelope.of(handlers.archive(resolver.resolve(request), cardId));
  }

  /** 取消归档（B-PRJ-13）：与 archive 同形，评论不产生副作用。 */
  @PostMapping("/cards/{cardId}/unarchive")
  @Operation(operationId = "unarchiveCard")
  @RequirePrivilege("board-card-edit")
  @Audit(action = "card-unarchive", objectType = "card")
  @AuditDiff(objectType = "card")
  public DataEnvelope<CardView> unarchive(@PathVariable long cardId,
      @RequestBody(required = false) CommentRequest body, HttpServletRequest request) {
    return DataEnvelope.of(handlers.unarchive(resolver.resolve(request), cardId));
  }

  @DeleteMapping("/cards/{cardId}")
  @Operation(operationId = "deleteCard")
  @RequirePrivilege("board-card-edit")
  @Audit(action = "card-delete", objectType = "card")
  @AuditDiff(objectType = "card")
  public DataEnvelope<Void> delete(@PathVariable long cardId, HttpServletRequest request) {
    handlers.delete(resolver.resolve(request), cardId);
    return DataEnvelope.empty();
  }
}
