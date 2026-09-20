package net.zentao.quality.infra;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.quality.api.TestRunApi;
import net.zentao.quality.app.TestRunQueryService;
import net.zentao.quality.domain.TestRun;
import net.zentao.quality.domain.TestRunRepository;
import org.springframework.stereotype.Component;

/** TestRunApi 真实现（A2 跨域只读：workspace 聚合消费面）。 */
@Component
public class TestRunApiImpl implements TestRunApi {

  private final TestRunRepository repository;
  private final TestRunQueryService queryService;

  public TestRunApiImpl(TestRunRepository repository, TestRunQueryService queryService) {
    this.repository = repository;
    this.queryService = queryService;
  }

  @Override
  public CasePassRate casePassRate(long testRunId, SessionPrincipal principal) {
    return queryService.casePassRate(principal, testRunId);
  }

  @Override
  public Map<Long, String> titlesByIds(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return Map.of();
    }
    Map<Long, String> titles = new LinkedHashMap<>();
    for (TestRun testRun : repository.findActiveByIds(ids.stream().distinct().toList())) {
      titles.put(testRun.id(), testRun.name());
    }
    return titles;
  }
}
