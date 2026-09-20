package net.zentao.org.app;

import net.zentao.org.domain.Department;
import net.zentao.org.domain.DepartmentRepository;
import net.zentao.platform.error.ApiException;
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
        .orElseThrow(() -> ApiException.notFound("部门"));
    if (repository.existsByParentId(departmentId)) {
      throw ApiException.guardNotSatisfied("存在子部门，不能删除。");
    }
    if (repository.hasMembers(departmentId)) {
      throw ApiException.guardNotSatisfied("部门下存在成员，不能删除。");
    }
    repository.delete(department.id());
  }
}
