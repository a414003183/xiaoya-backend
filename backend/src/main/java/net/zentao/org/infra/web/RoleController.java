package net.zentao.org.infra.web;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import net.zentao.org.app.AccountQueryService;
import net.zentao.org.app.CreateAccountHandler;
import net.zentao.org.app.CreateRoleHandler;
import net.zentao.org.app.RoleActionHandler;
import net.zentao.org.app.RoleQueryService;
import net.zentao.org.domain.Account;
import net.zentao.org.domain.AccountRepository;
import net.zentao.platform.audit.Audit;
import net.zentao.platform.audit.AuditDiff;
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

/**
 * 角色端点（T23 统一实体）：一个角色 = 权限码 + 成员 + 数据权限，故权限矩阵与成员挂在它下面。
 *
 * <p>权限码 `role-view`（读）/`role-create`/`role-edit`/`role-delete`/`role-copy`/`role-priv-edit`/
 * `role-member-edit`（写）——旧 `group-*` 与 `role-manage` 已随实体统一退场。
 */
@RestController
@RequestMapping("/api/v1")
public class RoleController {

  private final RoleQueryService queryService;
  private final CreateRoleHandler createHandler;
  private final RoleActionHandler actionHandler;
  private final AccountRepository accountRepository;
  private final SessionResolver resolver;

  public RoleController(RoleQueryService queryService, CreateRoleHandler createHandler,
      RoleActionHandler actionHandler, AccountRepository accountRepository, SessionResolver resolver) {
    this.queryService = queryService;
    this.createHandler = createHandler;
    this.actionHandler = actionHandler;
    this.accountRepository = accountRepository;
    this.resolver = resolver;
  }

  @GetMapping("/roles")
  @Operation(operationId = "listRoles")
  @RequirePrivilege("role-view")
  public DataEnvelope<RoleQueryService.RoleList> list() {
    return DataEnvelope.of(queryService.list());
  }

  @PostMapping("/roles")
  @Operation(operationId = "createRole")
  @RequirePrivilege("role-create")
  @Audit(action = "role-create", objectType = "role")
  public DataEnvelope<RoleQueryService.RoleView> create(@RequestBody CreateRoleHandler.RoleCreateRequest body,
      HttpServletRequest request) {
    return DataEnvelope.of(queryService.detail(createHandler.handle(resolver.resolve(request).account(), body).id()));
  }

  @GetMapping("/roles/{roleId}")
  @Operation(operationId = "getRole")
  @RequirePrivilege("role-view")
  public DataEnvelope<RoleQueryService.RoleView> detail(@PathVariable long roleId) {
    return DataEnvelope.of(queryService.detail(roleId));
  }

  @PatchMapping("/roles/{roleId}")
  @Operation(operationId = "updateRole")
  @RequirePrivilege("role-edit")
  @Audit(action = "role-update", objectType = "role")
  @AuditDiff(objectType = "role")
  public DataEnvelope<RoleQueryService.RoleView> update(@PathVariable long roleId,
      @RequestBody RoleActionHandler.RoleUpdateRequest body, HttpServletRequest request) {
    actionHandler.update(resolver.resolve(request), roleId, body);
    return DataEnvelope.of(queryService.detail(roleId));
  }

  @DeleteMapping("/roles/{roleId}")
  @Operation(operationId = "deleteRole")
  @RequirePrivilege("role-delete")
  @Audit(action = "role-delete", objectType = "role")
  @AuditDiff(objectType = "role")
  public DataEnvelope<Void> delete(@PathVariable long roleId) {
    actionHandler.delete(roleId);
    return DataEnvelope.empty();
  }

  @PostMapping("/roles/{roleId}/copy")
  @Operation(operationId = "copyRole")
  @RequirePrivilege("role-copy")
  @Audit(action = "role-copy", objectType = "role")
  public DataEnvelope<RoleQueryService.RoleView> copy(@PathVariable long roleId,
      @RequestBody RoleActionHandler.RoleCopyRequest body, HttpServletRequest request) {
    return DataEnvelope.of(queryService.detail(actionHandler.copy(resolver.resolve(request), roleId, body).id()));
  }

  @GetMapping("/roles/{roleId}/privileges")
  @Operation(operationId = "getRolePrivileges")
  @RequirePrivilege("role-view")
  public DataEnvelope<RolePrivilegesView> privileges(@PathVariable long roleId) {
    return DataEnvelope.of(new RolePrivilegesView(queryService.privileges(roleId)));
  }

  @PutMapping("/roles/{roleId}/privileges")
  @Operation(operationId = "saveRolePrivileges")
  @RequirePrivilege("role-priv-edit")
  @Audit(action = "role-grant", objectType = "role")
  @AuditDiff(objectType = "role")
  public DataEnvelope<RolePrivilegesView> savePrivileges(@PathVariable long roleId,
      @RequestBody RoleActionHandler.RolePrivilegesRequest body, HttpServletRequest request) {
    return DataEnvelope.of(
        new RolePrivilegesView(actionHandler.savePrivileges(resolver.resolve(request), roleId, body)));
  }

  @GetMapping("/roles/{roleId}/members")
  @Operation(operationId = "getRoleMembers")
  @RequirePrivilege("role-view")
  public DataEnvelope<AccountQueryService.AccountList> members(@PathVariable long roleId) {
    return DataEnvelope.of(accountList(queryService.members(roleId)));
  }

  @PutMapping("/roles/{roleId}/members")
  @Operation(operationId = "saveRoleMembers")
  @RequirePrivilege("role-member-edit")
  @Audit(action = "role-member-update", objectType = "role")
  @AuditDiff(objectType = "role")
  public DataEnvelope<AccountQueryService.AccountList> saveMembers(@PathVariable long roleId,
      @RequestBody RoleActionHandler.RoleMembersRequest body, HttpServletRequest request) {
    List<Long> ids = actionHandler.saveMembers(resolver.resolve(request), roleId, body);
    List<Account> accounts = ids.stream()
        .map(accountRepository::findActiveById)
        .flatMap(java.util.Optional::stream)
        .toList();
    return DataEnvelope.of(accountList(accounts));
  }

  public record RolePrivilegesView(List<String> codes) {}

  private AccountQueryService.AccountList accountList(List<Account> accounts) {
    List<AccountView> items = accounts.stream()
        .map(account -> CreateAccountHandler.toView(account, accountRepository.roleIdsOf(account.id())))
        .toList();
    return new AccountQueryService.AccountList(items, items.size());
  }
}
