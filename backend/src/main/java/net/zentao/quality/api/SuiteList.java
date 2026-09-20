package net.zentao.quality.api;

import java.util.List;

/** 套件/用例库分页载荷（contract：SuiteList = items + total）。 */
public record SuiteList(List<SuiteView> items, long total) {}
