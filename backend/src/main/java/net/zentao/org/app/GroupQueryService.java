package net.zentao.org.app;

import java.util.List;
import net.zentao.org.domain.Account;
import net.zentao.org.domain.AccountRepository;
import net.zentao.org.domain.Group;
import net.zentao.org.domain.GroupRepository;
import net.zentao.platform.error.ApiException;
import org.springframework.stereotype.Component;

/** 权限组查询（org 卡 §3.3：memberCount/privilegeCount 实时统计）。 */
@Component
public class GroupQueryService {

  private final GroupRepository groupRepository;
  private final AccountRepository accountRepository;

  public GroupQueryService(GroupRepository groupRepository, AccountRepository accountRepository) {
    this.groupRepository = groupRepository;
    this.accountRepository = accountRepository;
  }

  /** GroupList 载荷（contract：items + total）。 */
  public record GroupList(List<CreateGroupHandler.GroupView> items, long total) {}

  public GroupList list() {
    List<CreateGroupHandler.GroupView> items =
        groupRepository.findAll().stream().map(this::toView).toList();
    return new GroupList(items, items.size());
  }

  public CreateGroupHandler.GroupView detail(long groupId) {
    return toView(requireGroup(groupId));
  }

  public List<Long> memberIds(long groupId) {
    requireGroup(groupId);
    return groupRepository.memberIdsOf(groupId);
  }

  public List<Account> members(long groupId) {
    requireGroup(groupId);
    return groupRepository.memberIdsOf(groupId).stream()
        .map(accountRepository::findActiveById)
        .flatMap(java.util.Optional::stream)
        .toList();
  }

  public List<String> privileges(long groupId) {
    requireGroup(groupId);
    return groupRepository.privCodesOf(groupId);
  }

  private Group requireGroup(long groupId) {
    return groupRepository.findById(groupId).orElseThrow(() -> ApiException.notFound("权限组"));
  }

  private CreateGroupHandler.GroupView toView(Group group) {
    return new CreateGroupHandler.GroupView(
        group.id(), group.name(), group.description(), group.acl(),
        groupRepository.memberIdsOf(group.id()).size(),
        groupRepository.privCodesOf(group.id()).size(),
        group.createdBy(), null, null, null, group.lockVersion());
  }
}
