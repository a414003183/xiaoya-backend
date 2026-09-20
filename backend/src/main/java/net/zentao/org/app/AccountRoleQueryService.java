package net.zentao.org.app;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import net.zentao.org.domain.AccountRepository;
import net.zentao.org.domain.AccountRole;
import net.zentao.org.domain.AccountRoleRepository;
import net.zentao.platform.error.ApiException;
import org.springframework.stereotype.Component;

/**
 * 账号角色字典查询（org 卡 §3.4）：全量按 sort 升序返回；accountCount 为**仍在使用该角色的账号数**
 * （软删账号不计），既是列表列也是删除守卫的可见依据。
 */
@Component
public class AccountRoleQueryService {

  private final AccountRoleRepository repository;
  private final AccountRepository accountRepository;

  public AccountRoleQueryService(AccountRoleRepository repository, AccountRepository accountRepository) {
    this.repository = repository;
    this.accountRepository = accountRepository;
  }

  /** RoleView（contract：code/labels/sort/builtin/accountCount/审计/lockVersion）。 */
  public record RoleView(
      String code, Map<String, String> labels, int sort, boolean builtin, long accountCount,
      String createdBy, Instant createdAt, String updatedBy, Instant updatedAt, int lockVersion) {}

  /** RoleList 载荷（contract：items + total；字典级小列表，不分页）。 */
  public record RoleList(List<RoleView> items, long total) {}

  public RoleList list() {
    List<RoleView> items = repository.findAll().stream().map(this::toView).toList();
    return new RoleList(items, items.size());
  }

  public RoleView detail(String code) {
    return toView(require(code));
  }

  AccountRole require(String code) {
    return repository.findByCode(code).orElseThrow(() -> ApiException.notFound("角色"));
  }

  private RoleView toView(AccountRole role) {
    return new RoleView(role.code(), role.labels(), role.sort(), role.builtin(),
        accountRepository.countByRole(role.code()), null, null, role.updatedBy(), null, role.lockVersion());
  }
}
