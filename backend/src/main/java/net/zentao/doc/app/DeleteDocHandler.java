package net.zentao.doc.app;

import java.time.Instant;
import net.zentao.doc.domain.Doc;
import net.zentao.doc.domain.DocRepository;
import net.zentao.doc.domain.DocVersion;
import net.zentao.doc.domain.DocVersionRepository;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.workflow.WorkflowEngine;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 软删文档（doc 卡 §4/§5）：子文档按 path 前缀一并软删；动态流 deleted（快照随主表软删隐藏）。 */
@Component
public class DeleteDocHandler {

  private final DocRepository repository;
  private final DocVersionRepository versionRepository;
  private final DocAccess access;
  private final WorkflowEngine engine;

  public DeleteDocHandler(DocRepository repository, DocVersionRepository versionRepository, DocAccess access,
      WorkflowEngine engine) {
    this.repository = repository;
    this.versionRepository = versionRepository;
    this.access = access;
    this.engine = engine;
  }

  @Transactional
  public void handle(SessionPrincipal actor, long docId, String comment) {
    Doc doc = access.requireEditableDoc(actor, docId);
    DocVersion working = versionRepository.findByDocAndVersion(docId, DocVersion.DRAFT_VERSION).orElse(null);
    engine.fire(new DocTarget(doc, working, false, actor.account()), "delete", comment);
    Instant now = Instant.now();
    repository.softDelete(doc.id(), actor.account(), now);
    repository.softDeleteSubtree(doc.path(), actor.account(), now);
  }
}
