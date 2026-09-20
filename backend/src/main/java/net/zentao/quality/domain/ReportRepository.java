package net.zentao.quality.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 测试报告仓储。 */
public interface ReportRepository {

  Optional<Report> findActiveById(long id);

  List<Report> queryPage(Object whereWrapper, int offset, int limit);

  long countByQuery(Object whereWrapper);

  Report insert(Report report);

  /** 全量覆盖（含 null）；乐观锁不符返回空。 */
  Optional<Report> update(Report report);

  /** 软删单行（deleted_at 置位，A-07；引用方 test_run.reportId 由处理器清空回写）。 */
  void softDelete(long id, String actor, Instant at);
}
