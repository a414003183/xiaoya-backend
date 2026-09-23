package net.zentao.doc.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.zentao.doc.domain.DocCategory;
import net.zentao.doc.domain.DocCategoryRepository;
import net.zentao.doc.domain.DocRepository;
import net.zentao.doc.domain.DocSpace;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
import net.zentao.platform.session.SessionPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 库内目录命令（doc 卡 §2/§5）：新建/改名移动排序/真实删除。
 * 删除守卫：有子节点或有文档引用 → 42203；移动成环（选自身/后代）→ 42201。
 */
@Component
public class DocCategoryHandlers {

  public record DocCategoryCreateRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
      Long parentId, Integer sort) {}

  public record DocCategoryUpdateRequest(String name, Long parentId, Integer sort) {}

  private final DocCategoryRepository repository;
  private final DocRepository docRepository;
  private final DocAccess access;

  public DocCategoryHandlers(DocCategoryRepository repository, DocRepository docRepository, DocAccess access) {
    this.repository = repository;
    this.docRepository = docRepository;
    this.access = access;
  }

  @Transactional
  public DocCategory create(SessionPrincipal actor, long docSpaceId, DocCategoryCreateRequest command) {
    DocSpace space = access.requireSpace(actor, docSpaceId);
    Map<String, String> errors = DocFields.errors();
    DocFields.requireName(errors, "name", command.name());
    long parentId = command.parentId() == null ? 0 : command.parentId();
    if (parentId != 0 && !belongsToSpace(parentId, space.id())) {
      errors.put("parentId", "invalid");
    }
    DocFields.reject(errors);
    Instant now = Instant.now();
    return repository.insert(new DocCategory(0, space.id(), parentId, command.name().trim(),
        command.sort() == null ? 0 : command.sort(), actor.account(), now, null, null, 0));
  }

  @Transactional
  public DocCategory update(SessionPrincipal actor, long docSpaceId, long categoryId,
      DocCategoryUpdateRequest command) {
    DocSpace space = access.requireSpace(actor, docSpaceId);
    DocCategory category = requireInSpace(categoryId, space.id());
    Map<String, String> errors = DocFields.errors();
    // PATCH 部分更新（03 §1 null=不修改；契约 DocCategoryUpdateRequest 无必填项）：name 传值才校验
    if (command.name() != null) {
      DocFields.requireName(errors, "name", command.name());
    }
    if (command.parentId() != null) {
      long parentId = command.parentId();
      if (parentId == categoryId || subtreeIds(space.id(), categoryId).contains(parentId)) {
        errors.put("parentId", "cycle");
      } else if (parentId != 0 && !belongsToSpace(parentId, space.id())) {
        errors.put("parentId", "invalid");
      }
    }
    DocFields.reject(errors);
    category.update(command.name(), command.parentId(), command.sort());
    category.markUpdatedBy(actor.account());
    return repository.update(category).orElseThrow(() -> ApiException.notFound("entity.docCategory"));
  }

  /** 真实删除：有子节点或有文档引用 → 42203（doc 卡 §2）。 */
  @Transactional
  public void delete(SessionPrincipal actor, long docSpaceId, long categoryId) {
    DocSpace space = access.requireSpace(actor, docSpaceId);
    DocCategory category = requireInSpace(categoryId, space.id());
    if (repository.countChildren(category.id()) > 0 || docRepository.countLiveByCategory(category.id()) > 0) {
      throw ApiException.keyed(ErrorCode.GUARD_NOT_SATISFIED, "docCategory.guard.notEmpty");
    }
    repository.delete(category.id());
  }

  private DocCategory requireInSpace(long categoryId, long spaceId) {
    DocCategory category = repository.findById(categoryId).orElseThrow(() -> ApiException.notFound("entity.docCategory"));
    if (category.docSpaceId() != spaceId) {
      throw ApiException.notFound("entity.docCategory");
    }
    return category;
  }

  private boolean belongsToSpace(long categoryId, long spaceId) {
    return repository.findById(categoryId).map(category -> category.docSpaceId() == spaceId).orElse(false);
  }

  /** 目录子树 id 集（含自身）：成环判定用；目录树小，内存遍历（ponytail: 升级路径 = 闭包表）。 */
  private Set<Long> subtreeIds(long spaceId, long rootId) {
    Map<Long, List<Long>> children = new LinkedHashMap<>();
    for (DocCategory category : repository.findBySpace(spaceId)) {
      children.computeIfAbsent(category.parentId(), key -> new ArrayList<>()).add(category.id());
    }
    Set<Long> ids = new HashSet<>();
    collect(rootId, children, ids);
    return ids;
  }

  private static void collect(long id, Map<Long, List<Long>> children, Set<Long> ids) {
    ids.add(id);
    for (Long child : children.getOrDefault(id, List.of())) {
      collect(child, children, ids);
    }
  }
}
