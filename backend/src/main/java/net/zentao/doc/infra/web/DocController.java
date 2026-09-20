package net.zentao.doc.infra.web;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import net.zentao.doc.api.DocList;
import net.zentao.doc.api.DocVersionList;
import net.zentao.doc.api.DocVersionView;
import net.zentao.doc.api.DocView;
import net.zentao.doc.app.BatchDocHandler;
import net.zentao.doc.app.CreateDocHandler;
import net.zentao.doc.app.DeleteDocHandler;
import net.zentao.doc.app.DocAccess;
import net.zentao.doc.app.DocQueryService;
import net.zentao.doc.app.DocVersionQueryService;
import net.zentao.doc.app.MoveDocHandler;
import net.zentao.doc.app.PublishDocHandler;
import net.zentao.doc.app.SaveDocDraftHandler;
import net.zentao.doc.app.UpdateDocHandler;
import net.zentao.platform.activity.ActivityQueryService;
import net.zentao.platform.rbac.RequirePrivilege;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.session.SessionResolver;
import net.zentao.platform.web.BatchActionRequest;
import net.zentao.platform.web.BatchActionResult;
import net.zentao.platform.web.CommentRequest;
import net.zentao.platform.web.DataEnvelope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 文档端点（doc 卡 §5 docs 族 13 行，含库内列表/创建与版本、动态流）。 */
@RestController
@RequestMapping("/api/v1")
public class DocController {

  private final DocQueryService queryService;
  private final DocVersionQueryService versionQueryService;
  private final DocAccess access;
  private final CreateDocHandler createHandler;
  private final UpdateDocHandler updateHandler;
  private final SaveDocDraftHandler saveDraftHandler;
  private final PublishDocHandler publishHandler;
  private final MoveDocHandler moveHandler;
  private final DeleteDocHandler deleteHandler;
  private final BatchDocHandler batchHandler;
  private final ActivityQueryService activityQueryService;
  private final SessionResolver resolver;

  public DocController(DocQueryService queryService, DocVersionQueryService versionQueryService, DocAccess access,
      CreateDocHandler createHandler, UpdateDocHandler updateHandler, SaveDocDraftHandler saveDraftHandler,
      PublishDocHandler publishHandler, MoveDocHandler moveHandler, DeleteDocHandler deleteHandler,
      BatchDocHandler batchHandler, ActivityQueryService activityQueryService, SessionResolver resolver) {
    this.queryService = queryService;
    this.versionQueryService = versionQueryService;
    this.access = access;
    this.createHandler = createHandler;
    this.updateHandler = updateHandler;
    this.saveDraftHandler = saveDraftHandler;
    this.publishHandler = publishHandler;
    this.moveHandler = moveHandler;
    this.deleteHandler = deleteHandler;
    this.batchHandler = batchHandler;
    this.activityQueryService = activityQueryService;
    this.resolver = resolver;
  }

  @GetMapping("/doc-spaces/{docSpaceId}/docs")
  @Operation(operationId = "listSpaceDocs")
  @RequirePrivilege("doc-view")
  public DataEnvelope<DocList> listSpaceDocs(@PathVariable long docSpaceId, HttpServletRequest request) {
    return DataEnvelope.of(queryService.pageInSpace(docSpaceId, resolver.resolve(request),
        request.getParameterMap()));
  }

  @PostMapping("/doc-spaces/{docSpaceId}/docs")
  @Operation(operationId = "createDoc")
  @RequirePrivilege("doc-create")
  public DataEnvelope<DocView> create(@PathVariable long docSpaceId,
      @RequestBody CreateDocHandler.DocCreateRequest body, HttpServletRequest request) {
    SessionPrincipal principal = resolver.resolve(request);
    return DataEnvelope.of(queryService.viewOf(createHandler.handle(principal, docSpaceId, body), principal));
  }

  @GetMapping("/docs")
  @Operation(operationId = "listDocs")
  @RequirePrivilege("doc-view")
  public DataEnvelope<DocList> list(HttpServletRequest request) {
    return DataEnvelope.of(queryService.page(resolver.resolve(request), request.getParameterMap()));
  }

