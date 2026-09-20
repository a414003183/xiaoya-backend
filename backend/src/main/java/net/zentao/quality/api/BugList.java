package net.zentao.quality.api;

import java.util.List;

/** Bug 分页载荷（contract：BugList = items + total）。 */
public record BugList(List<BugView> items, long total) {}
