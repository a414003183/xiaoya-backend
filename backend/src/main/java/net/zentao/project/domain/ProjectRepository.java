package net.zentao.project.domain;

import java.util.List;
import java.util.Optional;

/** 项目仓储（domain 接口；实现 infra，A1：接口不泄漏 ORM 类型）。 */
public interface ProjectRepository {

  Optional<Project> findActiveById(long id);

  List<Project> findActiveByIds(List<Long> ids);

  /** 全部未删项目（含白名单装配）：可见性判定与树装配的数据源（project 卡 §7）。 */
  List<Project> findAllActive();

  Project insert(Project project);

  Optional<Project> update(Project project);

  /** path/grade 回填（创建后自增 id 才可知，走免乐观锁路径）。 */
  void updatePath(long id, String path, int grade);

  List<Project> queryPage(Object whereWrapper, int offset, int limit);

  long countByQuery(Object whereWrapper);

  /** 未删子行数（删守卫：program 的子 program/project、project 的执行都经 parent_id 指向）。 */
  long countActiveChildren(long parentId);

  /** 软删（A-07）：置 deleted_at。 */
  void softDelete(long id);
}
