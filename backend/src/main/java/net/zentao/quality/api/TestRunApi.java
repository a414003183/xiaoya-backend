package net.zentao.quality.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import net.zentao.platform.session.SessionPrincipal;

/** 测试单域对外接口（A2：workspace 域待办 objectTitle 与用例通过率报表经此只读）。 */
public interface TestRunApi {

  /** 标题联查（workspace 卡 §3.1 objectTitle 现算）：id → 标题，缺失/已删的 id 不出现在结果中。 */
  Map<Long, String> titlesByIds(List<Long> ids);

  /** 用例通过率取数（workspace 卡 §5：pass/fail/blocked/n-a 计数；测试单不可见 → 40302）。 */
  record CasePassRate(long total, long passed, long failed, long blocked, long na) {}

  CasePassRate casePassRate(long testRunId, SessionPrincipal principal);

  /** 通过率百分数：passed/(total−na)×100 两位小数，分母 0 → null。 */
  static BigDecimal passRate(CasePassRate counts) {
    long denominator = counts.total() - counts.na();
    if (denominator <= 0) {
      return null;
    }
    return BigDecimal.valueOf(counts.passed())
        .multiply(BigDecimal.valueOf(100))
        .divide(BigDecimal.valueOf(denominator), 2, java.math.RoundingMode.HALF_UP);
  }
}
