package net.zentao.task.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** 任务仓储（domain 接口；实现 infra，A1：不泄漏 ORM 类型）。 */
public interface TaskRepository {

  Optional<Task> findActiveById(long id);

  List<Task> findActiveByIds(List<Long> ids);

  /** 未删子任务（父子联动与 is_parent 维护用）。 */
  List<Task> findActiveChildren(long parentId);

  /** 某需求的全部未删任务（需求 stage 联动信号，§4）。 */
  List<Task> findActiveByStory(long storyId);

  /** 执行下全部未删任务（燃尽逐任务行取数）。 */
  List<Task> findActiveByExecution(long executionId);

  /** 项目下全部未删任务（周报 EVM 统计范围，workspace 卡 §3.2）。 */
  List<Task> findActiveByProject(long projectId);

  /** 子任务计数（is_parent 复位判定，避免全量加载）。 */
  long countActiveChildren(long parentId);

  Task insert(Task task);

  Optional<Task> update(Task task);

  /** 软删（deleted_at 置位，A-07）。 */
  void softDelete(long id);

  List<Task> queryPage(Object whereWrapper, int offset, int limit);

  long countByQuery(Object whereWrapper);
}
