package net.zentao.quality.infra;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.List;
import net.zentao.quality.domain.Result;
import net.zentao.quality.domain.ResultRepository;
import org.springframework.stereotype.Component;

/** 执行结果仓储实现（infra：UNIQUE(test_run_id, test_case_id) 幂等 upsert）。 */
@Component
public class ResultRepositoryImpl implements ResultRepository {

  private final ResultMapper mapper;

  public ResultRepositoryImpl(ResultMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public java.util.Optional<Result> find(long testRunId, long testCaseId) {
    return java.util.Optional.ofNullable(toDomain(mapper.selectOneByCondition(
        new QueryColumn("test_run_id").eq(testRunId).and(new QueryColumn("test_case_id").eq(testCaseId)))));
  }

  @Override
  public List<Result> findByTestRun(long testRunId) {
    return mapper.selectListByCondition(new QueryColumn("test_run_id").eq(testRunId)).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<Result> queryPage(Object whereWrapper, int offset, int limit) {
    return mapper.selectListByQuery(((QueryWrapper) whereWrapper).limit(offset, limit)).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public long countByQuery(Object whereWrapper) {
    return mapper.selectCountByQuery((QueryWrapper) whereWrapper);
  }

  @Override
  public Result insert(Result result) {
    ResultPO po = toPo(result);
    po.setId(null);
    po.setVersion(1);
    mapper.insert(po);
    return toDomain(mapper.selectOneById(po.getId()));
  }

  @Override
  public int update(Result result) {
    // 全量覆盖（含 null：result 清空场景）
    return mapper.update(toPo(result), false);
  }

  @Override
  public void insertAll(long testRunId, List<Long> caseIds, String assignee) {
    List<Long> existing = mapper.selectListByCondition(new QueryColumn("test_run_id").eq(testRunId)).stream()
        .map(ResultPO::getTestCaseId)
        .toList();
    for (Long caseId : caseIds) {
      if (existing.contains(caseId)) {
        continue;
      }
      ResultPO po = new ResultPO();
      po.setTestRunId(testRunId);
      po.setTestCaseId(caseId);
      po.setVersion(1);
      po.setAssignee(assignee);
      mapper.insert(po);
    }
  }

  @Override
  public int delete(long testRunId, List<Long> caseIds) {
    if (caseIds == null || caseIds.isEmpty()) {
      return 0;
    }
    return mapper.deleteByCondition(new QueryColumn("test_run_id").eq(testRunId)
        .and(new QueryColumn("test_case_id").in(caseIds)));
  }

  private Result toDomain(ResultPO po) {
    if (po == null) {
      return null;
    }
    return new Result(po.getId(), po.getTestRunId(), po.getTestCaseId(),
        po.getVersion() == null ? 1 : po.getVersion(), po.getAssignee(), po.getResult(), po.getRunner(),
        po.getRunAt());
  }

  private ResultPO toPo(Result result) {
    ResultPO po = new ResultPO();
    po.setId(result.id() == 0 ? null : result.id());
    po.setTestRunId(result.testRunId());
    po.setTestCaseId(result.testCaseId());
    po.setVersion(result.version());
    po.setAssignee(result.assignee());
    po.setResult(result.result());
    po.setRunner(result.runner());
    po.setRunAt(result.runAt());
    return po;
  }
}
