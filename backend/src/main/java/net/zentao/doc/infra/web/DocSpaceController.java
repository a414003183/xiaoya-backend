package net.zentao.doc.infra.web;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import net.zentao.doc.api.DocSpaceList;
import net.zentao.doc.api.DocSpaceView;
import net.zentao.doc.app.CreateDocSpaceHandler;
import net.zentao.doc.app.DeleteDocSpaceHandler;
import net.zentao.doc.app.DocAccess;
import net.zentao.doc.app.DocSpaceQueryService;
import net.zentao.doc.app.UpdateDocSpaceHandler;
import net.zentao.platform.rbac.RequirePrivilege;
import net.zentao.platform.session.SessionResolver;
import net.zentao.platform.web.CommentRequest;
import net.zentao.platform.web.DataEnvelope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 文档库端点（doc 卡 §5 doc-spaces 族 5 行）。 */
@RestController
@RequestMapping("/api/v1")
public class DocSpaceController {

  private final DocSpaceQueryService queryService;
  private final DocAccess access;
  private final CreateDocSpaceHandler createHandler;
  private final UpdateDocSpaceHandler updateHandler;
  private final DeleteDocSpaceHandler deleteHandler;
  private final SessionResolver resolver;

  public DocSpaceController(DocSpaceQueryService queryService, DocAccess access, CreateDocSpaceHandler createHandler,
      UpdateDocSpaceHandler updateHandler, DeleteDocSpaceHandler deleteHandler, SessionResolver resolver) {
    this.queryService = queryService;
    this.access = access;
    this.createHandler = createHandler;
    this.updateHandler = updateHandler;
    this.deleteHandler = deleteHandler;
    this.resolver = resolver;
  }

  @GetMapping("/doc-spaces")
  @Operation(operationId = "listDocSpaces")
  @RequirePrivilege("doc-space-view")
  public DataEnvelope<DocSpaceList> list(HttpServletRequest request) {
    return DataEnvelope.of(queryService.page(resolver.resolve(request), request.getParameterMap()));
  }

  @PostMapping("/doc-spaces")
  @Operation(operationId = "createDocSpace")
  @RequirePrivilege("doc-space-create")
  public DataEnvelope<DocSpaceView> create(@RequestBody CreateDocSpaceHandler.DocSpaceCreateRequest body,
      HttpServletRequest request) {
    return DataEnvelope.of(queryService.viewOf(createHandler.handle(resolver.resolve(request), body)));
  }

  @GetMapping("/doc-spaces/{docSpaceId}")
  @Operation(operationId = "getDocSpace")
  @RequirePrivilege("doc-space-view")
  public DataEnvelope<DocSpaceView> detail(@PathVariable long docSpaceId, HttpServletRequest request) {
    return DataEnvelope.of(queryService.viewOf(access.requireSpace(resolver.resolve(request), docSpaceId)));
  }

  @PatchMapping("/doc-spaces/{docSpaceId}")
  @Operation(operationId = "updateDocSpace")
  @RequirePrivilege("doc-space-edit")
  public DataEnvelope<DocSpaceView> update(@PathVariable long docSpaceId,
      @RequestBody UpdateDocSpaceHandler.DocSpaceUpdateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(queryService.viewOf(updateHandler.handle(resolver.resolve(request), docSpaceId, body)));
  }

  @PostMapping("/doc-spaces/{docSpaceId}/delete")
  @Operation(operationId = "deleteDocSpace")
  @RequirePrivilege("doc-space-delete")
  public DataEnvelope<Void> delete(@PathVariable long docSpaceId,
      @RequestBody(required = false) CommentRequest body, HttpServletRequest request) {
    deleteHandler.handle(resolver.resolve(request), docSpaceId);
    return DataEnvelope.empty();
  }
}
