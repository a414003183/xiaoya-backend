package net.zentao.doc.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import net.zentao.doc.domain.DocAcl;
import net.zentao.doc.domain.DocSpace;
import net.zentao.doc.domain.DocSpaceRepository;
import net.zentao.org.api.AccountApi;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.product.api.ProductApi;
import net.zentao.project.api.ExecutionApi;
import net.zentao.project.api.ProjectApi;
import net.zentao.project.api.ProjectView;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 创建文档库（doc 卡 §3.1/§5）：type+归属对象；mine 每人至多一个（重复创建返回既有库，幂等 200）；
 * type 创建后不可改；isDefault 唯一置位。
 */
@Component
public class CreateDocSpaceHandler {

  public record DocSpaceCreateRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
          allowableValues = {"custom", "execution", "mine", "product", "project"}) String type,
      Long productId, Long projectId, Long executionId,
      @Schema(allowableValues = {"default", "open", "private"}) String acl, DocAcl whitelist,
      String description,
      @Schema(allowableValues = {"id_asc", "id_desc"}) String docSort, Boolean isDefault, Integer sort) {}

  private final DocSpaceRepository repository;
  private final ProductApi productApi;
  private final ProjectApi projectApi;
  private final ExecutionApi executionApi;
  private final AccountApi accountApi;

  public CreateDocSpaceHandler(DocSpaceRepository repository, ProductApi productApi, ProjectApi projectApi,
      ExecutionApi executionApi, AccountApi accountApi) {
    this.repository = repository;
    this.productApi = productApi;
    this.projectApi = projectApi;
    this.executionApi = executionApi;
    this.accountApi = accountApi;
  }

  @Transactional
  public DocSpace handle(SessionPrincipal actor, DocSpaceCreateRequest command) {
    String type = command.type() == null ? "custom" : command.type();
    Map<String, String> errors = DocFields.errors();
    DocFields.requireName(errors, "name", command.name());
    DocFields.oneOf(errors, "type", command.type(), DocFields.SPACE_TYPES);
    DocFields.oneOf(errors, "acl", command.acl(), DocFields.SPACE_ACLS);
    DocFields.oneOf(errors, "docSort", command.docSort(), DocFields.SPACE_DOC_SORTS);
    DocFields.validateAcl(errors, "whitelist", command.whitelist(), accountApi);
    validateOwnerPresence(errors, type, command);
    if ("custom".equals(type) && "default".equals(command.acl())) {
      errors.put("acl", "invalid");
    }
    DocFields.reject(errors);
    requireOwnerVisible(actor, type, command);

    // mine：每人至多一个，重复创建返回既有库（既有库字段一律不改）
    if ("mine".equals(type)) {
      DocSpace existing = repository.findMineByCreator(actor.account()).orElse(null);
      if (existing != null) {
        return existing;
      }
    }
    String acl = "mine".equals(type) ? "private" : command.acl() == null ? "open" : command.acl();
    Instant now = Instant.now();
    DocSpace space = repository.insert(new DocSpace(
        0,
        command.name() == null ? null : command.name().trim(),
        type,
        "product".equals(type) ? command.productId() : 0,
        "project".equals(type) || "execution".equals(type) ? command.projectId() : 0,
        "execution".equals(type) ? command.executionId() : 0,
        acl,
        "mine".equals(type) || command.whitelist() == null ? DocAcl.EMPTY : command.whitelist(),
        command.description(),
        command.docSort() == null ? "id_asc" : command.docSort(),
        command.isDefault() != null && command.isDefault(),
        command.sort() == null ? 0 : command.sort(),
        actor.account(),
        now,
        null,
        null,
        0));
    if (space.isDefault()) {
      repository.clearDefaultFlag(type, ownerId(space), space.id());
    }
    return space;
  }

  private void validateOwnerPresence(Map<String, String> errors, String type, DocSpaceCreateRequest command) {
    switch (type) {
      case "product" -> DocFields.requirePresent(errors, "productId", command.productId());
      case "project" -> DocFields.requirePresent(errors, "projectId", command.projectId());
      case "execution" -> {
        DocFields.requirePresent(errors, "projectId", command.projectId());
        DocFields.requirePresent(errors, "executionId", command.executionId());
      }
      default -> {
        // custom/mine：无归属对象
      }
    }
  }

  /** 归属对象必须存在且可见（product 卡 §7 / project 卡 §7）：不可见 → 40302，不存在 → 40401。 */
  private void requireOwnerVisible(SessionPrincipal actor, String type, DocSpaceCreateRequest command) {
    switch (type) {
      case "product" -> productApi.requireVisible(actor, command.productId());
      case "project" -> projectApi.requireVisible(actor, command.projectId(), "project");
      case "execution" -> {
        projectApi.requireVisible(actor, command.projectId(), "project");
        ProjectView execution = executionApi.requireExecution(actor, command.executionId());
        if (execution.parentId() != command.projectId()) {
          // execution 型由执行带出 projectId（doc 卡 §3.1）
          throw ApiException.validation(Map.of("executionId", "invalid"));
        }
      }
      default -> {
        // custom/mine：无归属对象
      }
    }
  }

  /** 归属对象 id（product/project/execution 型取对应列，其余 0）。 */
  static long ownerId(DocSpace space) {
    return switch (space.type()) {
      case "product" -> space.productId();
      case "project" -> space.projectId();
      case "execution" -> space.executionId();
      default -> 0;
    };
  }
}
