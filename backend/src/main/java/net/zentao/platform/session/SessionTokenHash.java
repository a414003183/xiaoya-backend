package net.zentao.platform.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 会话 token 的单向摘要（T13 P1-1 → T51 SEC-03）：**它就是 session 表的主键**——凭据本身不落库，
 * 在线用户列表/强退对外的 id 也是它。sha256 不可反推 256 位随机 token，故摘要外泄不等于会话被盗
 * （摘要不能当 cookie 用）；写入口、解析、改密保留当前会话共用本方法，保证口径唯一。
 */
public final class SessionTokenHash {

  private SessionTokenHash() {}

  public static String of(String token) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException missingSha256) {
      throw new IllegalStateException("JVM 缺少 SHA-256", missingSha256);
    }
  }
}
