package net.zentao.org.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import net.zentao.org.domain.Role;
import net.zentao.org.domain.RoleAcl;
import net.zentao.org.domain.RoleRepository;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
import net.zentao.platform.rbac.PrivilegeCatalog;
import net.zentao.platform.session.SessionPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 角色动作（T23）：更新（名字/标识/描述/数据权限，乐观锁）/删除（内置 42203，级联清成员与权限码）/
 * 复制/权限码写/成员写。权限码含未注册码 → 42201；成员含不存在账号 → 42201；整体替换差量落库。
 */
@Component
public class RoleActionHandler {

  private final RoleRepository repository;
  private final PrivilegeCatalog privilegeCatalog;
  private final net.zentao.org.domain.AccountRepository accountRepository;

  public RoleActionHandler(RoleRepository repository, PrivilegeCatalog privilegeCatalog,
      net.zentao.org.domain.AccountRepository accountRepository) {
    this.repository = repository;
    this.privilegeCatalog = privilegeCatalog;
    this.accountRepository = accountRepository;
  }

  /** acl：null=不修改；传对象=整体替换（子键 null 归一空表即清空该键）。 */
  public record RoleUpdateRequest(String name, String code, String description, RoleAcl acl, Integer sort,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer lockVersion) {}

  public record RoleCopyRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name, String description,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Boolean copyPrivileges,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Boolean copyMembers) {}

  public record RolePrivilegesRequest(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> codes) {}

  public record RoleMembersRequest(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Long> accountIds) {}

  @Transactional
  public Role update(SessionPrincipal actor, long roleId, RoleUpdateRequest command) {
    Role role = requireRole(roleId);
    if (command.lockVersion() == null || command.lockVersion() != role.lockVersion()) {
      throw ApiException.lockConflict();
    }
    if (command.name() != null) {
      CreateRoleHandler.requireName(command.name());
      repository.findByName(command.name().trim())
          .filter(existing -> existing.id() != roleId)
          .ifPresent(existing -> {
            throw ApiException.validation(Map.of("name", "duplicate"));
          });
      role.rename(command.name().trim());
    }
    if (command.code() != null) {
      assertCodeFree(roleId, command.code());
    }
    if (command.description() != null) {
      role.changeDescription(command.description());
    }
    if (command.acl() != null) {
      role.changeAcl(command.acl());
    }
    if (command.sort() != null) {
      role.reorder(command.sort());
    }
    role.markUpdatedBy(actor == null ? null : actor.account());
    return repository.update(role).orElseThrow(ApiException::lockConflict);
  }

  /** 角色码不可改（它是外部引用角色的稳定标识，改了等于换了个角色）：传了别的值一律 42201。 */
  private void assertCodeFree(long roleId, String code) {
    if (code.isBlank()) {
      throw ApiException.validation(Map.of("code", "readonly"));
    }
    repository.findAll().stream()
        .filter(existing -> code.trim().equals(existing.code()) && existing.id() != roleId)
        .findFirst()
        .ifPresent(existing -> {
          throw ApiException.validation(Map.of("code", "duplicate"));
        });
  }

  @Transactional
  public void delete(long roleId) {
    Role role = requireRole(roleId);
    if (role.builtin() || roleId == net.zentao.platform.rbac.PrivilegeChecker.SUPER_ROLE_ID) {
      throw ApiException.keyed(ErrorCode.GUARD_NOT_SATISFIED, "role.guard.builtinDelete");
    }
    repository.delete(roleId);
  }

  @Transactional
  public Role copy(SessionPrincipal actor, long roleId, RoleCopyRequest command) {
    CreateRoleHandler.requireName(command.name());
    if (repository.findByName(command.name().trim()).isPresent()) {
      throw ApiException.validation(Map.of("name", "duplicate"));
    }
    Role source = requireRole(roleId);
    boolean copyPrivileges = command.copyPrivileges() != null && command.copyPrivileges();
    boolean copyMembers = command.copyMembers() != null && command.copyMembers();
    Role created = repository.insert(new Role(0, null, command.name().trim(), command.description(),
        RoleAcl.EMPTY, false, source.sort(), actor == null ? null : actor.account(), java.time.Instant.now(),
        null, null, 0));
    if (copyPrivileges) {
      repository.replacePrivCodes(created.id(), repository.privCodesOf(source.id()));
    }
    if (copyMembers) {
      repository.replaceMembers(created.id(), repository.memberIdsOf(source.id()));
    }
    return created;
  }

  @Transactional
  public List<String> savePrivileges(SessionPrincipal actor, long roleId, RolePrivilegesRequest command) {
    requireRole(roleId);
    List<String> codes = command.codes() == null ? List.of() : command.codes().stream().distinct().toList();
    for (String code : codes) {
      if (!privilegeCatalog.isRegistered(code)) {
        throw ApiException.validation(Map.of("codes", code));
      }
    }
    repository.replacePrivCodes(roleId, codes);
    return codes;
  }

  @Transactional
  public List<Long> saveMembers(SessionPrincipal actor, long roleId, RoleMembersRequest command) {
    requireRole(roleId);
    List<Long> accountIds = command.accountIds() == null ? List.of() : command.accountIds().stream().distinct().toList();
    for (Long accountId : accountIds) {
      if (accountRepository.findActiveById(accountId).isEmpty()) {
        throw ApiException.validation(Map.of("accountIds", "notFound:" + accountId));
      }
    }
    repository.replaceMembers(roleId, accountIds);
    return accountIds;
  }

  private Role requireRole(long roleId) {
    return repository.findById(roleId).orElseThrow(() -> ApiException.notFound("entity.role"));
  }
}
