package net.zentao.quality.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 用例仓储（含 case_step 子表整体替换写；queryPage 以 Object 承载 wrapper 防 A1 泄漏）。 */
public interface TestCaseRepository {

  Optional<TestCase> findActiveById(long id);

  List<TestCase> findActiveByIds(List<Long> ids);

  /** 批量取步骤（键 = caseId），详情/导入复制用。 */
  Map<Long, List<TestCase.Step>> findSteps(List<Long> caseIds);

  List<TestCase> queryPage(Object whereWrapper, int offset, int limit);

  long countByQuery(Object whereWrapper);

  TestCase insert(TestCase testCase);

  /** 全量覆盖主表 + steps 整体替换（旧行全删，新行按 sort 落）；乐观锁不符返回空。 */
  Optional<TestCase> update(TestCase testCase);

  /** lastRun 三字段批量同步（record-result 副作用；不走乐观锁）。 */
  int updateLastRun(List<Long> caseIds, String result, String runner, Instant runAt);

  /** 库内未删用例计数（library 删除守卫，A-07）。 */
  long countActiveInLibrary(long libraryId);

  /** 软删单行（deleted_at 置位，A-07；case_step/test_run_case 历史行保留）。 */
  void softDelete(long id, String actor, Instant at);
}
