package net.zentao.doc.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import net.zentao.doc.domain.DocAcl;
import net.zentao.doc.domain.DocSpace;
import net.zentao.doc.domain.DocSpaceRepository;
import net.zentao.org.api.AccountApi;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 部分更新文档库（doc 卡 §5 PATCH 白名单；type 不可改，mine 恒 private，custom 无 default）；
 * lockVersion 不符 → 40901；isDefault 置 true 时同归属旧主库自动落 false。
 */
@Component
public class UpdateDocSpaceHandler {

  public record DocSpaceUpdateRequest(
      String name, String description,
      @Schema(allowableValues = {"default", "open", "private"}) String acl, DocAcl whitelist,
      @Schema(allowableValues = {"id_asc", "id_desc"}) String docSort, Boolean isDefault,
      Integer sort, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer lockVersion) {}

  private final DocSpaceRepository repository;
  private final DocAccess access;
  private final AccountApi accountApi;

  public UpdateDocSpaceHandler(DocSpaceRepository repository, DocAccess access, AccountApi accountApi) {
    this.repository = repository;
    this.access = access;
    this.accountApi = accountApi;
  }

  @Transactional
  public DocSpace handle(SessionPrincipal actor, long spaceId, DocSpaceUpdateRequest command) {
    DocSpace space = access.requireSpace(actor, spaceId);
    if (command.lockVersion() == null || command.lockVersion() != space.lockVersion()) {
      throw ApiException.lockConflict("数据已被他人修改，请刷新后重试。");
    }
    Map<String, String> errors = DocFields.errors();
    // PATCH 部分更新（03 §1 null=不修改；契约 DocSpaceUpdateRequest 仅 lockVersion 必填）：name 传值才校验
    if (command.name() != null) {
      DocFields.requireName(errors, "name", command.name());
    }
    DocFields.oneOf(errors, "acl", command.acl(), DocFields.SPACE_ACLS);
    DocFields.oneOf(errors, "docSort", command.docSort(), DocFields.SPACE_DOC_SORTS);
    DocFields.validateAcl(errors, "whitelist", command.whitelist(), accountApi);
    if ("custom".equals(space.type()) && "default".equals(command.acl())) {
      errors.put("acl", "invalid");
    }
    DocFields.reject(errors);

    space.update(command.name(), command.description(), command.acl(), command.whitelist(), command.docSort(),
        command.isDefault(), command.sort());
    if ("mine".equals(space.type())) {
      space.forceMineAcl();
    }
    space.markUpdatedBy(actor.account());
    DocSpace saved = repository.update(space)
        .orElseThrow(() -> ApiException.lockConflict("数据已被他人修改，请刷新后重试。"));
    if (saved.isDefault()) {
      repository.clearDefaultFlag(saved.type(), CreateDocSpaceHandler.ownerId(saved), saved.id());
    }
    return saved;
  }
}
