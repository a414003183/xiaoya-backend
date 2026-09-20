package net.zentao.org.infra.web;

import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import net.zentao.org.app.CreateGroupHandler;
import net.zentao.org.app.GroupActionHandler;
import net.zentao.org.app.AccountQueryService;
import net.zentao.org.app.GroupQueryService;
import net.zentao.platform.error.ApiException;
import net.zentao.org.domain.Group;
import net.zentao.platform.rbac.RequirePrivilege;
import net.zentao.platform.session.AccountView;
import net.zentao.platform.session.SessionResolver;
import net.zentao.platform.web.DataEnvelope;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 权限组十一端点（org 卡 §5；权限码 group-*）。 */
@RestController
@RequestMapping("/api/v1")
public class GroupController {

  private final GroupQueryService queryService;
  private final CreateGroupHandler createHandler;
  private final GroupActionHandler actionHandler;
  private final net.zentao.org.domain.AccountRepository accountRepository;
  private final SessionResolver resolver;

  public GroupController(GroupQueryService queryService, CreateGroupHandler createHandler,
      GroupActionHandler actionHandler, net.zentao.org.domain.AccountRepository accountRepository,
      SessionResolver resolver) {
    this.queryService = queryService;
    this.createHandler = createHandler;
    this.actionHandler = actionHandler;
    this.accountRepository = accountRepository;
    this.resolver = resolver;
  }

  @GetMapping("/groups")
  @Operation(operationId = "listGroups")
  @RequirePrivilege("group-view")
  public DataEnvelope<GroupQueryService.GroupList> list() {
    return DataEnvelope.of(queryService.list());
  }

  @PostMapping("/groups")
  @Operation(operationId = "createGroup")
  @RequirePrivilege("group-create")
  public DataEnvelope<CreateGroupHandler.GroupView> create(
      @RequestBody CreateGroupHandler.GroupCreateRequest body, jakarta.servlet.http.HttpServletRequest request) {
    Group created = createHandler.handle(resolver.resolve(request).account(), body);
    return DataEnvelope.of(queryService.detail(created.id()));
  }

  @GetMapping("/groups/{groupId}")
  @Operation(operationId = "getGroup")
  @RequirePrivilege("group-view")
  public DataEnvelope<CreateGroupHandler.GroupView> detail(@PathVariable long groupId) {
    return DataEnvelope.of(queryService.detail(groupId));
  }

  @PatchMapping("/groups/{groupId}")
  @Operation(operationId = "updateGroup")
  @RequirePrivilege("group-edit")
  public DataEnvelope<CreateGroupHandler.GroupView> update(@PathVariable long groupId,
      @RequestBody GroupActionHandler.GroupUpdateRequest body, jakarta.servlet.http.HttpServletRequest request) {
    actionHandler.update(resolver.resolve(request), groupId, body);
    return DataEnvelope.of(queryService.detail(groupId));
  }

  @DeleteMapping("/groups/{groupId}")
  @Operation(operationId = "deleteGroup")
  @RequirePrivilege("group-delete")
  public DataEnvelope<Void> delete(@PathVariable long groupId) {
    actionHandler.delete(groupId);
    return DataEnvelope.empty();
  }

  @PostMapping("/groups/{groupId}/copy")
  @Operation(operationId = "copyGroup")
  @RequirePrivilege("group-copy")
  public DataEnvelope<CreateGroupHandler.GroupView> copy(@PathVariable long groupId,
      @RequestBody GroupActionHandler.GroupCopyRequest body, jakarta.servlet.http.HttpServletRequest request) {
    Group created = actionHandler.copy(resolver.resolve(request), groupId, body);
    return DataEnvelope.of(queryService.detail(created.id()));
  }

  @GetMapping("/groups/{groupId}/privileges")
  @Operation(operationId = "getGroupPrivileges")
  @RequirePrivilege("group-view")
  public DataEnvelope<GroupPrivilegesView> getPrivileges(@PathVariable long groupId) {
    return DataEnvelope.of(new GroupPrivilegesView(queryService.privileges(groupId)));
  }

  @PutMapping("/groups/{groupId}/privileges")
  @Operation(operationId = "saveGroupPrivileges")
  @RequirePrivilege("group-priv-edit")
  public DataEnvelope<GroupPrivilegesView> savePrivileges(@PathVariable long groupId,
      @RequestBody GroupActionHandler.GroupPrivilegesRequest body, jakarta.servlet.http.HttpServletRequest request) {
    return DataEnvelope.of(new GroupPrivilegesView(actionHandler.savePrivileges(resolver.resolve(request), groupId, body)));
  }

  @GetMapping("/groups/{groupId}/members")
  @Operation(operationId = "getGroupMembers")
  @RequirePrivilege("group-view")
  public DataEnvelope<AccountQueryService.AccountList> members(@PathVariable long groupId) {
    List<AccountView> items = queryService.members(groupId).stream()
        .map(account -> net.zentao.org.app.CreateAccountHandler.toView(account, accountRepository.groupIdsOf(account.id())))
        .toList();
    return DataEnvelope.of(new AccountQueryService.AccountList(items, items.size()));
  }

  @PutMapping("/groups/{groupId}/members")
  @Operation(operationId = "saveGroupMembers")
  @RequirePrivilege("group-member-edit")
  public DataEnvelope<AccountQueryService.AccountList> saveMembers(@PathVariable long groupId,
      @RequestBody GroupActionHandler.GroupMembersRequest body, jakarta.servlet.http.HttpServletRequest request) {
    List<Long> ids = actionHandler.saveMembers(resolver.resolve(request), groupId, body);
    List<AccountView> items = ids.stream()
        .map(accountRepository::findActiveById)
        .flatMap(java.util.Optional::stream)
        .map(account -> net.zentao.org.app.CreateAccountHandler.toView(account, accountRepository.groupIdsOf(account.id())))
        .toList();
    return DataEnvelope.of(new AccountQueryService.AccountList(items, items.size()));
  }

  public record GroupPrivilegesView(List<String> codes) {}

}
