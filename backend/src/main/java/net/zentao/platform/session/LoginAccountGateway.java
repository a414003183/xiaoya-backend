package net.zentao.platform.session;

/**
 * platform 侧账号网关（A3：platform 不引用 org，由 org 域实现注入——org/infra/LoginAccountGatewayImpl）。
 * 平台只关心"这份凭据是否有效"与"这个账号的响应视图长什么样"，不关心存储。
 */
public interface LoginAccountGateway {

  /**
   * 校验登录凭据：账号存在且未删、已启用、未在锁定窗口、密码匹配。
   * 任一不满足 → 抛 40101。成功返回完整账号视图。
   */
  AccountView verifyLogin(String account, String rawPassword);

  /** 按账号 id 取未删账号视图；不存在返回 null。 */
  AccountView view(long accountId);
}
