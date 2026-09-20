package net.zentao.quality.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 测试单仓储。 */
public interface TestRunRepository {

  Optional<TestRun> findActiveById(long id);

  List<TestRun> findActiveByIds(List<Long> ids);

  List<TestRun> findActiveByExecution(long executionId);

  List<TestRun> queryPage(Object whereWrapper, int offset, int limit);

  long countByQuery(Object whereWrapper);

  TestRun insert(TestRun testRun);

  /** 全量覆盖（含 null）；乐观锁不符返回空。 */
  Optional<TestRun> update(TestRun testRun);

  /** 软删单行（deleted_at 置位，A-07；test_run_case 历史行与已回填 reportId 保留）。 */
  void softDelete(long id, String actor, Instant at);
}
