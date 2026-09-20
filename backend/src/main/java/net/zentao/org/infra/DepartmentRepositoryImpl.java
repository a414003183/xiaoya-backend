package net.zentao.org.infra;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.row.Row;
import java.util.List;
import java.util.Optional;
import net.zentao.org.domain.Department;
import net.zentao.org.domain.DepartmentRepository;
import org.springframework.stereotype.Component;

/** 部门仓储实现（infra：PO ↔ 领域对象转换）。 */
@Component
public class DepartmentRepositoryImpl implements DepartmentRepository {

  private final DepartmentMapper mapper;

  public DepartmentRepositoryImpl(DepartmentMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<Department> findById(long id) {
    return Optional.ofNullable(mapper.selectOneById(id)).map(DepartmentRepositoryImpl::toDomain);
  }

  @Override
  public List<Department> findAll() {
    return mapper.selectAll().stream().map(DepartmentRepositoryImpl::toDomain).toList();
  }

  @Override
  public Optional<Department> save(Department department) {
    DepartmentPO po = toPo(department);
    int updated = mapper.update(po);
    return updated > 0 ? Optional.of(department) : Optional.empty();
  }

  @Override
  public Department insert(Department department) {
    DepartmentPO po = new DepartmentPO();
    po.setName(department.name());
    po.setParentId(department.parentId());
    po.setPath(department.path());
    po.setGrade(department.grade());
    po.setSort(department.sort());
    po.setManager(department.manager());
    po.setLockVersion(0);
    mapper.insert(po);
    return new Department(po.getId(), po.getName(), po.getParentId(), po.getPath(), po.getGrade(),
        po.getSort(), po.getManager(), po.getLockVersion());
  }

  @Override
  public void updatePath(long id, String path, int grade) {
    Db.updateByCondition("department", Row.of("path", path).set("grade", grade),
        new QueryColumn("id").eq(id));
  }

  @Override
  public void delete(long id) {
    mapper.deleteById(id);
  }

  @Override
  public boolean existsByParentId(long parentId) {
    return mapper.selectCountByQuery(QueryWrapper.create()
        .where(new QueryColumn("parent_id").eq(parentId))) > 0;
  }

  @Override
  public boolean hasMembers(long departmentId) {
    return Db.selectCountByCondition("account", new QueryColumn("department_id").eq(departmentId)) > 0;
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<Department> queryPage(Object whereWrapper, int offset, int limit) {
    return mapper.selectListByQuery(((QueryWrapper) whereWrapper).limit(offset, limit)).stream()
        .map(DepartmentRepositoryImpl::toDomain)
        .toList();
  }

  @Override
  public long countByQuery(Object whereWrapper) {
    return mapper.selectCountByQuery((QueryWrapper) whereWrapper);
  }

  private static Department toDomain(DepartmentPO po) {
    return new Department(po.getId(), po.getName(), po.getParentId(), po.getPath(),
        po.getGrade() == null ? 1 : po.getGrade(), po.getSort() == null ? 0 : po.getSort(),
        po.getManager(), po.getLockVersion() == null ? 0 : po.getLockVersion());
  }

  private static DepartmentPO toPo(Department department) {
    DepartmentPO po = new DepartmentPO();
    po.setId(department.id());
    po.setName(department.name());
    po.setParentId(department.parentId());
    po.setPath(department.path());
    po.setGrade(department.grade());
    po.setSort(department.sort());
    po.setManager(department.manager());
    po.setLockVersion(department.lockVersion());
    return po;
  }
}
