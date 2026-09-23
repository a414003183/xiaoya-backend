package net.zentao.product.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import net.zentao.org.api.AccountApi;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.meta.FieldDefValidator;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.product.api.ProductApi;
import net.zentao.product.domain.Product;
import net.zentao.product.domain.ProductRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 部分更新产品（product 卡 §5 PATCH 白名单；lockVersion 不符 → 40901）。 */
@Component
public class UpdateProductHandler {

  private final ProductRepository repository;
  private final ProductApi productApi;
  private final AccountApi accountApi;
  private final FieldDefValidator fieldDefValidator;

  public UpdateProductHandler(ProductRepository repository, ProductApi productApi, AccountApi accountApi,
      FieldDefValidator fieldDefValidator) {
    this.fieldDefValidator = fieldDefValidator;
    this.repository = repository;
    this.productApi = productApi;
    this.accountApi = accountApi;
  }

  public record ProductUpdateRequest(
      String name, String code, @Schema(allowableValues = {"branch", "normal", "platform"}) String type,
      Long programId, String po, String qd, String rd,
      @Schema(allowableValues = {"custom", "public", "private"}) String acl,
      List<String> whitelist, String description, Integer sort, Map<String, Object> customFields,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer lockVersion) {}

  @Transactional
  public Product handle(SessionPrincipal actor, long productId, ProductUpdateRequest command) {
    Product product = ProductGuard.requireVisible(repository, productApi, actor, productId);
    if (command.lockVersion() == null || command.lockVersion() != product.lockVersion()) {
      throw ApiException.lockConflict();
    }
    ProductFields.validate(command.name(), command.code(), command.type(), command.acl(), command.whitelist(),
        command.po(), command.qd(), command.rd(), accountApi);
    fieldDefValidator.validate("product", command.customFields() == null ? java.util.Map.of() : command.customFields(), false);
    product.update(command.name(), command.code(), command.type(), command.programId(), command.po(), command.qd(),
        command.rd(), command.acl(), command.whitelist(), command.description(), command.sort(),
        command.customFields());
    if ("custom".equals(product.acl()) && product.whitelist().isEmpty()) {
      throw ApiException.validation(Map.of("whitelist", "required"));
    }
    product.markUpdatedBy(actor.account());
    return repository.update(product).orElseThrow(() -> ApiException.lockConflict());
  }
}
