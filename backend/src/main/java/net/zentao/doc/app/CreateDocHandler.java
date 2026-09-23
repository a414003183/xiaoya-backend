package net.zentao.doc.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.zentao.doc.domain.Doc;
import net.zentao.doc.domain.DocAcl;
import net.zentao.doc.domain.DocCategory;
import net.zentao.doc.domain.DocCategoryRepository;
import net.zentao.doc.domain.DocRepository;
import net.zentao.doc.domain.DocSpace;
import net.zentao.doc.domain.DocVersion;
import net.zentao.doc.domain.DocVersionRepository;
import net.zentao.org.api.AccountApi;
import net.zentao.platform.activity.ActivityRecorder;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.notification.NotificationRecorder;
import net.zentao.platform.session.SessionPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 创建文档（doc 卡 §4/§5）：status=draft 只写 v0 工作副本；status=published 直接发布 v1（content 空 → 42201）。
 * 新建仅 markdown（html 仅迁移存量，doc 卡 §3.2）；acl=open 时 editors/readers 强制清空。
 */
@Component
public class CreateDocHandler {

  public record DocCreateRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
      String keywords, Long categoryId, Long parentId,
      @Schema(allowableValues = {"html", "markdown"}) String type,
      @Schema(allowableValues = {"draft", "published"}) String status,
      @Schema(allowableValues = {"open", "private"}) String acl,
      DocAcl editors, DocAcl readers, List<String> notifyAccounts, String content, List<Long> files,
      Integer sort) {}

  private final DocRepository repository;
  private final DocVersionRepository versionRepository;
  private final DocCategoryRepository categoryRepository;
  private final DocAccess access;
  private final AccountApi accountApi;
  private final ActivityRecorder activityRecorder;
  private final NotificationRecorder notificationRecorder;

  public CreateDocHandler(DocRepository repository, DocVersionRepository versionRepository,
      DocCategoryRepository categoryRepository, DocAccess access, AccountApi accountApi,
      ActivityRecorder activityRecorder, NotificationRecorder notificationRecorder) {
    this.repository = repository;
    this.versionRepository = versionRepository;
    this.categoryRepository = categoryRepository;
    this.access = access;
    this.accountApi = accountApi;
    this.activityRecorder = activityRecorder;
    this.notificationRecorder = notificationRecorder;
  }

  @Transactional
  public Doc handle(SessionPrincipal actor, long docSpaceId, DocCreateRequest command) {
    DocSpace space = access.requireSpace(actor, docSpaceId);
    String status = command.status() == null ? "draft" : command.status();
    Map<String, String> errors = DocFields.errors();
    DocFields.requireTitle(errors, command.title());
    DocFields.maxLength(errors, "keywords", command.keywords(), DocFields.KEYWORDS_MAX);
    // 新建仅 markdown：html 仅迁移存量（doc 卡 §3.2）
    DocFields.oneOf(errors, "type", command.type(), Set.of("markdown"));
    DocFields.oneOf(errors, "status", command.status(), DocFields.DOC_STATUSES);
    DocFields.oneOf(errors, "acl", command.acl(), DocFields.DOC_ACLS);
    DocFields.validateAcl(errors, "editors", command.editors(), accountApi);
    DocFields.validateAcl(errors, "readers", command.readers(), accountApi);
    DocFields.validateAccounts(errors, "notifyAccounts", command.notifyAccounts(), accountApi);
    if ("published".equals(status) && (command.content() == null || command.content().isBlank())) {
      errors.put("content", "required");
    }
    DocFields.reject(errors);

    Long parentId = command.parentId() == null ? 0 : command.parentId();
    Doc parent = parentId == 0 ? null : chapter(parentId, space, "parentId", errors);
    if (command.categoryId() != null && command.categoryId() != 0
        && !belongsToSpace(command.categoryId(), space.id())) {
      errors.put("categoryId", "invalid");
    }
    DocFields.reject(errors);

    Instant now = Instant.now();
    String title = command.title().trim();
    List<Long> files = command.files() == null ? List.of() : command.files();
    String acl = command.acl() == null ? "open" : command.acl();
    // acl=open 时 editors/readers 强制清空（doc 卡 §3.2）
    DocAcl editors = "open".equals(acl) ? DocAcl.EMPTY : command.editors();
    DocAcl readers = "open".equals(acl) ? DocAcl.EMPTY : command.readers();
    Doc doc = repository.insert(new Doc(
        0, space.id(), space.productId(), space.projectId(), space.executionId(),
        command.categoryId() == null ? 0 : command.categoryId(),
        parent == null ? 0 : parent.id(),
        "", title, command.keywords(), "markdown", "draft", acl,
        editors, readers, command.notifyAccounts(), 0, 0,
        command.sort() == null ? 0 : command.sort(), actor.account(), now, null, null, 0));
    String path = parent == null ? "," + doc.id() + "," : parent.path() + doc.id() + ",";
    repository.updatePath(doc.id(), path);
    doc.applyPath(path);
    versionRepository.insert(DocVersion.draft(doc.id(), title, command.content(), files, actor.account(), now));

    Long activityId = activityRecorder.record(actor.account(), "doc", doc.id(), "created", null, null);
    if ("published".equals(status)) {
      versionRepository.insert(DocVersion.snapshot(doc.id(), 1, title, command.content(), files, actor.account(), now));
      doc.publishedAs(1, title);
      doc.markUpdatedBy(actor.account());
      Doc published = repository.update(doc)
          .orElseThrow(() -> ApiException.lockConflict());
      // 直接发布的通知与 publish 动作同型（platform：<objectType>-<action>）
      notificationRecorder.record(published.notifyAccounts(), "doc-publish", "doc", published.id(), activityId,
          title, null, actor.account());
      return published;
    }
    return doc;
  }

  /** 章节父节点必须在本库且未删（doc 卡 §3.2 parentId）。 */
  private Doc chapter(long parentId, DocSpace space, String field, Map<String, String> errors) {
    Doc parent = repository.findActiveById(parentId).orElse(null);
    if (parent == null || parent.docSpaceId() != space.id()) {
      errors.put(field, "invalid");
      return null;
    }
    return parent;
  }

  private boolean belongsToSpace(long categoryId, long spaceId) {
    return categoryRepository.findById(categoryId).map(DocCategory::docSpaceId).orElse(-1L) == spaceId;
  }
}
