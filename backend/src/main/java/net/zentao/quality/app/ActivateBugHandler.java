package net.zentao.quality.app;

import java.util.Map;
import net.zentao.org.api.AccountApi;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.workflow.WorkflowEngine;
import net.zentao.product.api.ProductApi;
import net.zentao.quality.api.BugView;
import net.zentao.quality.domain.BugRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 重开 Bug（quality 卡 §4.1）：openedBuilds 必填（42201）；assignee 省略回派原解决人；
 * activatedCount+1 与 resolution 三字段清空由 Bug 聚合承担。
 */
@Component
public class ActivateBugHandler {

  private final BugRepository repository;
  private final ProductApi productApi;
  private final AccountApi accountApi;
  private final WorkflowEngine engine;

  public ActivateBugHandler(BugRepository repository, ProductApi productApi, AccountApi accountApi,
      WorkflowEngine engine) {
    this.repository = repository;
    this.productApi = productApi;
    this.accountApi = accountApi;
    this.engine = engine;
  }

  public record BugActivateRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String openedBuilds, String assignee,
      String comment) {}

  @Transactional
  public BugView handle(SessionPrincipal actor, long bugId, BugActivateRequest command) {
    if (command.openedBuilds() == null || command.openedBuilds().isBlank()) {
      throw ApiException.validation(Map.of("openedBuilds", "required"));
    }
    if (command.openedBuilds().length() > 255) {
      throw ApiException.validation(Map.of("openedBuilds", "maxLength"));
    }
    if (command.assignee() != null) {
      BugFields.validateAssignee(command.assignee(), accountApi);
    }
    return BugActionSupport.fire(actor, repository, productApi, engine, bugId, "activate", command.comment(),
        bug -> {
          bug.activate(command.assignee());
          bug.markOpenedBuilds(command.openedBuilds());
        });
  }
}
