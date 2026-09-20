package net.zentao.workspace.domain;

import java.time.LocalDate;
import java.util.List;

/** 燃尽日行仓储（domain 接口；实现 infra）。 */
public interface BurnRepository {

  /** 某执行全部日行（按日期、任务排序）。 */
  List<Burn> findByExecution(long executionId);

  /** 某执行某日的日行（含 task_id=0 汇总行）。 */
  List<Burn> findByExecutionAndDate(long executionId, LocalDate burnDate);

  /** 同日 upsert（UNIQUE(execution_id, burn_date, task_id) 兜底），逐行幂等。 */
  void upsert(Burn burn);

  /** 执行当前剩余工时合计（理想线起点：首日基线）。 */
  java.math.BigDecimal totalLeftHours(long executionId);
}
