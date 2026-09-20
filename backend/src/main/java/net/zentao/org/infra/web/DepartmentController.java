package net.zentao.org.infra.web;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import net.zentao.org.api.DepartmentNode;
import net.zentao.org.app.CreateDepartmentHandler;
import net.zentao.org.app.DeleteDepartmentHandler;
import net.zentao.org.app.DepartmentQueryService;
import net.zentao.org.app.SaveDepartmentTreeHandler;
import net.zentao.org.app.UpdateDepartmentHandler;
import net.zentao.org.domain.Department;
import net.zentao.platform.rbac.RequirePrivilege;
import net.zentao.platform.web.DataEnvelope;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 部门五端点（org 卡 §5；权限码 department-*）。 */
@RestController
@RequestMapping("/api/v1")
public class DepartmentController {

  private final DepartmentQueryService queryService;
  private final CreateDepartmentHandler createHandler;
  private final UpdateDepartmentHandler updateHandler;
  private final SaveDepartmentTreeHandler saveTreeHandler;
  private final DeleteDepartmentHandler deleteHandler;

  public DepartmentController(DepartmentQueryService queryService, CreateDepartmentHandler createHandler,
      UpdateDepartmentHandler updateHandler, SaveDepartmentTreeHandler saveTreeHandler,
      DeleteDepartmentHandler deleteHandler) {
    this.queryService = queryService;
    this.createHandler = createHandler;
    this.updateHandler = updateHandler;
    this.saveTreeHandler = saveTreeHandler;
    this.deleteHandler = deleteHandler;
  }

  @GetMapping("/departments")
  @Operation(operationId = "listDepartments")
  @RequirePrivilege("department-view")
  public DataEnvelope<DepartmentQueryService.DepartmentList> list(HttpServletRequest request) {
    return DataEnvelope.of(queryService.page(request.getParameterMap()));
  }

  @GetMapping("/departments/tree")
  @Operation(operationId = "getDepartmentTree")
  public DataEnvelope<DepartmentTree> getTree() {
    return DataEnvelope.of(new DepartmentTree(queryService.tree()));
  }

  @PostMapping("/departments")
  @Operation(operationId = "createDepartment")
  @RequirePrivilege("department-create")
  public DataEnvelope<DepartmentNode> create(@RequestBody CreateDepartmentHandler.DepartmentCreateRequest body) {
    return DataEnvelope.of(toNode(createHandler.handle(body)));
  }

  @PatchMapping("/departments/{departmentId}")
  @Operation(operationId = "updateDepartment")
  @RequirePrivilege("department-edit")
  public DataEnvelope<DepartmentNode> update(@PathVariable long departmentId,
      @RequestBody UpdateDepartmentHandler.DepartmentUpdateRequest body) {
    return DataEnvelope.of(toNode(updateHandler.handle(departmentId, body)));
  }

  @PutMapping("/departments/tree")
  @Operation(operationId = "saveDepartmentTree")
  @RequirePrivilege("department-edit")
  public DataEnvelope<DepartmentTree> saveTree(@RequestBody SaveDepartmentTreeHandler.DepartmentTreeSaveRequest body) {
    saveTreeHandler.handle(body);
    return DataEnvelope.of(new DepartmentTree(queryService.tree()));
  }

  @DeleteMapping("/departments/{departmentId}")
  @Operation(operationId = "deleteDepartment")
  @RequirePrivilege("department-delete")
  public DataEnvelope<Void> delete(@PathVariable long departmentId) {
    deleteHandler.handle(departmentId);
    return DataEnvelope.empty();
  }

  static DepartmentNode toNode(Department department) {
    return DepartmentNode.flat(department.id(), department.name(), department.parentId(), null, department.path(),
        department.grade(), department.sort(), department.manager());
  }

  public record DepartmentTree(List<DepartmentNode> items) {}
}
