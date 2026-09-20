package net.zentao.doc.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import net.zentao.doc.domain.Doc;
import net.zentao.doc.domain.DocRepository;
import net.zentao.doc.domain.DocSpace;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.workflow.WorkflowEngine;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 移动文档（doc 卡 §4/§5）：目标库可见（不可见 → 40302）；联动冗余 productId/projectId/executionId；
 * parentId 不可选自身/后代（成环 → 42201）且必须同目标库；path 连同子树整体重建；动态流 moved。
 */
@Component
public class MoveDocHandler {

  public record DocMoveRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long docSpaceId,
      Long categoryId, Long parentId) {}

  private final DocRepository repository;
  private final DocAccess access;
  private final WorkflowEngine engine;

  public MoveDocHandler(DocRepository repository, DocAccess access, WorkflowEngine engine) {
    this.repository = repository;
    this.access = access;
    this.engine = engine;
  }

  @Transactional
  public Doc handle(SessionPrincipal actor, long docId, DocMoveRequest command) {
    Doc doc = access.requireEditableDoc(actor, docId);
    if (command.docSpaceId() == null) {
      throw ApiException.validation(Map.of("docSpaceId", "required"));
    }
    DocSpace target = access.requireSpace(actor, command.docSpaceId());
    Map<String, String> errors = DocFields.errors();
    long parentId = command.parentId() == null ? 0 : command.parentId();
    Doc parent = null;
    if (parentId != 0) {
      if (parentId == docId || repository.existsInSubtree(docId, doc.path(), parentId)) {
        errors.put("parentId", "cycle");
      } else {
        parent = repository.findActiveById(parentId).orElse(null);
        if (parent == null || parent.docSpaceId() != target.id()) {
          errors.put("parentId", "invalid");
        }
      }
    }
    DocFields.reject(errors);

    String oldPath = doc.path();
    String newPath = parent == null ? "," + docId + "," : parent.path() + docId + ",";
    doc.moveTo(target.id(), command.categoryId() == null ? 0 : command.categoryId(), parentId,
        target.productId(), target.projectId(), target.executionId());
    doc.markUpdatedBy(actor.account());
    Doc saved = repository.update(doc)
        .orElseThrow(() -> ApiException.lockConflict("数据已被他人修改，请刷新后重试。"));
    repository.replacePathPrefix(oldPath, newPath);
    saved.applyPath(newPath);
    engine.fire(new DocTarget(saved, null, false, actor.account()), "move", null);
    return saved;
  }
}
