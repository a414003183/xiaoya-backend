package net.zentao.project.app;

import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.project.domain.Stakeholder;
import net.zentao.project.domain.StakeholderRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 移除干系人（project 卡 §3.8：软删；对象不匹配的 stakeholderId → 40401，防跨对象删改）。 */
@Component
public class RemoveStakeholderHandler {

  private final StakeholderRepository repository;
  private final ProjectQueryService projectQueryService;

  public RemoveStakeholderHandler(StakeholderRepository repository, ProjectQueryService projectQueryService) {
    this.repository = repository;
    this.projectQueryService = projectQueryService;
  }

  @Transactional
  public void handle(SessionPrincipal actor, String objectType, long objectId, long stakeholderId) {
    projectQueryService.requireVisible(actor, objectType, objectId);
    Stakeholder stakeholder = repository.findActiveById(stakeholderId)
        .filter(row -> objectType.equals(row.objectType()) && row.objectId() == objectId)
        .orElseThrow(() -> ApiException.notFound("entity.stakeholder"));
    repository.softDelete(stakeholder.id(), actor.account());
  }
}
