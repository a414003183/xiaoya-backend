package net.zentao.quality.api;

import java.util.List;

/** 测试单分页载荷（contract：TestRunList = items + total）。 */
public record TestRunList(List<TestRunView> items, long total) {}
