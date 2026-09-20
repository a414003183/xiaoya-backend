package net.zentao.workspace.app;

import java.util.List;
import java.util.Map;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.quality.api.BugApi;
import net.zentao.quality.api.TestRunApi;
import net.zentao.requirement.api.StoryApi;
import net.zentao.workspace.api.BugDistributionReport;
import net.zentao.workspace.api.CasePassRateReport;
import net.zentao.workspace.api.StorySummaryReport;
import org.springframework.stereotype.Component;

/**
 * 三个固定报表（workspace 卡 §5/§7）：数据归各域，本域只做形状装配；
 * 可见性由各域 api 守卫（产品 ACL / 测试单所属执行可见性，不可见 → 40302）。
 */
@Component
public class FixedReportQueryService {

  private final StoryApi storyApi;
  private final BugApi bugApi;
  private final TestRunApi testRunApi;

  public FixedReportQueryService(StoryApi storyApi, BugApi bugApi, TestRunApi testRunApi) {
    this.storyApi = storyApi;
    this.bugApi = bugApi;
    this.testRunApi = testRunApi;
  }

  /** 需求统计（排除已删；resolution 同口径见 Bug 分布）。 */
  public StorySummaryReport storySummary(SessionPrincipal principal, long productId) {
    StoryApi.StorySummaryCounts counts = storyApi.summaryCounts(productId, principal);
    return new StorySummaryReport(
        counts.total(),
        entries(counts.byStatus()).stream().map(entry -> new StorySummaryReport.StatusCount(entry.getKey(),
            entry.getValue())).toList(),
        entries(counts.byPriority()).stream().map(entry -> new StorySummaryReport.PriorityCount(entry.getKey(),
            entry.getValue())).toList(),
        entries(counts.byStage()).stream().map(entry -> new StorySummaryReport.StageCount(entry.getKey(),
            entry.getValue())).toList(),
        entries(counts.byType()).stream().map(entry -> new StorySummaryReport.TypeCount(entry.getKey(),
            entry.getValue())).toList());
  }

  /** Bug 分布（resolution 空计入 unresolved 桶）。 */
  public BugDistributionReport bugDistribution(SessionPrincipal principal, long productId) {
    BugApi.BugDistributionCounts counts = bugApi.distributionCounts(productId, principal);
    return new BugDistributionReport(
        counts.total(),
        entries(counts.bySeverity()).stream().map(entry -> new BugDistributionReport.SeverityCount(entry.getKey(),
            entry.getValue())).toList(),
        entries(counts.byStatus()).stream().map(entry -> new BugDistributionReport.StatusCount(entry.getKey(),
            entry.getValue())).toList(),
        entries(counts.byResolution()).stream().map(entry -> new BugDistributionReport.ResolutionCount(
            entry.getKey(), entry.getValue())).toList());
  }

  /** 用例通过率（分母 total−na 为 0 → passRate=null）。 */
  public CasePassRateReport casePassRate(SessionPrincipal principal, long testRunId) {
    TestRunApi.CasePassRate counts = testRunApi.casePassRate(testRunId, principal);
    return new CasePassRateReport(counts.total(), counts.passed(), counts.failed(), counts.blocked(), counts.na(),
        TestRunApi.passRate(counts));
  }

  private static <K> List<Map.Entry<K, Long>> entries(Map<K, Long> counts) {
    return counts.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey(java.util.Comparator
        .comparing(String::valueOf))).toList();
  }
}
