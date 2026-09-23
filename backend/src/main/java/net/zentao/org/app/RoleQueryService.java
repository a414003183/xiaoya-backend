package net.zentao.org.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import net.zentao.org.domain.Account;
import net.zentao.org.domain.AccountRepository;
import net.zentao.org.domain.Role;
import net.zentao.org.domain.RoleAcl;
import net.zentao.org.domain.RoleRepository;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.rbac.PrivilegeCatalog;
import org.springframework.stereotype.Component;

/** 角色查询（T23：memberCount/privilegeCount 实时统计，列表一次装配避免 N+1）。 */
@Component
public class RoleQueryService {

  private final RoleRepository roleRepository;
  private final AccountRepository accountRepository;
  private final PrivilegeCatalog privilegeCatalog;

  public RoleQueryService(RoleRepository roleRepository, AccountRepository accountRepository,
      PrivilegeCatalog privilegeCatalog) {
    this.roleRepository = roleRepository;
    this.accountRepository = accountRepository;
    this.privilegeCatalog = privilegeCatalog;
  }

  /** RoleList 载荷（contract：items + total）。 */
  public record RoleList(List<RoleView> items, long total) {}

  /**
   * 角色视图（contract RoleView）。`code` 是可选稳定标识（迁移来的岗位角色带码，新建的可空）；
   * `builtin` 的角色不可删除（超管角色 id=1 与迁移来的内置岗位角色）。
   */
  public record RoleView(long id, String code, String name, String description,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) RoleAcl acl, long memberCount, long privilegeCount,
      boolean builtin, int sort, String createdBy, Instant createdAt, String updatedBy, Instant updatedAt,
      int lockVersion) {}

  public RoleList list() {
    List<RoleView> items = roleRepository.findAll().stream().map(this::toView).toList();
    return new RoleList(items, items.size());
  }

  public RoleView detail(long roleId) {
    return toView(requireRole(roleId));
  }

  public List<Long> memberIds(long roleId) {
    requireRole(roleId);
    return roleRepository.memberIdsOf(roleId);
  }

  public List<Account> members(long roleId) {
    requireRole(roleId);
    return roleRepository.memberIdsOf(roleId).stream()
        .map(accountRepository::findActiveById)
        .flatMap(java.util.Optional::stream)
        .toList();
  }

  public List<String> privileges(long roleId) {
    requireRole(roleId);
    return roleRepository.privCodesOf(roleId);
  }

  private Role requireRole(long roleId) {
    return roleRepository.findById(roleId).orElseThrow(() -> ApiException.notFound("entity.role"));
  }

  private RoleView toView(Role role) {
    return new RoleView(role.id(), role.code(), role.name(), role.description(), role.acl(),
        roleRepository.countMembers(role.id()), privilegeCountOf(role), role.builtin(),
        role.sort(), role.createdBy(), null, null, null, role.lockVersion());
  }

  /**
   * 权限数：超管角色（id=1）不落 role_priv 行而是「全过」（PrivilegeChecker 的短路），
   * 照真值显示 0 会让人以为它没权限，故按权限编目全集回答；其余角色读实际行数。
   */
  private long privilegeCountOf(Role role) {
    return role.id() == net.zentao.platform.rbac.PrivilegeChecker.SUPER_ROLE_ID
        ? privilegeCatalog.allCodes().size()
        : roleRepository.countPrivCodes(role.id());
  }
}
