package net.zentao.org.app;

import java.util.Map;
import net.zentao.org.domain.AccountRepository;
import net.zentao.org.domain.AccountRole;
import net.zentao.org.domain.AccountRoleRepository;
import net.zentao.platform.error.ApiException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 账号角色改名/排序/删除（org 卡 §3.4）。守卫口径：
 * - 内置角色（builtin，迁移自旧禅道 roleList 九项）不可删除，但可改名/排序（旧禅道同语义：改名即改语言项）；
 * - 仍被账号使用的角色不可删除（否则账号上的角色码会变成空标签）→ 42203；
 * - 改名/排序走乐观锁，lockVersion 不符 → 40901。
 */
@Component
public class AccountRoleActionHandler {

  private final AccountRoleRepository repository;
  private final AccountRepository accountRepository;

  public AccountRoleActionHandler(AccountRoleRepository repository, AccountRepository accountRepository) {
    this.repository = repository;
    this.accountRepository = accountRepository;
  }

  /** 部分更新（03 §1：null = 不修改）。 */
  public record RoleUpdateRequest(Map<String, String> labels, Integer sort, Integer lockVersion) {}

  @Transactional
  public AccountRole update(String actor, String code, RoleUpdateRequest command) {
    AccountRole role = repository.findByCode(code).orElseThrow(() -> ApiException.notFound("角色"));
    // 乐观锁口径同权限组/账号：请求须带当前版本，缺或陈旧 → 40901
    if (command.lockVersion() == null || command.lockVersion() != role.lockVersion()) {
      throw ApiException.lockConflict("数据已被他人修改，请刷新。");
    }
    if (command.labels() != null) {
      role.relabel(CreateAccountRoleHandler.labelsOrFail(command.labels()));
    }
    if (command.sort() != null) {
      if (command.sort() < 0) {
        throw ApiException.validation(Map.of("sort", "min"));
      }
      role.reorder(command.sort());
    }
    role.markUpdatedBy(actor);
    return repository.update(role)
        .orElseThrow(() -> ApiException.lockConflict("角色已被他人修改，请刷新后重试。"));
  }

  @Transactional
  public void delete(String code) {
    AccountRole role = repository.findByCode(code).orElseThrow(() -> ApiException.notFound("角色"));
    if (role.builtin()) {
      throw ApiException.guardNotSatisfied("内置角色不可删除，可改名或调整排序。");
    }
    long inUse = accountRepository.countByRole(code);
    if (inUse > 0) {
      throw ApiException.guardNotSatisfied("该角色仍有 " + inUse + " 个账号在使用，请先调整这些账号的角色。");
    }
    repository.delete(code);
  }
}
