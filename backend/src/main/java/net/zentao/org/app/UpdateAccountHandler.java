package net.zentao.org.app;

import java.util.List;
import net.zentao.org.domain.Account;
import net.zentao.org.domain.AccountRepository;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 更新账号资料（org 卡 §5：account 不可改；groupIds 全量替换；lockVersion 乐观锁）。 */
@Component
public class UpdateAccountHandler {

  private final AccountRepository repository;
  private final AccountRoleValidator roleValidator;

  public UpdateAccountHandler(AccountRepository repository, AccountRoleValidator roleValidator) {
    this.repository = repository;
    this.roleValidator = roleValidator;
  }

  /**
   * contract AccountUpdateRequest；account 字段仅用于检测“出现在更新体”（→ 42201，org 卡 §3.1 不可改），
   * 对契约 schema 隐藏。
   */
  public record AccountUpdateRequest(
      @Schema(hidden = true) String account,
      String realName, String nickname,
      @Schema(maxLength = 16, description = "账号角色码，必须存在于角色字典（GET /roles）") String role,
      Long departmentId, String email, String mobile, String phone,
      @Schema(allowableValues = {"m", "f"}) String gender, java.time.LocalDate birthday,
      java.time.LocalDate joinedAt, Long avatarFileId, List<Long> groupIds, Integer lockVersion) {}

  @Transactional
  public Account handle(SessionPrincipal actor, long accountId, AccountUpdateRequest command) {
    if (command.account() != null) {
      throw ApiException.validation(java.util.Map.of("account", "readonly"));
    }
    Account account = repository.findActiveById(accountId)
        .orElseThrow(() -> ApiException.notFound("账号"));
    if (command.lockVersion() == null || command.lockVersion() != account.lockVersion()) {
      throw ApiException.lockConflict("数据已被他人修改，请刷新。");
    }
    roleValidator.require(command.role());
    account.updateProfile(command.realName(), command.nickname(), command.role(), command.departmentId(),
        command.email(), command.mobile(), command.phone(), command.gender(), command.birthday(),
        command.joinedAt(), command.avatarFileId());
    account.markUpdatedBy(actor == null ? null : actor.account());
    Account saved = repository.update(account).orElseThrow(() -> ApiException.lockConflict("数据已被他人修改，请刷新。"));
    if (command.groupIds() != null) {
      List<Long> missing = repository.findMissingGroupIds(command.groupIds());
      if (!missing.isEmpty()) {
        throw ApiException.validation(java.util.Map.of("groupIds", "notFound"));
      }
      repository.replaceGroups(accountId, command.groupIds());
    }
    return saved;
  }

}
