package net.zentao.project.infra.web;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import net.zentao.platform.audit.Audit;
import net.zentao.platform.audit.AuditDiff;
import net.zentao.platform.rbac.RequirePrivilege;
import net.zentao.platform.session.SessionResolver;
import net.zentao.platform.web.DataEnvelope;
import net.zentao.project.app.ManageStageHandler;
import net.zentao.project.app.ManageStageHandler.StageCreateRequest;
import net.zentao.project.app.ManageStageHandler.StageUpdateRequest;
import net.zentao.project.app.ManageStageHandler.StageView;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 阶段类型字典端点（project 卡 §5 stages 族）。 */
@RestController
@RequestMapping("/api/v1")
public class StageController {

  private final ManageStageHandler handler;
  private final SessionResolver resolver;

  public StageController(ManageStageHandler handler, SessionResolver resolver) {
    this.handler = handler;
    this.resolver = resolver;
  }

  @GetMapping("/stages")
  @Operation(operationId = "listStages")
  @RequirePrivilege("stage-view")
  public DataEnvelope<ManageStageHandler.StageList> list(HttpServletRequest request) {
    return DataEnvelope.of(handler.page(resolver.resolve(request), request.getParameterMap()));
  }

  @PostMapping("/stages")
  @Operation(operationId = "createStage")
  @RequirePrivilege("stage-manage")
  @Audit(action = "stage-create", objectType = "stage")
  public DataEnvelope<StageView> create(@RequestBody StageCreateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(handler.create(resolver.resolve(request), body));
  }

  @PatchMapping("/stages/{stageId}")
  @Operation(operationId = "updateStage")
  @RequirePrivilege("stage-manage")
  @Audit(action = "stage-update", objectType = "stage")
  @AuditDiff(objectType = "stage")
  public DataEnvelope<StageView> update(@PathVariable long stageId, @RequestBody StageUpdateRequest body,
      HttpServletRequest request) {
    return DataEnvelope.of(handler.update(resolver.resolve(request), stageId, body));
  }

  @DeleteMapping("/stages/{stageId}")
  @Operation(operationId = "deleteStage")
  @RequirePrivilege("stage-manage")
  @Audit(action = "stage-delete", objectType = "stage")
  @AuditDiff(objectType = "stage")
  public DataEnvelope<Void> delete(@PathVariable long stageId, HttpServletRequest request) {
    handler.delete(resolver.resolve(request), stageId);
    return DataEnvelope.empty();
  }
}
