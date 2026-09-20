package net.zentao.quality.api;

import java.util.List;

/** 执行清单分页载荷（contract：RunCaseList = items + total）。 */
public record RunCaseList(List<ResultView> items, long total) {}
