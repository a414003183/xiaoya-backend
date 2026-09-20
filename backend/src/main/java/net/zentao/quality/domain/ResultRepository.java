package net.zentao.quality.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 执行结果仓储（test_run_case；UNIQUE(testRunId, testCaseId) 幂等 upsert）。 */
public interface ResultRepository {

  Optional<Result> find(long testRunId, long testCaseId);

  /** 测试单下全部执行结果（用例通过率报表取数，workspace 卡 §5）。 */
  List<Result> findByTestRun(long testRunId);

  List<Result> queryPage(Object whereWrapper, int offset, int limit);

  long countByQuery(Object whereWrapper);

  Result insert(Result result);

  /** 覆写 result/runner/runAt（幂等 upsert 的 update 半边）。 */
  int update(Result result);

  /** 关联行插入（link-cases；已存在的 id 忽略）。 */
  void insertAll(long testRunId, List<Long> caseIds, String assignee);

  /** 解除关联。 */
  int delete(long testRunId, List<Long> caseIds);
}
