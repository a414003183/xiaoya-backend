package net.zentao.doc.app;

import java.time.Instant;
import java.util.List;
import net.zentao.doc.domain.DocSpace;
import net.zentao.doc.domain.DocSpaceRepository;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 删除文档库（doc 卡 §5）：库内无未删文档，否则 42203；软删。 */
@Component
public class DeleteDocSpaceHandler {

  private final DocSpaceRepository repository;
  private final DocAccess access;

  public DeleteDocSpaceHandler(DocSpaceRepository repository, DocAccess access) {
    this.repository = repository;
    this.access = access;
  }

  @Transactional
  public void handle(SessionPrincipal actor, long spaceId) {
    DocSpace space = access.requireSpace(actor, spaceId);
    if (repository.countLiveDocsBySpaces(List.of(spaceId)).getOrDefault(spaceId, 0L) > 0) {
      throw ApiException.guardNotSatisfied("文档库非空，请先清空库内文档。");
    }
    repository.softDelete(space.id(), actor.account(), Instant.now());
  }
}
