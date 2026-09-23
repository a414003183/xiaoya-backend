package net.zentao.task.infra.web;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import net.zentao.platform.audit.Audit;
import net.zentao.platform.audit.AuditDiff;
import net.zentao.platform.rbac.RequirePrivilege;
import net.zentao.platform.session.SessionResolver;
import net.zentao.platform.web.DataEnvelope;
import net.zentao.task.api.EffortView;
import net.zentao.task.app.DeleteEffortHandler;
import net.zentao.task.app.EditEffortHandler;
import net.zentao.task.app.EditEffortHandler.EffortUpdateRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 工时端点（task 卡 §5：编辑/删除，仅本人或超管）。 */
@RestController
@RequestMapping("/api/v1")
public class EffortController {

  private final EditEffortHandler editHandler;
  private final DeleteEffortHandler deleteHandler;
  private final SessionResolver resolver;

  public EffortController(EditEffortHandler editHandler, DeleteEffortHandler deleteHandler,
      SessionResolver resolver) {
    this.editHandler = editHandler;
    this.deleteHandler = deleteHandler;
    this.resolver = resolver;
  }

  @PatchMapping("/efforts/{effortId}")
  @Operation(operationId = "updateEffort")
  @RequirePrivilege("task-effort-edit")
  @Audit(action = "effort-update", objectType = "effort")
  @AuditDiff(objectType = "effort")
  public DataEnvelope<EffortView> update(@PathVariable long effortId, @RequestBody EffortUpdateRequest body,
      HttpServletRequest request) {
    return DataEnvelope.of(editHandler.handle(resolver.resolve(request), effortId, body));
  }

  @DeleteMapping("/efforts/{effortId}")
  @Operation(operationId = "deleteEffort")
  @RequirePrivilege("task-effort-delete")
  @Audit(action = "effort-delete", objectType = "effort")
  @AuditDiff(objectType = "effort")
  public DataEnvelope<Void> delete(@PathVariable long effortId, HttpServletRequest request) {
    deleteHandler.handle(resolver.resolve(request), effortId);
    return DataEnvelope.empty();
  }
}
