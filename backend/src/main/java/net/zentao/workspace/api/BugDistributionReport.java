package net.zentao.workspace.api;

import java.util.List;

/** Bug 分布（contract：BugDistributionReport；workspace 卡 §5，resolution 空计入 unresolved 桶）。 */
public record BugDistributionReport(
    long total,
    List<SeverityCount> bySeverity,
    List<StatusCount> byStatus,
    List<ResolutionCount> byResolution) {

  public record SeverityCount(int severity, long count) {}

  public record StatusCount(String status, long count) {}

  public record ResolutionCount(String resolution, long count) {}
}
