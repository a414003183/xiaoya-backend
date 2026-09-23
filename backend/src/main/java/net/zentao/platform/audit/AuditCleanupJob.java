package net.zentao.platform.audit;

import com.mybatisflex.core.query.QueryColumn;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import net.zentao.platform.meta.SettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 审计保留策略（T04/ADR-004 决策 5）：删除早于保留期的审计行，动作自身记审计。
 *
 * <p>保留期取设置 {@code audit.retention-days}（默认 180，0 或负数 = 不清理），设置缺失时回落
 * 配置项 {@code zentao.audit.retention-days}。
 *
 * <p><b>这是全仓唯一允许删审计行的代码路径</b>（`check-audit-append-only` 门禁的行内豁免）：
 * 「只追加」防的是**篡改**，不是防保留期治理——生产授权规约（只授 INSERT/SELECT）不变，
 * 需要清理的环境给账号额外开 DELETE，不给则本 Job 记一条 error 日志后静默放弃。
 *
 * <p>清理会截断哈希链的前缀：{@code /audit-logs/verify} 以扫描区间内首行为锚点，故保留窗口内的链仍自洽
 * （窗口之外的行已经不存在，无从校验）。
 */
@Component
public class AuditCleanupJob {

  private static final Logger log = LoggerFactory.getLogger(AuditCleanupJob.class);

  private static final QueryColumn CREATED_AT = new QueryColumn("created_at");
  private static final String ACTION = "audit-cleanup";

  private final AuditLogMapper mapper;
  private final AuditRecorder recorder;
  private final SettingRepository settings;
  private final JsonMapper jsonMapper;
  private final int defaultRetentionDays;

  public AuditCleanupJob(AuditLogMapper mapper, AuditRecorder recorder, SettingRepository settings,
      JsonMapper jsonMapper, @Value("${zentao.audit.retention-days:180}") int defaultRetentionDays) {
    this.mapper = mapper;
    this.recorder = recorder;
    this.settings = settings;
    this.jsonMapper = jsonMapper;
    this.defaultRetentionDays = defaultRetentionDays;
  }

  @Scheduled(cron = "${zentao.audit.cleanup-cron:0 15 4 * * *}")
  public void cleanup() {
    int retentionDays = retentionDays();
    if (retentionDays <= 0) {
      log.debug("audit cleanup disabled (retention-days={})", retentionDays);
      return;
    }
    Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    Instant cutoff = now.minus(retentionDays, ChronoUnit.DAYS);
    int removed;
    try {
      removed = mapper.deleteByCondition(CREATED_AT.lt(cutoff));  // audit-append-only-ok：保留策略（ADR-004 决策 5）唯一允许的删行路径
    } catch (RuntimeException e) {
      log.error("audit cleanup failed cutoff={}（生产账号无 DELETE 权限时属预期，见本类 javadoc）", cutoff, e);
      return;
    }
    if (removed == 0) {
      // 空跑不记行：审计表按天运行会产生 365 行/年噪音，清理「确实删了东西」才值得留痕
      return;
    }
    AuditLogPO row = new AuditLogPO();
    row.setAction(ACTION);
    row.setResult(AuditResult.SUCCESS.value());
    row.setDetail("removed=" + removed + " cutoff=" + cutoff + " retentionDays=" + retentionDays);
    recorder.record(row);
  }

  /** 保留天数：设置项优先（JSON 数字文本），解析不出回落配置默认值。 */
  private int retentionDays() {
    return settings.findSystem("audit", "retention-days")
        .map(po -> parseDays(po.getItemValue()))
        .orElse(defaultRetentionDays);
  }

  private int parseDays(String json) {
    if (json == null || json.isBlank()) {
      return defaultRetentionDays;
    }
    try {
      return jsonMapper.readTree(json).asInt(defaultRetentionDays);
    } catch (RuntimeException broken) {
      log.warn("audit.retention-days 值不可解析：{}（回落 {}）", json, defaultRetentionDays);
      return defaultRetentionDays;
    }
  }
}
