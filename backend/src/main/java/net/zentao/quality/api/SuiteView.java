package net.zentao.quality.api;

import java.time.Instant;
import java.util.List;
import net.zentao.quality.domain.Suite;
import io.swagger.v3.oas.annotations.media.Schema;

/** 套件/用例库共用视图（quality 卡 §3.3；字段集合与 contract SuiteView 一一对应）。 */
public record SuiteView(
    long id,
    long productId,
    String name,
    String description,
    @Schema(allowableValues = {"library", "private", "public"}) String type,
    int sort,
    List<Long> caseIds,
    long caseCount,
    String createdBy,
    Instant createdAt,
    String updatedBy,
    Instant updatedAt,
    int lockVersion) {

  /** 列表行：caseIds 恒空数组（契约约定），caseCount 由查询服务批量计算。 */
  public static SuiteView listRow(Suite suite, long caseCount) {
    return of(suite, List.of(), caseCount);
  }

  /** 详情行：含关联用例 id。 */
  public static SuiteView detailOf(Suite suite, long caseCount) {
    return of(suite, suite.caseIds(), caseCount);
  }

  private static SuiteView of(Suite suite, List<Long> caseIds, long caseCount) {
    return new SuiteView(
        suite.id(),
        suite.productId(),
        suite.name(),
        suite.description(),
        suite.type(),
        suite.sort(),
        caseIds,
        caseCount,
        suite.createdBy(),
        suite.createdAt(),
        suite.updatedBy(),
        suite.updatedAt(),
        suite.lockVersion());
  }
}
