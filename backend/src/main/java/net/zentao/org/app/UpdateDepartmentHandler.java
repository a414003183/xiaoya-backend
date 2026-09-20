package net.zentao.org.app;

import java.util.List;
import java.util.Map;
import net.zentao.org.domain.Department;
import net.zentao.org.domain.DepartmentRepository;
import net.zentao.platform.error.ApiException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 部门更新（org 卡 §3.2/§5）：重命名/移动/排序/负责人。
 * 移动环检测：parentId 为自身或后代 → 42203；子树 path/grade 级联重算；lockVersion 不符 → 40901。
 */
@Component
public class UpdateDepartmentHandler {

  private final DepartmentRepository repository;

  public UpdateDepartmentHandler(DepartmentRepository repository) {
    this.repository = repository;
  }

  public record DepartmentUpdateRequest(String name, Long parentId, Integer sort, String manager, Integer lockVersion) {}

  @Transactional
  public Department handle(long departmentId, DepartmentUpdateRequest command) {
    Department department = repository.findById(departmentId)
        .orElseThrow(() -> ApiException.notFound("部门"));
    if (command.lockVersion() != null && command.lockVersion() != department.lockVersion()) {
      throw ApiException.lockConflict("数据已被他人修改，请刷新。");
    }
    if (command.name() != null) {
      CreateDepartmentHandler.requireName(command.name());
      department.rename(command.name());
    }
    if (command.sort() != null) {
      department.changeSort(command.sort());
    }
    if (command.manager() != null) {
      CreateDepartmentHandler.requireManager(command.manager());
      department.changeManager(command.manager());
    }
    if (command.parentId() != null && command.parentId() != department.parentId()) {
      moveTo(department, command.parentId());
    }
    return repository.save(department).orElseThrow(() -> ApiException.lockConflict("数据已被他人修改，请刷新。"));
  }

  private void moveTo(Department department, Long newParentId) {
    Department newParent = repository.findById(newParentId)
        .orElseThrow(() -> ApiException.validation(Map.of("parentId", "notFound")));
    if (newParent.id() == department.id() || newParent.isSelfOrDescendant(department.id())) {
      throw ApiException.guardNotSatisfied("不能移动到自身或其后代部门。");
    }
    int oldGrade = department.grade();
    String oldPath = department.path();
    department.moveTo(newParent.id(), newParent.path(), newParent.grade());
    // 子树级联：path 以旧 path 为前缀的后代重算
    int gradeDelta = department.grade() - oldGrade;
    List<Department> all = repository.findAll();
    for (Department descendant : all) {
      if (!descendant.path().equals(oldPath) && descendant.path().startsWith(oldPath)) {
        String newPath = department.path() + descendant.path().substring(oldPath.length());
        descendant.relocate(newPath, descendant.grade() + gradeDelta);
        repository.save(descendant);
      }
    }
  }
}
