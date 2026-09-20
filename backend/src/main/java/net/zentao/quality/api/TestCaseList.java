package net.zentao.quality.api;

import java.util.List;

/** 用例分页载荷（contract：TestCaseList = items + total）。 */
public record TestCaseList(List<TestCaseView> items, long total) {}
