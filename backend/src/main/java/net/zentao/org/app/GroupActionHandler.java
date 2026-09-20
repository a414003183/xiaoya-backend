package net.zentao.org.app;

import java.util.List;
import net.zentao.org.domain.Group;
import net.zentao.org.domain.GroupAcl;
import net.zentao.org.domain.GroupRepository;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.rbac.PrivilegeCatalog;
import net.zentao.platform.session.SessionPrincipal;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 权限组动作（org 卡 §5）：更新/删除（admin 组 42203，级联清成员与矩阵）/复制/矩阵写/成员写。
 * 矩阵含未注册权限码 → 42201；成员含不存在账号 → 42201；整体替换差量落库。
 */
@Component
public class GroupActionHandler {

  private final GroupRepository repository;
  private final PrivilegeCatalog privilegeCatalog;
  private final net.zentao.org.domain.AccountRepository accountRepository;

  public GroupActionHandler(GroupRepository repository, PrivilegeCatalog privilegeCatalog,
      net.zentao.org.domain.AccountRepository accountRepository) {
    this.repository = repository;
    this.privilegeCatalog = privilegeCatalog;
    this.accountRepository = accountRepository;
  }

  /** acl：null=不修改；传对象=整体替换（子键 null 归一空表即清空该键）。 */
  public record GroupUpdateRequest(String name, String description, GroupAcl acl, Integer lockVersion) {}

  public record GroupCopyRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
      String description,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Boolean copyPrivileges,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Boolean copyMembers) {}

  public record GroupPrivilegesRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> codes) {}

  public record GroupMembersRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Long> accountIds) {}

  @Transactional
  public Group update(SessionPrincipal actor, long groupId, GroupUpdateRequest command) {
    Group group = requireGroup(groupId);
    if (command.lockVersion() == null || command.lockVersion() != group.lockVersion()) {
      throw ApiException.lockConflict("数据已被他人修改，请刷新。");
    }
    if (command.name() != null) {
      CreateGroupHandler.requireName(command.name());
      repository.findByName(command.name())
          .filter(existing -> existing.id() != groupId)
          .ifPresent(existing -> {
            throw ApiException.validation(java.util.Map.of("name", "duplicate"));
          });
      group.rename(command.name());
    }
    if (command.description() != null) {
      group.changeDescription(command.description());
    }
    if (command.acl() != null) {
      group.changeAcl(command.acl());
    }
    return repository.update(group).orElseThrow(() -> ApiException.lockConflict("数据已被他人修改，请刷新。"));
  }

  @Transactional
  public void delete(long groupId) {
    if (groupId == net.zentao.platform.rbac.PrivilegeChecker.SUPER_GROUP_ID) {
      throw ApiException.guardNotSatisfied("内置超管组不可删除。");
    }
    requireGroup(groupId);
    repository.delete(groupId);
  }

  @Transactional
  public Group copy(SessionPrincipal actor, long groupId, GroupCopyRequest command) {
    CreateGroupHandler.requireName(command.name());
    if (repository.findByName(command.name()).isPresent()) {
      throw ApiException.validation(java.util.Map.of("name", "duplicate"));
    }
    Group source = requireGroup(groupId);
    boolean copyPrivileges = command.copyPrivileges() != null && command.copyPrivileges();
    boolean copyMembers = command.copyMembers() != null && command.copyMembers();
    Group created = repository.insert(new Group(0, command.name(), command.description(), GroupAcl.EMPTY,
        actor == null ? null : actor.account(), 0));
    if (copyPrivileges) {
      repository.replacePrivCodes(created.id(), repository.privCodesOf(source.id()));
    }
    if (copyMembers) {
      repository.replaceMembers(created.id(), repository.memberIdsOf(source.id()));
    }
    return created;
  }

  @Transactional
  public List<String> savePrivileges(SessionPrincipal actor, long groupId, GroupPrivilegesRequest command) {
    requireGroup(groupId);
    List<String> codes = command.codes() == null ? List.of() : command.codes().stream().distinct().toList();
    for (String code : codes) {
      if (!privilegeCatalog.isRegistered(code)) {
        throw ApiException.validation(java.util.Map.of("codes", "未注册权限码：" + code));
      }
    }
    repository.replacePrivCodes(groupId, codes);
    return codes;
  }

  @Transactional
  public List<Long> saveMembers(SessionPrincipal actor, long groupId, GroupMembersRequest command) {
    requireGroup(groupId);
    List<Long> accountIds = command.accountIds() == null ? List.of() : command.accountIds().stream().distinct().toList();
    for (Long accountId : accountIds) {
      if (accountRepository.findActiveById(accountId).isEmpty()) {
        throw ApiException.validation(java.util.Map.of("accountIds", "notFound:" + accountId));
      }
    }
    repository.replaceMembers(groupId, accountIds);
    return accountIds;
  }

  private Group requireGroup(long groupId) {
    return repository.findById(groupId).orElseThrow(() -> ApiException.notFound("权限组"));
  }
}
