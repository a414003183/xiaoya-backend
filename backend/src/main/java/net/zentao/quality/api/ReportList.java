package net.zentao.quality.api;

import java.util.List;

/** 测试报告分页载荷（contract：ReportList = items + total）。 */
public record ReportList(List<ReportView> items, long total) {}
