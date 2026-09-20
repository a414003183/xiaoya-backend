package net.zentao.workspace.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** 周报快照仓储（domain 接口；实现 infra）。 */
public interface WeeklyReportRepository {

  Optional<WeeklyReport> find(long projectId, LocalDate weekStart);

  /** 落库或整行覆写（UNIQUE(project_id, week_start) 幂等）。 */
  WeeklyReport upsert(WeeklyReport report);

  List<WeeklyReport> queryPage(Object whereWrapper, int offset, int limit);

  long countByQuery(Object whereWrapper);
}
