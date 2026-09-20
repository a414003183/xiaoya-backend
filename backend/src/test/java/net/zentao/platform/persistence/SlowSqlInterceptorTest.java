package net.zentao.platform.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;

import net.zentao.platform.audit.AuditLogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * B1 §4 慢 SQL 观测：验证 Interceptor 真的挂上了手工装配的 SqlSessionFactory
 * （阈值设 0 → 任意语句都算慢，输出里必出现告警行）。耗时比较本身是 nanoTime 差值，不再单测。
 */
@SpringBootTest(properties = "zentao.persistence.slow-sql-threshold=0ms")
@ExtendWith(OutputCaptureExtension.class)
class SlowSqlInterceptorTest {

  @Autowired
  AuditLogMapper auditLogMapper;

  @Test
  @DisplayName("阈值 0 时任意 mapper 查询都打 slow sql 告警（证明拦截器已入链）")
  void logsSlowStatement(CapturedOutput output) {
    auditLogMapper.selectAll();
    assertTrue(output.getOut().contains("slow sql"), output.getOut());
  }
}
