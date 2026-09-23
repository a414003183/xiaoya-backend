package net.zentao.product.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import net.zentao.org.api.AccountApi;
import net.zentao.platform.meta.FieldDefValidator;
import net.zentao.platform.activity.ActivityRecorder;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.product.domain.Product;
import net.zentao.product.domain.ProductRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 创建产品（product 卡 §3.1/§5）：acl=custom 时 whitelist 必填且 ≤100；引用账号必须存在。 */
@Component
public class CreateProductHandler {

  private final ProductRepository repository;
  private final AccountApi accountApi;
  private final ActivityRecorder activityRecorder;
  private final FieldDefValidator fieldDefValidator;

  public CreateProductHandler(ProductRepository repository, AccountApi accountApi, ActivityRecorder activityRecorder,
      FieldDefValidator fieldDefValidator) {
    this.fieldDefValidator = fieldDefValidator;
    this.repository = repository;
    this.accountApi = accountApi;
    this.activityRecorder = activityRecorder;
  }

  public record ProductCreateRequest(
      Long programId, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name, String code,
      @Schema(allowableValues = {"branch", "normal", "platform"}) String type,
      String po, String qd, String rd, @Schema(allowableValues = {"custom", "public", "private"}) String acl,
      List<String> whitelist, String description, Integer sort, Map<String, Object> customFields) {}

  @Transactional
  public Product handle(SessionPrincipal actor, ProductCreateRequest command) {
    if (command.name() == null || command.name().trim().isEmpty()) {
      throw ApiException.validation(Map.of("name", "required"));
    }
    ProductFields.validate(command.name(), command.code(), command.type(), command.acl(), command.whitelist(),
        command.po(), command.qd(), command.rd(), accountApi);
    String acl = command.acl() == null ? "public" : command.acl();
    List<String> whitelist = command.whitelist() == null ? List.of() : command.whitelist();
    if ("custom".equals(acl) && whitelist.isEmpty()) {
      throw ApiException.validation(Map.of("whitelist", "required"));
    }
    Instant now = Instant.now();
    fieldDefValidator.validate("product", command.customFields() == null ? java.util.Map.of() : command.customFields(), true);
    Product product = repository.insert(new Product(
        0,
        command.programId() == null ? 0 : command.programId(),
        command.name().trim(),
        command.code(),
        command.type() == null ? "normal" : command.type(),
        "normal",
        command.description(),
        command.po(),
        command.qd(),
        command.rd(),
        acl,
        whitelist,
        command.sort() == null ? 0 : command.sort(),
        command.customFields(),
        actor.account(),
        now,
        null,
        null,
        null,
        0));
    activityRecorder.record(actor.account(), "product", product.id(), "created", null, null);
    return product;
  }
}
