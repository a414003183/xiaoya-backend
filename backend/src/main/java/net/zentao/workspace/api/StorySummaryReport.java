package net.zentao.workspace.api;

import java.util.List;

/** 产品需求统计（contract：StorySummaryReport；workspace 卡 §5，排除已删）。 */
public record StorySummaryReport(
    long total,
    List<StatusCount> byStatus,
    List<PriorityCount> byPriority,
    List<StageCount> byStage,
    List<TypeCount> byType) {

  public record StatusCount(String status, long count) {}

  public record PriorityCount(int priority, long count) {}

  public record StageCount(String stage, long count) {}

  public record TypeCount(String type, long count) {}
}
