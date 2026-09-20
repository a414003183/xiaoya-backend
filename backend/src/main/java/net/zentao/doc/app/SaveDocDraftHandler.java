package net.zentao.doc.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import net.zentao.doc.domain.Doc;
import net.zentao.doc.domain.DocRepository;
import net.zentao.doc.domain.DocVersion;
import net.zentao.doc.domain.DocVersionRepository;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 存草稿（doc 卡 §4）：覆盖写 v0 工作副本，不升 version、不改已发布内容、不写动态流；
 * 仅可编辑者（readers 写 → 40302）；html 型存量编辑保存后落为 markdown。
 */
@Component
public class SaveDocDraftHandler {

  public record DocSaveDraftRequest(String title,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String content, List<Long> files) {}

  private final DocRepository repository;
  private final DocVersionRepository versionRepository;
  private final DocAccess access;

  public SaveDocDraftHandler(DocRepository repository, DocVersionRepository versionRepository, DocAccess access) {
    this.repository = repository;
    this.versionRepository = versionRepository;
    this.access = access;
  }

  @Transactional
  public Doc handle(SessionPrincipal actor, long docId, DocSaveDraftRequest command) {
    Doc doc = access.requireEditableDoc(actor, docId);
    Map<String, String> errors = DocFields.errors();
    if (command.content() == null) {
      errors.put("content", "required");
    }
    DocFields.maxLength(errors, "title", command.title(), DocFields.TITLE_MAX);
    DocFields.reject(errors);

    Instant now = Instant.now();
    DocVersion working = versionRepository.findByDocAndVersion(docId, DocVersion.DRAFT_VERSION).orElse(null);
    String title = command.title() != null ? command.title().trim()
        : working != null ? working.title() : doc.title();
    List<Long> files = command.files() != null ? command.files()
        : working != null ? working.files() : List.of();
    DocVersion draft = new DocVersion(
        working == null ? 0 : working.id(), docId, DocVersion.DRAFT_VERSION, title, command.content(),
        DocVersion.digestOf(command.content()), files,
        working == null ? actor.account() : working.createdBy(),
        working == null ? now : working.createdAt(), actor.account(), now);
    if (working == null) {
      versionRepository.insert(draft);
    } else {
      versionRepository.updateDraft(draft);
    }
    // 主表仅更新 updatedBy/At 与 html→markdown 转换；title/status/version 不动（发布才同步标题）
    doc.convertToMarkdown();
    doc.markUpdatedBy(actor.account());
    return repository.update(doc).orElseThrow(() -> ApiException.lockConflict("数据已被他人修改，请刷新后重试。"));
  }
}
