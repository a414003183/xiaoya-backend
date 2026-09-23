package net.zentao.platform.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 唯一的 BCrypt 编码器（T62 SEC-11）：本人改密、管理员重置、建档、登录验密共用同一实例——
 * 强度/算法只有一处可配（T42 的密码策略设置在此收口），也避免各处 `new` 出参数不同的编码器，
 * 让同一口令在两条路径上有两种强度。T58 的未知账号替身哈希（`LoginAccountGatewayImpl`）同样用它。
 *
 * <p>例外：迁移（`db/migration/V3__org_seed.java`、`net/zentao/db/migration/V22__admin_initial_password.java`）各自 `new` 一份——
 * 迁移是独立可执行的，不依赖 Spring 容器（旧库导入 CLI 只跑迁移，没有 bean）。
 */
@Configuration
public class PasswordEncoderConfig {

  @Bean
  public BCryptPasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
