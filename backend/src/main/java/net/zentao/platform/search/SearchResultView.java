package net.zentao.platform.search;

import java.time.Instant;

/** 统一搜索结果项（platform 卡 §5.2：{objectType, objectId, title, excerpt, updatedAt}）。 */
public record SearchResultView(
    String objectType, long objectId, String title, String excerpt, Instant updatedAt) {}
