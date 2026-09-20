package net.zentao.org.domain;

import java.util.List;
import java.util.Optional;

/** 部门仓储接口（domain 层，纯 Java；实现于 infra）。 */
public interface DepartmentRepository {

  Optional<Department> findById(long id);

  List<Department> findAll();

  /** 新增或更新（更新走乐观锁，版本不符返回空 → 40901）。 */
  Optional<Department> save(Department department);

  /** 新增（生成 id 并回填 path）。 */
  Department insert(Department department);

  /** 仅回写 path/grade（创建时的 id 回填，不参与乐观锁）。 */
  void updatePath(long id, String path, int grade);

  void delete(long id);

  boolean existsByParentId(long parentId);

  boolean hasMembers(long departmentId);

  /**
   * 分页查询（DSL 编译产物；平铺列表端点用）。ponytail: 参数以 Object 承载（实现层强转），
   * 与 AccountRepository.queryPage 同口径，避免 domain 接口泄漏 ORM 类型。
   */
  List<Department> queryPage(Object whereWrapper, int offset, int limit);

  long countByQuery(Object whereWrapper);
}
