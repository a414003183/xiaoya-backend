package net.zentao.quality.app;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import net.zentao.org.api.AccountApi;
import net.zentao.platform.meta.FieldDefValidator;
import net.zentao.platform.activity.ActivityRecorder;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.product.api.ProductApi;
import net.zentao.quality.api.BugView;
import net.zentao.quality.domain.Bug;
import net.zentao.quality.domain.BugRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 提 Bug（quality 卡 §3.1/§5）：severity/priority/type 缺省 3/3/codeerror；relatedBugIds 须同产品。 */
@Component
public class CreateBugHandler {

  private final BugRepository repository;
  private final ProductApi productApi;
  private final AccountApi accountApi;
  private final ActivityRecorder activityRecorder;
  private final FieldDefValidator fieldDefValidator;

  public CreateBugHandler(BugRepository repository, ProductApi productApi, AccountApi accountApi,
      ActivityRecorder activityRecorder,
      FieldDefValidator fieldDefValidator) {
    this.fieldDefValidator = fieldDefValidator;
    this.repository = repository;
    this.productApi = productApi;
    this.accountApi = accountApi;
    this.activityRecorder = activityRecorder;
  }

  public record BugCreateRequest(
      Long branchId, Long categoryId, Long projectId, Long executionId, Long planId, Long storyId, Long taskId,
      Long testCaseId, Long testRunId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title, String keywords, Integer severity,
      Integer priority,
      @Schema(allowableValues = {"automation", "codeerror", "config", "designdefect", "install", "others",
          "performance", "security", "standard"}) String type,
      String os, String browser, String steps, String openedBuilds, String assignee,
      LocalDate deadline, List<Long> relatedBugIds, List<String> notifyAccounts,
      Map<String, Object> customFields) {}

  @Transactional
  public BugView handle(SessionPrincipal actor, long productId, BugCreateRequest command) {
    productApi.requireVisible(actor, productId);
    if (command.title() == null || command.title().trim().isEmpty()) {
      throw ApiException.validation(Map.of("title", "required"));
    }
    BugFields.validate(command.title(), command.keywords(), command.severity(), command.priority(),
        command.type(), command.os(), command.browser(), command.openedBuilds(), null, command.assignee(),
        command.notifyAccounts(), accountApi);
    BugFields.validateRelatedBugs(productId, command.relatedBugIds(), repository);
    Instant now = Instant.now();
    fieldDefValidator.validate("bug", command.customFields() == null ? java.util.Map.of() : command.customFields(), true);
    Bug bug = repository.insert(new Bug(
        0,
        productId,
        command.branchId() == null ? 0 : command.branchId(),
        command.categoryId() == null ? 0 : command.categoryId(),
        command.projectId() == null ? 0 : command.projectId(),
        command.executionId() == null ? 0 : command.executionId(),
        command.planId() == null || command.planId() == 0 ? null : command.planId(),
        command.storyId() == null || command.storyId() == 0 ? null : command.storyId(),
        command.taskId() == null || command.taskId() == 0 ? null : command.taskId(),
        command.testCaseId() == null || command.testCaseId() == 0 ? null : command.testCaseId(),
        command.testRunId() == null || command.testRunId() == 0 ? null : command.testRunId(),
        command.title().trim(),
        command.keywords(),
        command.severity() == null ? 3 : command.severity(),
        command.priority() == null ? 3 : command.priority(),
        command.type() == null ? "codeerror" : command.type(),
        command.os(),
        command.browser(),
        command.steps(),
        command.openedBuilds(),
        "active",
        false,
        0,
        command.deadline(),
        command.assignee(),
        null,
        null,
        null,
        null,
        null,
        null,
        command.relatedBugIds(),
        command.notifyAccounts(),
        null,
        null,
        command.customFields(),
        actor.account(),
        now,
        null,
        null,
        0));
    activityRecorder.record(actor.account(), "bug", bug.id(), "created", null, null);
    return BugView.of(bug);
  }
}
