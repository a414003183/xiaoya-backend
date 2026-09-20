package net.zentao.product.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import net.zentao.product.domain.Product;

/** 产品视图（contract：ProductView；product 卡 §3.1 读侧字段）。 */
public record ProductView(
    long id,
    long programId,
    String name,
    String code,
    @Schema(allowableValues = {"branch", "normal", "platform"}) String type,
    @Schema(allowableValues = {"closed", "normal"}) String status,
    String description,
    String po,
    String qd,
    String rd,
    @Schema(allowableValues = {"custom", "private", "public"}) String acl,
    List<String> whitelist,
    int sort,
    Map<String, Object> customFields,
    String createdBy,
    Instant createdAt,
    String updatedBy,
    Instant updatedAt,
    Instant closedAt,
    int lockVersion) {

  public static ProductView of(Product product) {
    return new ProductView(product.id(), product.programId(), product.name(), product.code(), product.type(),
        product.status(), product.description(), product.po(), product.qd(), product.rd(), product.acl(),
        product.whitelist(), product.sort(), product.customFields(), product.createdBy(), product.createdAt(),
        product.updatedBy(), product.updatedAt(), product.closedAt(), product.lockVersion());
  }
}
