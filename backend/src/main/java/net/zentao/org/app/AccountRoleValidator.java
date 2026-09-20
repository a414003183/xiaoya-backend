package net.zentao.org.app;

import java.util.Map;
import net.zentao.org.domain.AccountRoleRepository;
import net.zentao.platform.error.ApiException;
import org.springframework.stereotype.Component;

/**
 * 账号角色码校验（org 卡 §3.4）：role 非空时必须存在于角色字典（account_role），否则 42201。
 * 原先是角色枚举校验（dev|qa|pm|… 硬编码在请求体注解里）；角色集改为可维护数据后，唯一真源 = 字典表，
 * 因此校验口收敛到本类，账号新建/更新两条写路径共用。
 */
@Component
public class AccountRoleValidator {

  private final AccountRoleRepository repository;

  public AccountRoleValidator(AccountRoleRepository repository) {
    this.repository = repository;
  }

  public void require(String role) {
    if (role == null || role.isBlank()) {
      return;
    }
    if (!repository.existsByCode(role)) {
      throw ApiException.validation(Map.of("role", "notFound"));
    }
  }
}
