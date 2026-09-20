package net.zentao.workspace.domain;

import java.util.List;
import java.util.Optional;

/** 待办仓储（domain 接口；实现 infra，A1：不泄漏 ORM 类型）。 */
public interface TodoRepository {

  Optional<Todo> findActiveById(long id);

  List<Todo> findActiveByIds(List<Long> ids);

  Todo insert(Todo todo);

  /** 全量覆盖（含 null 字段——activate 清四列必须落库），写后回读数据库行（决策⑺：lockVersion 必须回读）。 */
  Optional<Todo> update(Todo todo);

  List<Todo> queryPage(Object whereWrapper, int offset, int limit);

  long countByQuery(Object whereWrapper);

  /** 软删（A-07）：置 deleted_at。 */
  void softDelete(long id);
}
