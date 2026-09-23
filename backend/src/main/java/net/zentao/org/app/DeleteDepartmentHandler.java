package net.zentao.org.app;

import net.zentao.org.domain.Department;
import net.zentao.org.domain.DepartmentRepository;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 删除部门（org 卡 §2/§5：真实删除；有子部门或有成员 → 42203）。 */
@Component
public class DeleteDepartmentHandler {

  private final DepartmentRepository repository;

  public DeleteDepartmentHandler(DepartmentRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public void handle(long departmentId) {
    Department department = repository.findById(departmentId)
        .orElseThrow(() -> ApiException.notFound("entity.department"));
    if (repository.existsByParentId(departmentId)) {
      throw ApiException.keyed(ErrorCode.GUARD_NOT_SATISFIED, "department.guard.hasChildren");
    }
    if (repository.hasMembers(departmentId)) {
      throw ApiException.keyed(ErrorCode.GUARD_NOT_SATISFIED, "department.guard.hasMembers");
    }
    repository.delete(department.id());
  }
}
