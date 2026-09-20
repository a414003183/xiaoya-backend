package net.zentao.org.app;

import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.query.QueryColumn;
import java.util.Map;
import net.zentao.org.domain.Department;
import net.zentao.org.domain.DepartmentRepository;
import net.zentao.platform.error.ApiException;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 新建部门（org 卡 §5：name 必填 1–60；parentId 存在；manager 为存在账号）。 */
@Component
public class CreateDepartmentHandler {

  private final DepartmentRepository repository;

  public CreateDepartmentHandler(DepartmentRepository repository) {
    this.repository = repository;
  }

  public record DepartmentCreateRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name, Long parentId, Integer sort, String manager) {}

  @Transactional
  public Department handle(DepartmentCreateRequest command) {
    requireName(command.name());
    String parentPath = ",";
    int parentGrade = 0;
    if (command.parentId() != null) {
      Department parent = repository.findById(command.parentId())
          .orElseThrow(() -> ApiException.validation(Map.of("parentId", "notFound")));
      parentPath = parent.path();
      parentGrade = parent.grade();
    }
    requireManager(command.manager());
    Department department = Department.create(command.name(), command.parentId(), parentPath, parentGrade,
        command.sort() == null ? 0 : command.sort(), command.manager());
    // path 需含自身 id，先插入拿到 id 再回填
    Department inserted = repository.insert(new Department(0, department.name(), department.parentId(),
        department.path(), department.grade(), department.sort(), department.manager(), 0));
    Department fixed = new Department(inserted.id(), inserted.name(), inserted.parentId(),
        parentPath + inserted.id() + ",", inserted.grade(), inserted.sort(), inserted.manager(), 0);
    repository.updatePath(fixed.id(), fixed.path(), fixed.grade());
    return fixed;
  }

  static void requireName(String name) {
    if (name == null || name.isBlank() || name.length() > 60) {
      throw ApiException.validation(Map.of("name", "size"));
    }
  }

  static void requireManager(String manager) {
    if (manager != null && !manager.isBlank()) {
      boolean exists = Db.selectCountByCondition("account",
          new QueryColumn("account").eq(manager)) > 0;
      if (!exists) {
        throw ApiException.validation(Map.of("manager", "notFound"));
      }
    }
  }
}
