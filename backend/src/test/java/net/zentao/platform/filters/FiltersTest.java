package net.zentao.platform.filters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.zentao.platform.error.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 03 §3 过滤 DSL 解析穷尽测试。 */
class FiltersTest {

  private static final FieldRegistry REGISTRY =
      FieldRegistry.allowing(Set.of("status", "id", "createdAt", "assignee"), Set.of("id", "createdAt", "priority"), Set.of("title"));

  private static Filters parse(String key, String value) {
    return Filters.parse(Map.of(key, new String[] {value}), REGISTRY);
  }

  private static ApiException badRequestOf(Runnable run) {
    return assertThrows(ApiException.class, run::run);
  }

  @Test
  @DisplayName("等值过滤")
  void eq() {
    Filters filters = parse("filters[status]", "active");
    assertEquals(1, filters.clauses().size());
    assertEquals(new Filters.FilterClause("status", Filters.Op.EQ, List.of("active")), filters.clauses().getFirst());
  }

  @Test
  @DisplayName("逗号分隔 IN")
  void in() {
    Filters filters = parse("filters[id]", "1,2,3");
    assertEquals(new Filters.FilterClause("id", Filters.Op.IN, List.of("1", "2", "3")), filters.clauses().getFirst());
  }

  @Test
  @DisplayName("闭区间")
  void closedRange() {
    Filters filters = parse("filters[createdAt]", "2026-01-01..2026-02-01");
    assertEquals(
        new Filters.FilterClause("createdAt", Filters.Op.RANGE, List.of("2026-01-01", "2026-02-01")),
        filters.clauses().getFirst());
  }

  @Test
  @DisplayName("半开区间（只有下界 / 只有上界）")
  void halfOpenRange() {
    Filters lowOnly = parse("filters[createdAt]", "2026-01-01..");
    assertEquals(new Filters.FilterClause("createdAt", Filters.Op.RANGE, List.of("2026-01-01", "")), lowOnly.clauses().getFirst());
    Filters highOnly = parse("filters[createdAt]", "..2026-02-01");
    assertEquals(new Filters.FilterClause("createdAt", Filters.Op.RANGE, List.of("", "2026-02-01")), highOnly.clauses().getFirst());
  }

  @Test
  @DisplayName("区间两端同时为空 → 40001")
  void emptyRangeRejected() {
    assertEquals(40001, badRequestOf(() -> parse("filters[createdAt]", "..")).errorCode().code());
  }

  @Test
  @DisplayName("特殊量 @null/@notNull/@me")
  void specials() {
    assertEquals(Filters.Op.IS_NULL, parse("filters[assignee]", "@null").clauses().getFirst().op());
    assertEquals(Filters.Op.NOT_NULL, parse("filters[assignee]", "@notNull").clauses().getFirst().op());
    assertEquals(
        new Filters.FilterClause("assignee", Filters.Op.EQ, List.of("@me")),
        parse("filters[assignee]", "@me").clauses().getFirst());
  }

  @Test
  @DisplayName("多个过滤字段并存")
  void multipleClauses() {
    Filters filters = Filters.parse(
        Map.of(
            "filters[status]", new String[] {"active"},
            "filters[id]", new String[] {"1,2"}),
        REGISTRY);
    assertEquals(2, filters.clauses().size());
  }

  @Test
  @DisplayName("未注册过滤字段 → 40001")
  void unregisteredFieldRejected() {
    assertEquals(40001, badRequestOf(() -> parse("filters[pri]", "1")).errorCode().code()); // banned-words-ok：反向断言——验证 pri 被拒
  }

  @Test
  @DisplayName("排序：- 前缀降序，多字段")
  void sortKeys() {
    Filters filters = parse("sort", "-priority,id");
    assertEquals(List.of(new Filters.SortKey("priority", true), new Filters.SortKey("id", false)), filters.sortKeys());
  }

  @Test
  @DisplayName("未注册排序字段 → 40001")
  void unregisteredSortRejected() {
    assertEquals(40001, badRequestOf(() -> parse("sort", "realName")).errorCode().code());
  }

