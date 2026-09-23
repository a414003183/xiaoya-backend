package net.zentao.doc.infra;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.row.Row;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.zentao.doc.domain.Doc;
import net.zentao.doc.domain.DocRepository;
import net.zentao.platform.filters.LikePatterns;
import net.zentao.platform.persistence.SoftDeletes;
import org.springframework.stereotype.Component;

/** 文档仓储实现（infra：PO ↔ 领域对象；editors/readers/notify_accounts 为 JSON 文本列）。 */
@Component
public class DocRepositoryImpl implements DocRepository {

  private static final QueryColumn DELETED_AT = new QueryColumn("deleted_at");
  private static final QueryColumn PATH = new QueryColumn("path");

  private final DocMapper mapper;
  private final DocAclJson aclJson;

  public DocRepositoryImpl(DocMapper mapper, DocAclJson aclJson) {
    this.mapper = mapper;
    this.aclJson = aclJson;
  }

  @Override
  public Optional<Doc> findActiveById(long id) {
    return Optional.ofNullable(mapper.selectOneByCondition(new QueryColumn("id").eq(id).and(DELETED_AT.isNull())))
        .map(this::toDomain);
  }

  @Override
  public List<Doc> findActiveByIds(List<Long> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    return mapper.selectListByCondition(new QueryColumn("id").in(ids).and(DELETED_AT.isNull())).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public Doc insert(Doc doc) {
    DocPO po = toPo(doc);
    po.setId(null);
    po.setLockVersion(0);
    mapper.insert(po);
    return toDomain(mapper.selectOneById(po.getId()));
  }

  @Override
  public Optional<Doc> update(Doc doc) {
    // 全量覆盖（含 null 字段）：聚合持有完整状态，清空 keywords/acl 白名单必须落库
    // 回读行：lockVersion 由库内自增，回内存聚合会给出过期版本（下一次 PATCH 必 40901）。
    if (mapper.update(toPo(doc), false) <= 0) {
      return Optional.empty();
    }
    return findActiveById(doc.id());
  }

  @Override
  public void updatePath(long id, String path) {
    // 免乐观锁路径：path 是派生列（同 project.updatePath 口径）
    Db.updateByCondition("doc", Row.of("path", path), new QueryColumn("id").eq(id));
  }

  @Override
  public void updateViews(long id, int views) {
    // 免乐观锁路径：views 计数不改动其他字段的 lockVersion（doc 卡 §4 阅读计数）
    Db.updateByCondition("doc", Row.of("views", views), new QueryColumn("id").eq(id));
  }

  @Override
  public void softDelete(long id, String actor, Instant at) {
    SoftDeletes.apply("doc", Row.of("deleted_at", at).set("updated_by", actor),
        new QueryColumn("id").eq(id).and(DELETED_AT.isNull()));
  }

  @Override
  public int softDeleteSubtree(String pathPrefix, String actor, Instant at) {
    return SoftDeletes.apply("doc", Row.of("deleted_at", at).set("updated_by", actor),
        new QueryColumn("path").likeLeft(pathPrefix).and(DELETED_AT.isNull()));
  }

  @Override
  public void replacePathPrefix(String oldPrefix, String newPrefix) {
    // ponytail: 子树逐行回写（章节树规模有界）；升级路径 = 单条 UPDATE ... SET path = CONCAT(?, SUBSTRING(path, ?))
    List<DocPO> subtree = mapper.selectListByCondition(PATH.likeLeft(oldPrefix).and(DELETED_AT.isNull()));
    for (DocPO row : subtree) {
      updatePath(row.getId(), newPrefix + row.getPath().substring(oldPrefix.length()));
    }
  }

  @Override
  public boolean existsInSubtree(long rootId, String rootPath, long candidateId) {
    QueryCondition inSubtree = new QueryColumn("id").eq(rootId).or(PATH.likeLeft(rootPath));
    return mapper.selectCountByQuery(QueryWrapper.create()
        .where(new QueryColumn("id").eq(candidateId).and(inSubtree).and(DELETED_AT.isNull()))) > 0;
  }

  @Override
  public long countLiveByCategory(long categoryId) {
    return mapper.selectCountByQuery(QueryWrapper.create()
        .where(new QueryColumn("category_id").eq(categoryId).and(DELETED_AT.isNull())));
  }

  @Override
  public Map<Long, Long> countLiveBySpaces(List<Long> spaceIds) {
    if (spaceIds.isEmpty()) {
      return Map.of();
    }
    String placeholders = String.join(",", java.util.Collections.nCopies(spaceIds.size(), "?"));
    List<Row> rows = Db.selectListBySql(
        "SELECT doc_space_id AS space_id, COUNT(*) AS doc_count FROM doc"
            + " WHERE deleted_at IS NULL AND doc_space_id IN (" + placeholders + ") GROUP BY doc_space_id",
        spaceIds.toArray());
    Map<Long, Long> counts = new LinkedHashMap<>();
    for (Row row : rows) {
      counts.put(row.getLong("space_id"), row.getLong("doc_count"));
    }
    return counts;
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<Doc> queryPage(Object whereWrapper, int offset, int limit) {
    return mapper.selectListByQuery(((QueryWrapper) whereWrapper).limit(offset, limit)).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public long countByQuery(Object whereWrapper) {
    return mapper.selectCountByQuery((QueryWrapper) whereWrapper);
  }

  private Doc toDomain(DocPO po) {
    return new Doc(po.getId(), po.getDocSpaceId(),
        po.getProductId() == null ? 0 : po.getProductId(),
        po.getProjectId() == null ? 0 : po.getProjectId(),
        po.getExecutionId() == null ? 0 : po.getExecutionId(),
        po.getCategoryId() == null ? 0 : po.getCategoryId(),
        po.getParentId() == null ? 0 : po.getParentId(),
        po.getPath(), po.getTitle(), po.getKeywords(), po.getType(), po.getStatus(), po.getAcl(),
        aclJson.readAcl(po.getEditors()), aclJson.readAcl(po.getReaders()),
        aclJson.readStrings(po.getNotifyAccounts()),
        po.getViews() == null ? 0 : po.getViews(), po.getVersion() == null ? 0 : po.getVersion(),
        po.getSort() == null ? 0 : po.getSort(), po.getCreatedBy(), po.getCreatedAt(), po.getUpdatedBy(),
        po.getUpdatedAt(), po.getLockVersion() == null ? 0 : po.getLockVersion());
  }

  private DocPO toPo(Doc doc) {
    DocPO po = new DocPO();
    po.setId(doc.id() == 0 ? null : doc.id());
    po.setDocSpaceId(doc.docSpaceId());
    po.setProductId(doc.productId());
    po.setProjectId(doc.projectId());
    po.setExecutionId(doc.executionId());
    po.setCategoryId(doc.categoryId());
    po.setParentId(doc.parentId());
    po.setPath(doc.path());
    po.setTitle(doc.title());
    po.setKeywords(doc.keywords());
    po.setType(doc.type());
    po.setStatus(doc.status());
    po.setAcl(doc.acl());
    po.setEditors(aclJson.writeAcl(doc.editors()));
    po.setReaders(aclJson.writeAcl(doc.readers()));
    po.setNotifyAccounts(aclJson.writeStrings(doc.notifyAccounts()));
    po.setViews(doc.views());
    po.setVersion(doc.version());
    po.setSort(doc.sort());
    po.setCreatedBy(doc.createdBy());
    po.setCreatedAt(doc.createdAt());
    po.setUpdatedBy(doc.updatedBy());
    po.setUpdatedAt(doc.updatedAt());
    po.setLockVersion(doc.lockVersion());
    return po;
  }
}
