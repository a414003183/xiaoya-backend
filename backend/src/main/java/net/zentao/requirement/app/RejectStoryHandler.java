package net.zentao.requirement.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.workflow.WorkflowEngine;
import net.zentao.product.api.ProductApi;
import net.zentao.requirement.api.StoryView;
import net.zentao.requirement.domain.StoryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 评审拒绝（requirement 卡 §4：reviewing → draft，comment 必填 → 缺省 42201，YAML 守卫兜底 42203）。 */
@Component
public class RejectStoryHandler {

  private final StoryRepository repository;
  private final ProductApi productApi;
  private final WorkflowEngine engine;

  public RejectStoryHandler(StoryRepository repository, ProductApi productApi, WorkflowEngine engine) {
    this.repository = repository;
    this.productApi = productApi;
    this.engine = engine;
  }

  public record StoryRejectRequest(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String comment) {}

  @Transactional
  public StoryView handle(SessionPrincipal actor, long storyId, StoryRejectRequest command) {
    if (command == null || command.comment() == null || command.comment().isBlank()) {
      throw ApiException.validation(Map.of("comment", "required"));
    }
    return StoryActionSupport.fire(actor, repository, productApi, engine, storyId, "reject", command.comment());
  }
}
