package net.zentao.quality.app;

import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.web.CommentRequest;
import net.zentao.platform.workflow.WorkflowEngine;
import net.zentao.product.api.ProductApi;
import net.zentao.quality.api.BugView;
import net.zentao.quality.domain.BugRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 关闭 Bug（quality 卡 §4.1）：resolved → closed；closedBy/closedAt 由 fieldSet 落。 */
@Component
public class CloseBugHandler {

  private final BugRepository repository;
  private final ProductApi productApi;
  private final WorkflowEngine engine;

  public CloseBugHandler(BugRepository repository, ProductApi productApi, WorkflowEngine engine) {
    this.repository = repository;
    this.productApi = productApi;
    this.engine = engine;
  }

  @Transactional
  public BugView handle(SessionPrincipal actor, long bugId, CommentRequest command) {
    String comment = command == null ? null : command.comment();
    return BugActionSupport.fire(actor, repository, productApi, engine, bugId, "close", comment);
  }
}