  @Test
  @DisplayName("分页默认值与上限钳制")
  void paging() {
    Filters defaults = Filters.parse(Map.of(), REGISTRY);
    assertEquals(1, defaults.page());
    assertEquals(20, defaults.limit());
    assertNull(defaults.q());
    assertEquals(0, defaults.offset());

    Filters page2 = parse("limit", "50");
    Filters clamped = Filters.parse(Map.of("page", new String[] {"3"}, "limit", new String[] {"300"}), REGISTRY);
    assertEquals(50, page2.limit());
    assertEquals(200, clamped.limit());
    assertEquals(400, clamped.offset());
  }

  @Test
  @DisplayName("非法分页参数 → 40001")
  void invalidPagingRejected() {
    assertEquals(40001, badRequestOf(() -> parse("page", "0")).errorCode().code());
    assertEquals(40001, badRequestOf(() -> parse("limit", "0")).errorCode().code());
    assertEquals(40001, badRequestOf(() -> parse("limit", "abc")).errorCode().code());
  }

  @Test
  @DisplayName("关键词 q：空白视为缺省")
  void keyword() {
    assertEquals("登录", Filters.parse(Map.of("q", new String[] {"登录"}), REGISTRY).q());
    assertNull(Filters.parse(Map.of("q", new String[] {"  "}), REGISTRY).q());
  }

  @Test
  @DisplayName("A-04：format=csv 时 limit 上限放宽到 5000，其余仍钳 200")
  void csvLimitRelaxed() {
    Filters export = Filters.parse(Map.of(
        "limit", new String[] {"5000"},
        "format", new String[] {"csv"}), REGISTRY);
    assertEquals(5000, export.limit());

    Filters stillClamped = Filters.parse(Map.of(
        "limit", new String[] {"6000"},
        "format", new String[] {"csv"}), REGISTRY);
    assertEquals(5000, stillClamped.limit());

    Filters notCsv = Filters.parse(Map.of(
        "limit", new String[] {"5000"},
        "format", new String[] {"json"}), REGISTRY);
    assertEquals(200, notCsv.limit());
  }

  @Test
  @DisplayName("BE-07：forCount 去排序、分页取缺省，过滤条件与 q 原样保留")
  void forCount() {
    Filters filters = Filters.parse(
        Map.of(
            "filters[status]", new String[] {"active"},
            "sort", new String[] {"-id"},
            "page", new String[] {"3"},
            "limit", new String[] {"50"},
            "q", new String[] {"登录"}),
        REGISTRY);
    Filters count = filters.forCount();
    assertEquals(filters.clauses(), count.clauses());
    assertEquals("登录", count.q());
    assertEquals(List.of(), count.sortKeys());
    assertEquals(Filters.DEFAULT_PAGE, count.page());
    assertEquals(Filters.DEFAULT_LIMIT, count.limit());
    assertEquals(0, count.offset());
  }

  @Test
  @DisplayName("BE-14：翻页深度上限（offset ≤ 10000，越界即 40001 而非照跑）")
  void offsetCap() {
    assertEquals(10000, parse("page", "501").offset());
    assertEquals(40001, badRequestOf(() -> parse("page", "502")).errorCode().code());
    // csv 放宽的是 limit，不是深度：limit=5000 时第 3 页（offset=10000）可用、第 4 页越界
    assertEquals(10000, Filters.parse(
        Map.of("page", new String[] {"3"}, "limit", new String[] {"5000"}, "format", new String[] {"csv"}),
        REGISTRY).offset());
    assertEquals(40001, badRequestOf(() -> Filters.parse(
        Map.of("page", new String[] {"4"}, "limit", new String[] {"5000"}, "format", new String[] {"csv"}),
        REGISTRY)).errorCode().code());
    // 溢出不得溜过闸门：page 取 Integer.MAX_VALUE 时 (page-1)*limit 在 int 下会变负数
    assertEquals(40001, badRequestOf(() -> parse("page", String.valueOf(Integer.MAX_VALUE))).errorCode().code());
  }

  @Test
  @DisplayName("BE-14：IN 值数上限（200 个可用，201 个 → 40001）")
  void inValuesCap() {
    String atLimit = "id,".repeat(Filters.MAX_IN_VALUES - 1) + "id";
    assertEquals(Filters.MAX_IN_VALUES, parse("filters[id]", atLimit).clauses().getFirst().values().size());
    assertEquals(40001, badRequestOf(() -> parse("filters[id]", atLimit + ",id")).errorCode().code());
  }
}
