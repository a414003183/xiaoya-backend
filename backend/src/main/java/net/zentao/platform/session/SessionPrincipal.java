package net.zentao.platform.session;

/**
 * 已解析的当前会话身份。
 *
 * <p>{@code sessionId} 是会话行主键 = cookie token 的 sha256（T51 SEC-03）——它能**定位**一条会话
 * （在线用户列表对外的 id、强退、本人改密时"保留当前会话"），但**不能冒充**该会话：不是凭据，
 * 冒充不了 cookie。凭据本体只存在于 cookie 与内存。
 */
public record SessionPrincipal(String sessionId, long accountId, String account) {

  /**
   * 无会话上下文的身份（单测造夹具、将来的后台任务）：拿不到 sessionId 就定位不到"本条会话"，
   * 依赖它的策略按 fail-closed 处理——见 {@code SessionApi#invalidateOthers}。
   */
  public SessionPrincipal(long accountId, String account) {
    this(null, accountId, account);
  }
}
