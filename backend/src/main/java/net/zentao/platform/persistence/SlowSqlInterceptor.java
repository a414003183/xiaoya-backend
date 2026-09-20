package net.zentao.platform.persistence;

import java.time.Duration;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 慢 SQL 观测（B1 §4）：超过阈值的语句打 WARN。
 *
 * <p>只记语句 id 与耗时，不记 SQL 文本与参数——参数里有口令、邮件正文这类内容，抄进日志等于二次泄露；
 * 要定位具体语句，拿语句 id 回源码即可。
 *
 * <p>ponytail: 只有日志没有指标；要按 P99 出面板须换 Micrometer Timer，等 B2 引入度量时一并接。
 */
@Component
@Intercepts({
    @Signature(
        type = Executor.class,
        method = "query",
        args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
    @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
public class SlowSqlInterceptor implements Interceptor {

  private static final Logger log = LoggerFactory.getLogger(SlowSqlInterceptor.class);

  private final long thresholdMillis;

  public SlowSqlInterceptor(@Value("${zentao.persistence.slow-sql-threshold:1s}") Duration threshold) {
    this.thresholdMillis = threshold.toMillis();
  }

  @Override
  public Object intercept(Invocation invocation) throws Throwable {
    long startedAt = System.nanoTime();
    try {
      return invocation.proceed();
    } finally {
      long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;
      if (elapsedMillis >= thresholdMillis) {
        log.warn("slow sql id={} elapsedMs={}", statementId(invocation), elapsedMillis);
      }
    }
  }

  @Override
  public Object plugin(Object target) {
    return Plugin.wrap(target, this);
  }

  private static String statementId(Invocation invocation) {
    Object first = invocation.getArgs().length == 0 ? null : invocation.getArgs()[0];
    return first instanceof MappedStatement statement ? statement.getId() : "unknown";
  }
}