  @PostMapping("/docs/batch")
  @Operation(operationId = "batchDocs")
  public DataEnvelope<BatchActionResult> batch(@RequestBody BatchActionRequest body, HttpServletRequest request) {
    return DataEnvelope.of(batchHandler.handle(resolver.resolve(request), body));
  }

  @GetMapping("/docs/{docId}")
  @Operation(operationId = "getDoc")
  @RequirePrivilege("doc-view")
  public DataEnvelope<DocView> detail(@PathVariable long docId, HttpServletRequest request) {
    return DataEnvelope.of(queryService.detail(resolver.resolve(request), docId));
  }

  @PatchMapping("/docs/{docId}")
  @Operation(operationId = "updateDoc")
  @RequirePrivilege("doc-edit")
  public DataEnvelope<DocView> update(@PathVariable long docId,
      @RequestBody UpdateDocHandler.DocUpdateRequest body, HttpServletRequest request) {
    SessionPrincipal principal = resolver.resolve(request);
    return DataEnvelope.of(queryService.viewOf(updateHandler.handle(principal, docId, body), principal));
  }

  @PostMapping("/docs/{docId}/save-draft")
  @Operation(operationId = "saveDocDraft")
  @RequirePrivilege("doc-edit")
  public DataEnvelope<DocView> saveDraft(@PathVariable long docId,
      @RequestBody SaveDocDraftHandler.DocSaveDraftRequest body, HttpServletRequest request) {
    SessionPrincipal principal = resolver.resolve(request);
    return DataEnvelope.of(queryService.viewOf(saveDraftHandler.handle(principal, docId, body), principal));
  }

  @PostMapping("/docs/{docId}/publish")
  @Operation(operationId = "publishDoc")
  @RequirePrivilege("doc-edit")
  public DataEnvelope<DocView> publish(@PathVariable long docId,
      @RequestBody(required = false) CommentRequest body, HttpServletRequest request) {
    SessionPrincipal principal = resolver.resolve(request);
    return DataEnvelope.of(queryService.viewOf(
        publishHandler.handle(principal, docId, body == null ? null : body.comment()), principal));
  }

  @PostMapping("/docs/{docId}/move")
  @Operation(operationId = "moveDoc")
  @RequirePrivilege("doc-edit")
  public DataEnvelope<DocView> move(@PathVariable long docId, @RequestBody MoveDocHandler.DocMoveRequest body,
      HttpServletRequest request) {
    SessionPrincipal principal = resolver.resolve(request);
    return DataEnvelope.of(queryService.viewOf(moveHandler.handle(principal, docId, body), principal));
  }

  @PostMapping("/docs/{docId}/delete")
  @Operation(operationId = "deleteDoc")
  @RequirePrivilege("doc-delete")
  public DataEnvelope<Void> delete(@PathVariable long docId,
      @RequestBody(required = false) CommentRequest body, HttpServletRequest request) {
    deleteHandler.handle(resolver.resolve(request), docId, body == null ? null : body.comment());
    return DataEnvelope.empty();
  }

  @GetMapping("/docs/{docId}/versions")
  @Operation(operationId = "listDocVersions")
  @RequirePrivilege("doc-view")
  public DataEnvelope<DocVersionList> versions(@PathVariable long docId, HttpServletRequest request) {
    return DataEnvelope.of(versionQueryService.list(resolver.resolve(request), docId));
  }

  @GetMapping("/docs/{docId}/versions/{version}")
  @Operation(operationId = "getDocVersion")
  @RequirePrivilege("doc-view")
  public DataEnvelope<DocVersionView> version(@PathVariable long docId, @PathVariable int version,
      HttpServletRequest request) {
    return DataEnvelope.of(versionQueryService.get(resolver.resolve(request), docId, version));
  }

  @GetMapping("/docs/{docId}/activities")
  @Operation(operationId = "listDocActivities")
  @RequirePrivilege("doc-view")
  public DataEnvelope<ActivityQueryService.ActivityList> activities(@PathVariable long docId,
      @RequestParam(required = false) Integer limit, @RequestParam(required = false) Long beforeId,
      HttpServletRequest request) {
    access.requireReadableDoc(resolver.resolve(request), docId);
    return DataEnvelope.of(activityQueryService.list("doc", docId, null, limit, beforeId));
  }
}
