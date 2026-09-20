package net.zentao.doc.app;

import java.time.Instant;
import net.zentao.doc.domain.Doc;
import net.zentao.doc.domain.DocRepository;
import net.zentao.doc.domain.DocVersion;
import net.zentao.doc.domain.DocVersionRepository;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.workflow.WorkflowEngine;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 发布文档（doc 卡 §4）：v0 工作副本落新快照——首发 v1，再发 v(N+1)；
 * 再发时 v0 相对当前版本无实际改动 → 42203（workflow/doc.yml 守卫），首发 title/content 空 → 42203；
 * 并发由 UNIQUE(doc_id, version) 兜底 → 40901；动态流 published/edited 与通知由 YAML 副作用落。
 */
@Component
public class PublishDocHandler {

  private final DocRepository repository;
  private final DocVersionRepository versionRepository;
  private final DocAccess access;
  private final WorkflowEngine engine;

  public PublishDocHandler(DocRepository repository, DocVersionRepository versionRepository, DocAccess access,
      WorkflowEngine engine) {
    this.repository = repository;
    this.versionRepository = versionRepository;
    this.access = access;
    this.engine = engine;
  }

  @Transactional
  public Doc handle(SessionPrincipal actor, long docId, String comment) {
    Doc doc = access.requireEditableDoc(actor, docId);
    DocVersion working = versionRepository.findByDocAndVersion(docId, DocVersion.DRAFT_VERSION)
        .orElseThrow(() -> ApiException.notFound("文档正文"));
    DocVersion current = doc.version() > 0
        ? versionRepository.findByDocAndVersion(docId, doc.version()).orElse(null)
        : null;
    boolean hasDraftChanges = current == null || !working.sameContentAs(current);
    engine.fire(new DocTarget(doc, working, hasDraftChanges, actor.account()), "publish", comment);

    // 新快照与 v0 逐字节一致（doc 卡 §4）；同版本号并发插入由唯一键兜底
    int newVersion = doc.version() + 1;
    try {
      versionRepository.insert(DocVersion.snapshot(docId, newVersion, working.title(), working.content(),
          working.files(), actor.account(), Instant.now()));
    } catch (DuplicateKeyException e) {
      throw ApiException.lockConflict("数据已被他人修改，请刷新后重试。");
    }
    doc.publishedAs(newVersion, working.title());
    doc.markUpdatedBy(actor.account());
    return repository.update(doc).orElseThrow(() -> ApiException.lockConflict("数据已被他人修改，请刷新后重试。"));
  }
}
