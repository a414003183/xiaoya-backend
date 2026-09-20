package net.zentao.org.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.zentao.org.domain.Department;
import net.zentao.org.domain.DepartmentRepository;
import net.zentao.platform.error.ApiException;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 整树保存（org 卡 §5）：无 id 节点新增 + 改名/移动/排序；删除走单条 DELETE。
 * 全树重算 path/grade；parentId 成环 → 42203。
 */
@Component
public class SaveDepartmentTreeHandler {

  private final DepartmentRepository repository;

  public SaveDepartmentTreeHandler(DepartmentRepository repository) {
    this.repository = repository;
  }

  public record DepartmentTreeNode(Long id, Long parentId, String name, Integer sort, String manager) {}

  public record DepartmentTreeSaveRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<DepartmentTreeNode> nodes) {}

  @Transactional
  public List<Department> handle(DepartmentTreeSaveRequest request) {
    Map<Long, Department> updated = new HashMap<>();
    for (DepartmentTreeNode node : request.nodes()) {
      if (node.id() != null) {
        Department department = repository.findById(node.id())
            .orElseThrow(() -> ApiException.validation(Map.of("id", "notFound")));
        CreateDepartmentHandler.requireName(node.name());
        department.rename(node.name());
        department.changeSort(node.sort() == null ? department.sort() : node.sort());
        if (node.manager() != null) {
          department.changeManager(node.manager());
        }
        if (node.parentId() != null && node.parentId() != department.parentId()) {
          requireNotSelfOrDescendant(repository.findAll(), department.id(), node.parentId());
          department.moveTo(node.parentId(), parentPath(repository.findAll(), node.parentId()),
              parentGrade(repository.findAll(), node.parentId()));
        }
        repository.save(department);
        updated.put(department.id(), department);
      } else {
        CreateDepartmentHandler.requireName(node.name());
        String parentPath = ",";
        int parentGrade = 0;
        if (node.parentId() != null) {
          parentPath = parentPath(repository.findAll(), node.parentId());
          parentGrade = parentGrade(repository.findAll(), node.parentId());
        }
        Department created = repository.insert(Department.create(node.name(), node.parentId(), parentPath,
            parentGrade, node.sort() == null ? 0 : node.sort(), node.manager()));
        repository.save(new Department(created.id(), created.name(), created.parentId(),
            parentPath + created.id() + ",", created.grade(), created.sort(), created.manager(), 0));
      }
    }
    recomputePaths();
    return repository.findAll();
  }

  /** 从根 DFS 重算 path/grade；检测到环 → 42203。 */
  private void recomputePaths() {
    List<Department> all = repository.findAll();
    Map<Long, Department> byId = new HashMap<>();
    for (Department department : all) {
      byId.put(department.id(), department);
    }
    for (Department department : all) {
      if (department.parentId() != null && !byId.containsKey(department.parentId())) {
        throw ApiException.validation(Map.of("parentId", "notFound"));
      }
    }
    for (Department root : all) {
      if (root.parentId() == null) {
        visit(root, ",", 0, byId, new ArrayList<>());
      }
    }
  }

  private void visit(Department department, String parentPath, int parentGrade, Map<Long, Department> byId,
      List<Long> visiting) {
    if (visiting.contains(department.id())) {
      throw ApiException.guardNotSatisfied("部门树存在环。");
    }
    visiting.add(department.id());
    department.relocate(parentPath + department.id() + ",", parentGrade + 1);
    repository.save(department);
    for (Department child : byId.values()) {
      if (department.id() == (child.parentId() == null ? -1 : child.parentId())) {
        visit(child, department.path(), department.grade(), byId, visiting);
      }
    }
    visiting.remove(visiting.size() - 1);
  }

  private void requireNotSelfOrDescendant(List<Department> all, long departmentId, Long newParentId) {
    for (Department department : all) {
      if (department.id() == departmentId) {
        if (newParentId == departmentId || department.isSelfOrDescendant(newParentId)) {
          throw ApiException.guardNotSatisfied("不能移动到自身或其后代部门。");
        }
      }
    }
  }

  private String parentPath(List<Department> all, Long parentId) {
    return all.stream()
        .filter(department -> department.id() == parentId)
        .findFirst()
        .map(Department::path)
        .orElseThrow(() -> ApiException.validation(Map.of("parentId", "notFound")));
  }

  private int parentGrade(List<Department> all, Long parentId) {
    return all.stream()
        .filter(department -> department.id() == parentId)
        .findFirst()
        .map(Department::grade)
        .orElseThrow(() -> ApiException.validation(Map.of("parentId", "notFound")));
  }
}
