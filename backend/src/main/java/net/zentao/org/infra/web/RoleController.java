package net.zentao.org.infra.web;

import io.swagger.v3.oas.annotations.Operation;
import net.zentao.org.app.AccountRoleActionHandler;
import net.zentao.org.app.AccountRoleQueryService;
import net.zentao.org.app.CreateAccountRoleHandler;
import net.zentao.org.domain.AccountRole;
import net.zentao.platform.rbac.RequirePrivilege;
import net.zentao.platform.session.SessionResolver;
import net.zentao.platform.web.DataEnvelope;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 账号角色字典四端点（org 卡 §3.4；权限码 role-view / role-manage）。
 * 旧禅道「后台→自定义→用户→角色列表」的等价能力：可新增/改名（按语言）/排序/删除角色。
 */
@RestController
@RequestMapping("/api/v1")
public class RoleController {

  private final AccountRoleQueryService queryService;
  private final CreateAccountRoleHandler createHandler;
  private final AccountRoleActionHandler actionHandler;
  private final SessionResolver resolver;

  public RoleController(AccountRoleQueryService queryService, CreateAccountRoleHandler createHandler,
      AccountRoleActionHandler actionHandler, SessionResolver resolver) {
    this.queryService = queryService;
    this.createHandler = createHandler;
    this.actionHandler = actionHandler;
    this.resolver = resolver;
  }

  @GetMapping("/roles")
  @Operation(operationId = "listRoles")
  @RequirePrivilege("role-view")
  public DataEnvelope<AccountRoleQueryService.RoleList> list() {
    return DataEnvelope.of(queryService.list());
  }

  @PostMapping("/roles")
  @Operation(operationId = "createRole")
  @RequirePrivilege("role-manage")
  public DataEnvelope<AccountRoleQueryService.RoleView> create(
      @RequestBody CreateAccountRoleHandler.RoleCreateRequest body, jakarta.servlet.http.HttpServletRequest request) {
    AccountRole created = createHandler.handle(resolver.resolve(request).account(), body);
    return DataEnvelope.of(queryService.detail(created.code()));
  }

  @PatchMapping("/roles/{code}")
  @Operation(operationId = "updateRole")
  @RequirePrivilege("role-manage")
  public DataEnvelope<AccountRoleQueryService.RoleView> update(@PathVariable String code,
      @RequestBody AccountRoleActionHandler.RoleUpdateRequest body, jakarta.servlet.http.HttpServletRequest request) {
    actionHandler.update(resolver.resolve(request).account(), code, body);
    return DataEnvelope.of(queryService.detail(code));
  }

  @DeleteMapping("/roles/{code}")
  @Operation(operationId = "deleteRole")
  @RequirePrivilege("role-manage")
  public DataEnvelope<Void> delete(@PathVariable String code) {
    actionHandler.delete(code);
    return DataEnvelope.of(null);
  }
}
