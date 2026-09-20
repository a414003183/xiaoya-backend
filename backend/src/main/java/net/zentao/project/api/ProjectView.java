package net.zentao.project.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import net.zentao.project.domain.Project;

/** 项目视图（contract：ProjectView；project 卡 §3.1 读侧字段；program/project/execution 三型同构，`type` 区分）。 */
public record ProjectView(
    long id,
    @Schema(allowableValues = {"kanban", "program", "project", "sprint", "stage"}) String type,
    long parentId,
    String path,
    int grade,
    String name,
    String code,
    @Schema(allowableValues = {"kanban", "scrum", "waterfall"}) String model,
    @Schema(allowableValues = {"closed", "delay", "doing", "suspended", "wait"}) String status,
    int priority,
    LocalDate beginDate,
    LocalDate endDate,
    LocalDate firstEndDate,
    LocalDate realBeganDate,
    LocalDate realEndDate,
    int days,
    BigDecimal budget,
    @Schema(allowableValues = {"CNY", "USD"}) String budgetUnit,
    String description,
    String pm,
    String po,
    String qd,
    String rd,
    int progress,
    BigDecimal estimateHours,
    BigDecimal consumedHours,
    BigDecimal leftHours,
    boolean isMilestone,
    @Schema(allowableValues = {"open", "private", "program"}) String acl,
    List<String> whitelist,
    int sort,
    Map<String, Object> customFields,
    String createdBy,
    Instant createdAt,
    String updatedBy,
    Instant updatedAt,
    String closedBy,
    Instant closedAt,
    int lockVersion) {

  public static ProjectView of(Project project) {
    return new ProjectView(project.id(), project.type(), project.parentId(), project.path(), project.grade(),
        project.name(), project.code(), project.model(), project.status(), project.priority(), project.beginDate(),
        project.endDate(), project.firstEndDate(), project.realBeganDate(), project.realEndDate(), project.days(),
        project.budget(), project.budgetUnit(), project.description(), project.pm(), project.po(), project.qd(),
        project.rd(), project.progress(), project.estimateHours(), project.consumedHours(), project.leftHours(),
        project.isMilestone(), project.acl(), project.whitelist(), project.sort(), project.customFields(),
        project.createdBy(), project.createdAt(), project.updatedBy(), project.updatedAt(), project.closedBy(),
        project.closedAt(), project.lockVersion());
  }
}
