package net.zentao.project.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import net.zentao.org.api.AccountApi;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.project.domain.AclEntryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 项目白名单（project 卡 §2/§5 whitelist 行）：acl_entry 单源读 + 全量替换（diff 落库）。
 * 处理器按 objectType 通用（program|project|execution），当前契约只开放项目端点的 GET/POST。
 */
@Component
public class ReplaceWhitelistHandler {

  private final AclEntryRepository aclEntryRepository;
  private final ProjectQueryService projectQueryService;
  private final AccountApi accountApi;

  public ReplaceWhitelistHandler(AclEntryRepository aclEntryRepository, ProjectQueryService projectQueryService,
      AccountApi accountApi) {
    this.aclEntryRepository = aclEntryRepository;
    this.projectQueryService = projectQueryService;
    this.accountApi = accountApi;
  }

  public record WhitelistRequest(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> accounts) {}

  public WhitelistRequest list(SessionPrincipal actor, String objectType, long objectId) {
    projectQueryService.requireVisible(actor, objectType, objectId);
    return new WhitelistRequest(aclEntryRepository.accounts(objectType, objectId));
  }

  /** 全量替换（账号不存在 → 42201）；替换后 acl=private 的可见集即时生效（§7）。 */
  @Transactional
  public WhitelistRequest replace(SessionPrincipal actor, String objectType, long objectId,
      WhitelistRequest command) {
    projectQueryService.requireVisible(actor, objectType, objectId);
    List<String> accounts = command == null ? null : command.accounts();
    if (accounts == null) {
      throw ApiException.validation(Map.of("accounts", "required"));
    }
    List<String> distinct = accounts.stream().distinct().toList();
    if (!accountApi.missingAccounts(distinct).isEmpty()) {
      throw ApiException.validation(Map.of("accounts", "notFound"));
    }
    aclEntryRepository.replace(objectType, objectId, distinct);
    return new WhitelistRequest(aclEntryRepository.accounts(objectType, objectId));
  }
}
