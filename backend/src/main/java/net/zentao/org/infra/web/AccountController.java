package net.zentao.org.infra.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import net.zentao.org.app.AccountActionHandler;
import net.zentao.org.app.AccountQueryService;
import net.zentao.org.app.PasswordActionHandler;
import net.zentao.org.app.BatchCreateAccountHandler;
import net.zentao.org.app.CreateAccountHandler;
import net.zentao.org.app.UpdateAccountHandler;
import net.zentao.org.domain.Account;
import net.zentao.org.domain.AccountRepository;
import net.zentao.platform.activity.ActivityQueryService;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.rbac.RequirePrivilege;
import net.zentao.platform.session.AccountView;
import net.zentao.platform.session.SessionResolver;
import net.zentao.platform.web.DataEnvelope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** 账号端点（org 卡 §5：列表/创建/批量/详情/PATCH + 动态流；动作端点 T-11 补充）。 */
@RestController
@RequestMapping("/api/v1")
public class AccountController {

  private final AccountQueryService queryService;
  private final CreateAccountHandler createHandler;
  private final BatchCreateAccountHandler batchHandler;
  private final UpdateAccountHandler updateHandler;
  private final AccountActionHandler actionHandler;
  private final PasswordActionHandler passwordHandler;
  private final AccountRepository repository;
  private final ActivityQueryService activityQueryService;
  private final SessionResolver resolver;

  public AccountController(AccountQueryService queryService, CreateAccountHandler createHandler,
      BatchCreateAccountHandler batchHandler, UpdateAccountHandler updateHandler,
      AccountActionHandler actionHandler, PasswordActionHandler passwordHandler, AccountRepository repository,
      ActivityQueryService activityQueryService, SessionResolver resolver) {
    this.queryService = queryService;
    this.createHandler = createHandler;
    this.batchHandler = batchHandler;
    this.updateHandler = updateHandler;
    this.actionHandler = actionHandler;
    this.passwordHandler = passwordHandler;
    this.repository = repository;
    this.activityQueryService = activityQueryService;
    this.resolver = resolver;
  }

  @GetMapping("/accounts")
  @Operation(operationId = "listAccounts")
  @RequirePrivilege("account-view")
  public DataEnvelope<AccountQueryService.AccountList> list(HttpServletRequest request) {
    return DataEnvelope.of(queryService.page(resolver.resolve(request), request.getParameterMap()));
  }

  @PostMapping("/accounts")
  @Operation(operationId = "createAccount")
  @RequirePrivilege("account-create")
  public DataEnvelope<AccountView> create(@RequestBody CreateAccountHandler.AccountCreateRequest body,
      HttpServletRequest request) {
    Account account = createHandler.handle(resolver.resolve(request), body);
    return DataEnvelope.of(CreateAccountHandler.toView(account, repository.groupIdsOf(account.id())));
  }

  @PostMapping("/accounts/batch")
  @Operation(operationId = "batchCreateAccounts")
  @RequirePrivilege("account-create")
  public DataEnvelope<AccountBatchCreateResult> batch(@RequestBody AccountBatchCreateRequest body, HttpServletRequest request) {
    var result = batchHandler.handle(resolver.resolve(request), body.items());
    return DataEnvelope.of(new AccountBatchCreateResult(result.results()));
  }

  @GetMapping("/accounts/{accountId}")
  @Operation(operationId = "getAccount")
  @RequirePrivilege("account-view")
  public DataEnvelope<AccountView> detail(@PathVariable long accountId) {
    Account account = repository.findActiveById(accountId).orElseThrow(() -> ApiException.notFound("账号"));
    return DataEnvelope.of(CreateAccountHandler.toView(account, repository.groupIdsOf(accountId)));
  }

  @PatchMapping("/accounts/{accountId}")
  @Operation(operationId = "updateAccount")
  @RequirePrivilege("account-edit")
  public DataEnvelope<AccountView> update(@PathVariable long accountId,
      @RequestBody UpdateAccountHandler.AccountUpdateRequest body, HttpServletRequest request) {
    Account account = updateHandler.handle(resolver.resolve(request), accountId, body);
    return DataEnvelope.of(CreateAccountHandler.toView(account, repository.groupIdsOf(accountId)));
  }

  @GetMapping("/accounts/{accountId}/activities")
  @Operation(operationId = "listAccountActivities")
  @RequirePrivilege("account-view")
  public DataEnvelope<ActivityQueryService.ActivityList> activities(
      @PathVariable long accountId,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) Long beforeId,
      HttpServletRequest request) {
    return DataEnvelope.of(activityQueryService.list("account", accountId, null, limit, beforeId));
  }

  @PostMapping("/accounts/{accountId}/password")
  @Operation(operationId = "changeAccountPassword")
  public DataEnvelope<AccountView> changePassword(@PathVariable long accountId,
      @RequestBody PasswordActionHandler.AccountPasswordRequest body, HttpServletRequest request) {
    return DataEnvelope.of(passwordHandler.changePassword(resolver.resolve(request), accountId, body));
  }

  @PostMapping("/accounts/{accountId}/reset-password")
  @Operation(operationId = "resetAccountPassword")
  @RequirePrivilege("account-reset-password")
  public DataEnvelope<AccountView> resetPassword(@PathVariable long accountId,
      @RequestBody PasswordActionHandler.AccountResetPasswordRequest body, HttpServletRequest request) {
    return DataEnvelope.of(passwordHandler.resetPassword(resolver.resolve(request), accountId, body));
  }

  @PostMapping("/accounts/{accountId}/disable")
  @Operation(operationId = "disableAccount")
  @RequirePrivilege("account-disable")
  public DataEnvelope<AccountView> disable(@PathVariable long accountId,
      @RequestBody(required = false) AccountActionHandler.AccountActionRequest body, HttpServletRequest request) {
    return DataEnvelope.of(actionHandler.disable(resolver.resolve(request), accountId,
        body == null ? new AccountActionHandler.AccountActionRequest(null) : body));
  }

  @PostMapping("/accounts/{accountId}/enable")
  @Operation(operationId = "enableAccount")
  @RequirePrivilege("account-enable")
  public DataEnvelope<AccountView> enable(@PathVariable long accountId,
      @RequestBody(required = false) AccountActionHandler.AccountActionRequest body, HttpServletRequest request) {
    return DataEnvelope.of(actionHandler.enable(resolver.resolve(request), accountId,
        body == null ? new AccountActionHandler.AccountActionRequest(null) : body));
  }

  @PostMapping("/accounts/{accountId}/unlock")
  @Operation(operationId = "unlockAccount")
  @RequirePrivilege("account-unlock")
  public DataEnvelope<AccountView> unlock(@PathVariable long accountId, HttpServletRequest request) {
    return DataEnvelope.of(actionHandler.unlock(resolver.resolve(request), accountId));
  }

  @PostMapping("/accounts/{accountId}/delete")
  @Operation(operationId = "deleteAccount")
  @RequirePrivilege("account-delete")
  public DataEnvelope<AccountView> remove(@PathVariable long accountId,
      @RequestBody(required = false) AccountActionHandler.AccountActionRequest body, HttpServletRequest request) {
    return DataEnvelope.of(actionHandler.delete(resolver.resolve(request), accountId,
        body == null ? new AccountActionHandler.AccountActionRequest(null) : body));
  }


  public record AccountBatchCreateRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<CreateAccountHandler.AccountCreateRequest> items) {}

  public record AccountBatchCreateResult(List<BatchCreateAccountHandler.BatchResultItem> results) {}
}
