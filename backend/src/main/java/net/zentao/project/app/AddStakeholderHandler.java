package net.zentao.project.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.zentao.org.api.AccountApi;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.project.app.StakeholderQueryService.StakeholderView;
import net.zentao.project.domain.Stakeholder;
import net.zentao.project.domain.StakeholderRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 添加干系人（project 卡 §3.8/§5：program 与 project 两类对象共用；同对象重复 account → 42201）。
 * 软删后的同 account 再次添加走仓储复活路径（unique 索引不允许第二行）。
 */
@Component
public class AddStakeholderHandler {

  private static final List<String> TYPES = List.of("inside", "outside");

  private final StakeholderRepository repository;
  private final ProjectQueryService projectQueryService;
  private final AccountApi accountApi;

  public AddStakeholderHandler(StakeholderRepository repository, ProjectQueryService projectQueryService,
      AccountApi accountApi) {
    this.repository = repository;
    this.projectQueryService = projectQueryService;
    this.accountApi = accountApi;
  }

  public record StakeholderCreateRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String account,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"inside", "outside"}) String type,
      Boolean isKey, @jakarta.validation.constraints.Size(max = 30) String source) {}

  @Transactional
  public StakeholderView handle(SessionPrincipal actor, String objectType, long objectId,
      StakeholderCreateRequest command) {
    projectQueryService.requireVisible(actor, objectType, objectId);
    String account = command == null ? null : command.account();
    String type = command == null ? null : command.type();
    String source = command == null ? null : command.source();
    Map<String, String> errors = new LinkedHashMap<>();
    // account 的「必填 | 引用存在」else-if 链与 type 取值域是守卫语义（required/notFound/invalid 三码同域）——不注解化。
    if (account == null || account.isBlank()) {
      errors.put("account", "required");
    } else if (!accountApi.missingAccounts(List.of(account)).isEmpty()) {
      errors.put("account", "notFound");
    }
    if (!TYPES.contains(type)) {
      errors.put("type", "invalid");
    }
    if (!errors.isEmpty()) {
      throw ApiException.validation(errors);
    }
    if (repository.findActiveByAccount(objectType, objectId, account).isPresent()) {
      throw ApiException.validation(Map.of("account", "duplicated"));
    }
    Stakeholder inserted = repository.insert(new Stakeholder(0, objectType, objectId, account, type,
        Boolean.TRUE.equals(command.isKey()), source, actor.account(), Instant.now(), null, null));
    return StakeholderView.of(inserted);
  }
}
