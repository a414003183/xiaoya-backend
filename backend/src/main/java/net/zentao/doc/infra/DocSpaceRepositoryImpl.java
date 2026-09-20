package net.zentao.doc.infra;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.row.Row;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.zentao.doc.domain.DocSpace;
import net.zentao.doc.domain.DocSpaceRepository;
import org.springframework.stereotype.Component;

/** 文档库仓储实现（infra：PO ↔ 领域对象；whitelist 为 JSON 文本列）。 */
@Component
public class DocSpaceRepositoryImpl implements DocSpaceRepository {

  private static final QueryColumn DELETED_AT = new QueryColumn("deleted_at");
  private static final Map<String, String> OWNER_COLUMNS = Map.of(
      "product", "product_id",
      "project", "project_id",
      "execution", "execution_id");

  private final DocSpaceMapper mapper;
  private final DocAclJson aclJson;

  public DocSpaceRepositoryImpl(DocSpaceMapper mapper, DocAclJson aclJson) {
    this.mapper = mapper;
    this.aclJson = aclJson;
  }

  @Override
  public Optional<DocSpace> findActiveById(long id) {
    return Optional.ofNullable(mapper.selectOneByCondition(new QueryColumn("id").eq(id).and(DELETED_AT.isNull())))
        .map(this::toDomain);
  }

  @Override
  public List<DocSpace> findAllActive() {
    return mapper.selectListByCondition(DELETED_AT.isNull()).stream().map(this::toDomain).toList();
  }

  @Override
  public List<DocSpace> findActiveByIds(List<Long> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    return mapper.selectListByCondition(new QueryColumn("id").in(ids).and(DELETED_AT.isNull())).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public Optional<DocSpace> findMineByCreator(String account) {
    return Optional.ofNullable(mapper.selectOneByCondition(new QueryColumn("type").eq("mine")
        .and(new QueryColumn("created_by").eq(account))
        .and(DELETED_AT.isNull()))).map(this::toDomain);
  }

  @Override
  public DocSpace insert(DocSpace space) {
    DocSpacePO po = toPo(space);
    po.setId(null);
    po.setLockVersion(0);
    mapper.insert(po);
    return toDomain(mapper.selectOneById(po.getId()));
  }

  @Override
  public Optional<DocSpace> update(DocSpace space) {
    // 全量覆盖（含 null 字段）：PATCH 的每个可清空字段都必须落库
    // 回读行：lockVersion 由库内自增，回内存聚合会给出过期版本（下一次 PATCH 必 40901）。
    if (mapper.update(toPo(space), false) <= 0) {
      return Optional.empty();
    }
    return findActiveById(space.id());
  }

  @Override
  public void clearDefaultFlag(String type, long ownerId, long excludeSpaceId) {
    String ownerColumn = OWNER_COLUMNS.get(type);
    if (ownerColumn == null) {
      return;
    }
    Db.updateByCondition("doc_space", Row.of("is_default", 0),
        new QueryColumn("type").eq(type)
            .and(new QueryColumn(ownerColumn).eq(ownerId))
            .and(new QueryColumn("id").ne(excludeSpaceId))
            .and(new QueryColumn("is_default").eq(1))
            .and(DELETED_AT.isNull()));
  }

  @Override
  public void softDelete(long id, String actor, java.time.Instant at) {
    Db.updateByCondition("doc_space", Row.of("deleted_at", at).set("updated_by", actor),
        new QueryColumn("id").eq(id).and(DELETED_AT.isNull()));
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<DocSpace> queryPage(Object whereWrapper, int offset, int limit) {
    return mapper.selectListByQuery(((QueryWrapper) whereWrapper).limit(offset, limit)).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public long countByQuery(Object whereWrapper) {
    return mapper.selectCountByQuery((QueryWrapper) whereWrapper);
  }

  @Override
  public Map<Long, Long> countLiveDocsBySpaces(List<Long> spaceIds) {
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

  private DocSpace toDomain(DocSpacePO po) {
    return new DocSpace(po.getId(), po.getName(), po.getType(),
        po.getProductId() == null ? 0 : po.getProductId(),
        po.getProjectId() == null ? 0 : po.getProjectId(),
        po.getExecutionId() == null ? 0 : po.getExecutionId(),
        po.getAcl(), aclJson.readAcl(po.getWhitelist()), po.getDescription(), po.getDocSort(),
        po.getIsDefault() != null && po.getIsDefault() == 1, po.getSort() == null ? 0 : po.getSort(),
        po.getCreatedBy(), po.getCreatedAt(), po.getUpdatedBy(), po.getUpdatedAt(),
        po.getLockVersion() == null ? 0 : po.getLockVersion());
  }

  private DocSpacePO toPo(DocSpace space) {
    DocSpacePO po = new DocSpacePO();
    po.setId(space.id() == 0 ? null : space.id());
    po.setName(space.name());
    po.setType(space.type());
    po.setProductId(space.productId());
    po.setProjectId(space.projectId());
    po.setExecutionId(space.executionId());
    po.setAcl(space.acl());
    po.setWhitelist(aclJson.writeAcl(space.whitelist()));
    po.setDescription(space.description());
    po.setDocSort(space.docSort());
    po.setIsDefault(space.isDefault() ? 1 : 0);
    po.setSort(space.sort());
    po.setCreatedBy(space.createdBy());
    po.setCreatedAt(space.createdAt());
    po.setUpdatedBy(space.updatedBy());
    po.setUpdatedAt(space.updatedAt());
    po.setLockVersion(space.lockVersion());
    return po;
  }
}
