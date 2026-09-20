package net.zentao.product.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.zentao.org.api.AccountApi;
import net.zentao.platform.meta.FieldDefValidator;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.rbac.PrivilegeChecker;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.web.BatchActionRequest;
import net.zentao.platform.web.BatchActionResult;
import net.zentao.platform.workflow.WorkflowEngine;
import net.zentao.product.api.ProductApi;
import net.zentao.product.domain.Product;
import net.zentao.product.domain.ProductRepository;
import org.springframework.stereotype.Component;

/**
 * 产品批量动作（product 卡 §5：action ∈ close|activate|edit，逐项结果部分成功）。
 * 动作码在请求级校验（无码 40301）；逐项成败与失败原因走 error 列（`<code>:<message>`）。
 */
@Component
public class ProductBatchHandler {

  private final ProductRepository repository;
  private final ProductApi productApi;
  private final AccountApi accountApi;
  private final PrivilegeChecker checker;
  private final WorkflowEngine engine;
  private final FieldDefValidator fieldDefValidator;

  public ProductBatchHandler(ProductRepository repository, ProductApi productApi, AccountApi accountApi,
      PrivilegeChecker checker, WorkflowEngine engine,
      FieldDefValidator fieldDefValidator) {
    this.fieldDefValidator = fieldDefValidator;
    this.repository = repository;
    this.productApi = productApi;
    this.accountApi = accountApi;
    this.checker = checker;
    this.engine = engine;
  }

  /** 批量 edit 的可改字段（product 卡 §5 PATCH 白名单子集；lockVersion 可选，给了就校验）。 */
  public record ProductBatchParams(
      String name, String code, String type, Long programId, String po, String qd, String rd, String acl,
      List<String> whitelist, String description, Integer sort, Map<String, Object> customFields,
      Integer lockVersion) {}

  public BatchActionResult handle(SessionPrincipal actor, BatchActionRequest command) {
    if (command.ids() == null || command.ids().isEmpty()) {
      throw ApiException.validation(Map.of("ids", "required"));
    }
    String action = command.action() == null ? "" : command.action();
    String code = switch (action) {
      case "close" -> "product-close";
      case "activate" -> "product-activate";
      case "edit" -> "product-edit";
      default -> null;
    };
    if (code == null) {
      throw ApiException.badRequest("不支持的批量动作：" + action);
    }
    if (!checker.hasPrivilege(actor, code)) {
      throw ApiException.forbidden("无权限：" + code);
    }
    ProductBatchParams params = toParams(command.params());
    List<BatchActionResult.Item> results = new ArrayList<>();
    for (Long id : command.ids()) {
      try {
        Product saved = "edit".equals(action) ? edit(actor, id, params) : fire(actor, id, action);
        results.add(BatchActionResult.ok(saved.id()));
      } catch (ApiException e) {
        results.add(BatchActionResult.failed(id, e.errorCode().code() + ":" + e.getMessage()));
      }
    }
    return new BatchActionResult(results);
  }

  private Product fire(SessionPrincipal actor, long productId, String action) {
    Product product = ProductGuard.requireVisible(repository, productApi, actor, productId);
    engine.fire(new WorkflowTargets.ProductTarget(product, actor.account()), action, null);
    product.markUpdatedBy(actor.account());
    return save(product);
  }

  private Product edit(SessionPrincipal actor, long productId, ProductBatchParams params) {
    Product product = ProductGuard.requireVisible(repository, productApi, actor, productId);
    if (params.lockVersion() != null && params.lockVersion() != product.lockVersion()) {
      throw ApiException.lockConflict("数据已被他人修改，请刷新后重试。");
    }
    ProductFields.validate(params.name(), params.code(), params.type(), params.acl(), params.whitelist(),
        params.po(), params.qd(), params.rd(), accountApi);
    fieldDefValidator.validate("product", params.customFields() == null ? java.util.Map.of() : params.customFields(), false);
    product.update(params.name(), params.code(), params.type(), params.programId(), params.po(), params.qd(),
        params.rd(), params.acl(), params.whitelist(), params.description(), params.sort(), params.customFields());
    if ("custom".equals(product.acl()) && product.whitelist().isEmpty()) {
      throw ApiException.validation(Map.of("whitelist", "required"));
    }
    product.markUpdatedBy(actor.account());
    return save(product);
  }

  private Product save(Product product) {
    return repository.update(product).orElseThrow(() -> ApiException.lockConflict("数据已被他人修改，请刷新后重试。"));
  }

  private static ProductBatchParams toParams(Map<String, Object> raw) {
    if (raw == null) {
      return new ProductBatchParams(null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
    return new ProductBatchParams(
        text(raw, "name"), text(raw, "code"), text(raw, "type"), longNumber(raw, "programId"), text(raw, "po"),
        text(raw, "qd"), text(raw, "rd"), text(raw, "acl"), texts(raw, "whitelist"), text(raw, "description"),
        number(raw, "sort"), map(raw, "customFields"), number(raw, "lockVersion"));
  }

  private static Long longNumber(Map<String, Object> raw, String key) {
    Object value = raw.get(key);
    if (value instanceof Number number) {
      return number.longValue();
    }
    return value == null ? null : Long.valueOf(String.valueOf(value));
  }

  private static String text(Map<String, Object> raw, String key) {
    Object value = raw.get(key);
    return value == null ? null : String.valueOf(value);
  }

  private static Integer number(Map<String, Object> raw, String key) {
    Object value = raw.get(key);
    if (value instanceof Number number) {
      return number.intValue();
    }
    return value == null ? null : Integer.valueOf(String.valueOf(value));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Map<String, Object> raw, String key) {
    Object value = raw.get(key);
    return value instanceof Map<?, ?> ? (Map<String, Object>) value : null;
  }

  private static List<String> texts(Map<String, Object> raw, String key) {
    Object value = raw.get(key);
    return value instanceof List<?> list ? list.stream().map(String::valueOf).toList() : null;
  }
}
