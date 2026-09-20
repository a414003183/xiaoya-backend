package net.zentao.task.domain;

import java.util.List;
import java.util.Optional;

/** 工时仓储（domain 接口；软删 + 未删合计回算，task 卡 §4）。 */
public interface EffortRepository {

  Optional<Effort> findActiveById(long id);

  List<Effort> findActiveByTask(long taskId);

  /** 项目下全部未删工时（周报 EVM 的 ac 与本周人力/workload 口径，workspace 卡 §3.2）。 */
  List<Effort> findActiveByProject(long projectId);

  Effort insert(Effort effort);

  Optional<Effort> update(Effort effort);

  void softDelete(long id);

  List<Effort> queryPage(Object whereWrapper, int offset, int limit);

  long countByQuery(Object whereWrapper);
}
