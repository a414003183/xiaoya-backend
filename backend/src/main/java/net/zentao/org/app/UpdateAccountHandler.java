package net.zentao.org.app;

import java.util.List;
import net.zentao.org.domain.Account;
import net.zentao.org.domain.AccountRepository;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 更新账号资料（org 卡 §5：account 不可改；roleIds 全量替换；lockVersion 乐观锁）。 */
@Component
public class UpdateAccountHandler {

  private final AccountRepository repository;

  public UpdateAccountHandler(AccountRepository repository) {
    this.repository = repository;
  }

  /**
   * contract AccountUpdateRequest；account 字段仅用于检测“出现在更新体”（→ 42201，org 卡 §3.1 不可改），
   * 对契约 schema 隐藏。
   */
  public record AccountUpdateRequest(
      @Schema(hidden = true) String account,
      String realName, String nickname,
      Long departmentId, String email, String mobile, String phone,
      @Schema(allowableValues = {"m", "f"}) String gender, java.time.LocalDate birthday,
      java.time.LocalDate joinedAt, Long avatarFileId, List<Long> roleIds,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer lockVersion) {}

  @Transactional
  public Account handle(SessionPrincipal actor, long accountId, AccountUpdateRequest command) {
    if (command.account() != null) {
      throw ApiException.validation(java.util.Map.of("account", "readonly"));
    }
    Account account = repository.findActiveById(accountId)
        .orElseThrow(() -> ApiException.notFound("entity.account"));
    if (command.lockVersion() == null || command.lockVersion() != account.lockVersion()) {
      throw ApiException.lockConflict();
    }
    account.updateProfile(command.realName(), command.nickname(), command.departmentId(),
        command.email(), command.mobile(), command.phone(), command.gender(), command.birthday(),
        command.joinedAt(), command.avatarFileId());
    account.markUpdatedBy(actor == null ? null : actor.account());
    Account saved = repository.update(account).orElseThrow(() -> ApiException.lockConflict());
    if (command.roleIds() != null) {
      List<Long> missing = repository.findMissingRoleIds(command.roleIds());
      if (!missing.isEmpty()) {
        throw ApiException.validation(java.util.Map.of("roleIds", "notFound"));
      }
      repository.replaceRoles(accountId, command.roleIds());
    }
    return saved;
  }

}
