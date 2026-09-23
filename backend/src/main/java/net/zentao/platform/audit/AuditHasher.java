package net.zentao.platform.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

/**
 * 审计哈希链的规范化与摘要（ADR-004 决策 4，T04）。
 *
 * <p>规范序列化 = 固定字段顺序 + 单元分隔符 {@code 0x1f} 拼接（分隔符在业务值里不出现，故拼接无歧义），
 * null 一律写成空串：任何一处顺序/占位变化都会让全表校验失败，**改这里等于改协议**。
 *
 * <p><b>id 不进哈希</b>：id 是自增列，插入前拿不到，而 audit_log 只有 INSERT/SELECT 授权
 * （UPDATE 补哈希被门禁与授权双挡），故哈希只绑业务列。篡改 id 会破坏 id 序上的 prev_hash 衔接
 * （每行记的是上一行的 hash），仍会被 {@code /audit-logs/verify} 抓到。
 *
 * <p><b>created_at 取秒</b>：库列是存量 TIMESTAMP（秒精度），H2 侧 TIMESTAMP 默认微秒——统一按
 * 「截断到秒的 epoch 秒」参与计算，两侧结果一致（写入时也已截断，见 {@link AuditRecorder}）。
 */
public final class AuditHasher {

  /** 字段分隔符：U+001F（unit separator），业务值里不会出现。 */
  private static final String SEP = "\u001f";

  private AuditHasher() {}

  /** 链哈希：{@code sha256(prev_hash + 规范序列化)}；链首行的 prevHash 为空串。 */
  static String hash(String prevHash, AuditLogPO row) {
    return sha256((prevHash == null ? "" : prevHash) + payload(row));
  }

  /** 规范序列化（字段顺序即协议，勿动）。 */
  static String payload(AuditLogPO row) {
    return String.join(
        SEP,
        text(row.getCategory()),
        text(row.getAction()),
        text(row.getAccount()),
        text(row.getObjectType()),
        text(row.getObjectId()),
        text(row.getResult()),
        text(row.getReason()),
        text(row.getDetail()),
        text(row.getChanges()),
        text(row.getSnapshot()),
        text(row.getExtra()),
        text(row.getBatchId()),
        text(row.getIp()),
        text(row.getUa()),
        text(row.getDevice()),
        text(row.getTraceId()),
        String.valueOf(epochSeconds(row.getCreatedAt())));
  }

  /** 通用 SHA-256 十六进制摘要：链哈希与导出文件摘要（T10 CSV/附件下载的 extra.sha256）共用一处。 */
  public static String sha256(String text) {
    return sha256(text.getBytes(StandardCharsets.UTF_8));
  }

  /** 字节侧重载：导出文件的哈希按响应体原始字节算（含 UTF-8 BOM），不能先转字符串。 */
  public static String sha256(byte[] bytes) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(bytes));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 不可用", impossible);
    }
  }

  static long epochSeconds(Instant instant) {
    return instant == null ? 0L : instant.truncatedTo(ChronoUnit.SECONDS).getEpochSecond();
  }

  private static String text(Object value) {
    return value == null ? "" : String.valueOf(value);
  }
}
