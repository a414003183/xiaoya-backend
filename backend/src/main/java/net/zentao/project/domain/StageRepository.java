package net.zentao.project.domain;

import java.util.List;
import java.util.Optional;

/** 阶段类型字典仓储（domain 接口；实现 infra，A1：接口不泄漏 ORM 类型）。 */
public interface StageRepository {

  Optional<Stage> findActiveById(long id);

  /** 全部未删阶段（percent 累计校验与列表的数据源，字典表小集合）。 */
  List<Stage> findAllActive();

  Stage insert(Stage stage);

  /** 全量写回（stage 无乐观锁列，StageUpdateRequest 亦无 lockVersion）。 */
  void update(Stage stage);

  void softDelete(long id);

  List<Stage> queryPage(Object whereWrapper, int offset, int limit);

  long countByQuery(Object whereWrapper);
}
