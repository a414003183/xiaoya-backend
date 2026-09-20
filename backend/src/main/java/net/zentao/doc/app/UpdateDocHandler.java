package net.zentao.doc.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import net.zentao.doc.domain.Doc;
import net.zentao.doc.domain.DocAcl;
import net.zentao.doc.domain.DocCategory;
import net.zentao.doc.domain.DocCategoryRepository;
import net.zentao.doc.domain.DocRepository;
import net.zentao.org.api.AccountApi;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基本信息部分更新（doc 卡 §5 PATCH /docs/{docId}：title/keywords/categoryId/parentId/acl/editors/readers/
 * notifyAccounts/sort，不含 content）；lockVersion 不符 → 40901；parentId 成环 → 42201 并级联重建子树 path。
 */
@Component
public class UpdateDocHandler {

  public record DocUpdateRequest(
      String title, String keywords, Long categoryId, Long parentId,
      @Schema(allowableValues = {"open", "private"}) String acl, DocAcl editors, DocAcl readers,
      java.util.List<String> notifyAccounts, Integer sort,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer lockVersion) {}

  private final DocRepository repository;
  private final DocCategoryRepository categoryRepository;
  private final DocAccess access;
  private final AccountApi accountApi;

  public UpdateDocHandler(DocRepository repository, DocCategoryRepository categoryRepository, DocAccess access,
      AccountApi accountApi) {
    this.repository = repository;
    this.categoryRepository = categoryRepository;
    this.access = access;
    this.accountApi = accountApi;
  }

  @Transactional
  public Doc handle(SessionPrincipal actor, long docId, DocUpdateRequest command) {
    Doc doc = access.requireEditableDoc(actor, docId);
    if (command.lockVersion() == null || command.lockVersion() != doc.lockVersion()) {
      throw ApiException.lockConflict("数据已被他人修改，请刷新后重试。");
    }
    Map<String, String> errors = DocFields.errors();
    if (command.title() != null) {
      DocFields.requireTitle(errors, command.title());
    }
    DocFields.maxLength(errors, "keywords", command.keywords(), DocFields.KEYWORDS_MAX);
    DocFields.oneOf(errors, "acl", command.acl(), DocFields.DOC_ACLS);
    DocFields.validateAcl(errors, "editors", command.editors(), accountApi);
    DocFields.validateAcl(errors, "readers", command.readers(), accountApi);
    DocFields.validateAccounts(errors, "notifyAccounts", command.notifyAccounts(), accountApi);
    if (command.categoryId() != null && command.categoryId() != 0
        && !belongsToSpace(command.categoryId(), doc.docSpaceId())) {
      errors.put("categoryId", "invalid");
    }
    Doc parent = null;
    if (command.parentId() != null && command.parentId() != 0) {
      if (command.parentId() == docId || repository.existsInSubtree(docId, doc.path(), command.parentId())) {
        // 不可选自身/后代（doc 卡 §3.2 成环 → 42201）
        errors.put("parentId", "cycle");
      } else {
        parent = repository.findActiveById(command.parentId()).orElse(null);
        if (parent == null || parent.docSpaceId() != doc.docSpaceId()) {
          errors.put("parentId", "invalid");
        }
      }
    }
    DocFields.reject(errors);

    String oldPath = doc.path();
    doc.updateBasic(command.title(), command.keywords(), command.categoryId(), command.parentId(), command.acl(),
        command.editors(), command.readers(), command.notifyAccounts(), command.sort());
    doc.markUpdatedBy(actor.account());
    Doc saved = repository.update(doc)
        .orElseThrow(() -> ApiException.lockConflict("数据已被他人修改，请刷新后重试。"));
    if (command.parentId() != null) {
      String newPath = parent == null ? "," + docId + "," : parent.path() + docId + ",";
      saved.applyPath(newPath);
      repository.replacePathPrefix(oldPath, newPath);
    }
    return saved;
  }

  private boolean belongsToSpace(long categoryId, long spaceId) {
    return categoryRepository.findById(categoryId).map(DocCategory::docSpaceId).orElse(-1L) == spaceId;
  }
}
