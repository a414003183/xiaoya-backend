package net.zentao.quality.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 套件/用例库仓储（suite 同表两义；caseIds 由 suite_case 承载）。 */
public interface SuiteRepository {

  Optional<Suite> findActiveById(long id);

  List<Suite> queryPage(Object whereWrapper, int offset, int limit);

  long countByQuery(Object whereWrapper);

  Suite insert(Suite suite);

  /** 全量覆盖主表；乐观锁不符返回空。 */
  Optional<Suite> update(Suite suite);

  /** 关联行全量替换（link/unlink 后落库；UNIQUE 幂等）。 */
  void replaceCases(long suiteId, List<Long> caseIds);

  /** 批量关联计数（键 = suiteId）。 */
  Map<Long, Long> countCases(List<Long> suiteIds);

  /** 软删单行（deleted_at 置位，A-07；suite_case 关联行保留自然失效）。 */
  void softDelete(long id, String actor, Instant at);
}
