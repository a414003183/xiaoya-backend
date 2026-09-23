package net.zentao.product.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import net.zentao.product.domain.Plan;

/** 计划视图（contract：PlanView；product 卡 §3.4 读侧字段）。 */
public record PlanView(
    long id,
    long productId,
    long branchId,
    long parentId,
    String title,
    @Schema(allowableValues = {"closed", "doing", "done", "wait"}) String status,
    String description,
    LocalDate beginDate,
    LocalDate endDate,
    Instant finishedAt,
    Instant closedAt,
    @Schema(allowableValues = {"cancel", "done"}) String closedReason,
    Map<String, Object> customFields,
    String createdBy,
    Instant createdAt,
    String updatedBy,
    Instant updatedAt,
    int lockVersion) {

  public static PlanView of(Plan plan) {
    return new PlanView(plan.id(), plan.productId(), plan.branchId(), plan.parentId(), plan.title(), plan.status(),
        plan.description(), plan.beginDate(), plan.endDate(), plan.finishedAt(), plan.closedAt(),
        plan.closedReason(), plan.customFields(), plan.createdBy(), plan.createdAt(), plan.updatedBy(),
        plan.updatedAt(), plan.lockVersion());
  }
}
