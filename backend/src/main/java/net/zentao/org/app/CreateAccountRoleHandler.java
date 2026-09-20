package net.zentao.org.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import net.zentao.org.domain.AccountRole;
import net.zentao.org.domain.AccountRoleRepository;
import net.zentao.platform.error.ApiException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 创建账号角色（org 卡 §3.4）：code 小写字母开头 2–16 位、全库唯一，创建后不可改（账号 role 列存它）；
 * labels 至少一个非空语言名。code 重复/非法、labels 全空 → 42201。
 */
@Component
public class CreateAccountRoleHandler {

  private final AccountRoleRepository repository;

  public CreateAccountRoleHandler(AccountRoleRepository repository) {
    this.repository = repository;
  }

  public record RoleCreateRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = AccountRole.CODE_MAX) String code,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Map<String, String> labels,
      Integer sort) {}

  @Transactional
  public AccountRole handle(String actor, RoleCreateRequest command) {
    if (!AccountRole.validCode(command.code())) {
      throw ApiException.validation(Map.of("code", "pattern"));
    }
    if (repository.existsByCode(command.code())) {
      throw ApiException.validation(Map.of("code", "duplicate"));
    }
    Map<String, String> labels = labelsOrFail(command.labels());
    int sort = command.sort() != null ? command.sort() : repository.nextSort();
    return repository.insert(new AccountRole(command.code(), labels, sort, false, 0));
  }

  /** labels 必须至少有一个非空语言名（其余空语言项被规整掉）。 */
  static Map<String, String> labelsOrFail(Map<String, String> labels) {
    Map<String, String> normalized;
    try {
      normalized = AccountRole.normalize(labels);
    } catch (IllegalArgumentException e) {
      throw ApiException.validation(Map.of("labels", "size"));
    }
    if (normalized.isEmpty()) {
      throw ApiException.validation(Map.of("labels", "required"));
    }
    return normalized;
  }
}
