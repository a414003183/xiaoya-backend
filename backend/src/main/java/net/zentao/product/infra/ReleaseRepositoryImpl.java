package net.zentao.product.infra;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.row.Row;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import net.zentao.product.domain.Release;
import net.zentao.product.domain.ReleaseRepository;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** 发布仓储实现（infra；三个 JSON 文本列）。 */
@Component
public class ReleaseRepositoryImpl implements ReleaseRepository {

  private static final QueryColumn DELETED_AT = new QueryColumn("deleted_at");
  private static final TypeReference<List<Long>> LONG_LIST = new TypeReference<>() {};
  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

  private final ReleaseMapper mapper;
  private final JsonMapper jsonMapper;

  public ReleaseRepositoryImpl(ReleaseMapper mapper, JsonMapper jsonMapper) {
    this.mapper = mapper;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Optional<Release> findActiveById(long id) {
    return Optional.ofNullable(mapper.selectOneByCondition(new QueryColumn("id").eq(id).and(DELETED_AT.isNull())))
        .map(this::toDomain);
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<Release> queryPage(Object whereWrapper, int offset, int limit) {
    return mapper.selectListByQuery(((QueryWrapper) whereWrapper).limit(offset, limit)).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public long countByQuery(Object whereWrapper) {
    return mapper.selectCountByQuery((QueryWrapper) whereWrapper);
  }

  @Override
  public Release insert(Release release) {
    ReleasePO po = toPo(release);
    po.setId(null);
    po.setLockVersion(0);
    mapper.insert(po);
    return toDomain(mapper.selectOneById(po.getId()));
  }

  @Override
  public Optional<Release> update(Release release) {
    return mapper.update(toPo(release), false) > 0 ? Optional.of(release) : Optional.empty();
  }

  @Override
  public boolean existsByBuildId(long buildId) {
    return mapper.selectCountByCondition(new QueryColumn("build_id").eq(buildId).and(DELETED_AT.isNull())) > 0;
  }

  @Override
  public boolean existsActiveByProduct(long productId) {
    return mapper.selectCountByCondition(
        new QueryColumn("product_id").eq(productId).and(DELETED_AT.isNull())) > 0;
  }

  @Override
  public void softDelete(long id) {
    Db.updateByCondition("product_release", Row.of("deleted_at", Instant.now()), new QueryColumn("id").eq(id));
  }

  private Release toDomain(ReleasePO po) {
    return new Release(po.getId(), po.getProductId(), po.getBranchId() == null ? 0 : po.getBranchId(),
        po.getBuildId(), po.getProjectId() == null ? 0 : po.getProjectId(), po.getName(), po.getStatus(),
        po.getReleaseDate(), po.getPublishedAt(), po.getIsMilestone() != null && po.getIsMilestone() == 1,
        readLongList(po.getStoryIds()), readLongList(po.getBugIds()), readStringList(po.getNotifyAccounts()),
        po.getDescription(), po.getCreatedBy(), po.getCreatedAt(), po.getUpdatedBy(), po.getUpdatedAt(),
        po.getLockVersion() == null ? 0 : po.getLockVersion());
  }

  private ReleasePO toPo(Release release) {
    ReleasePO po = new ReleasePO();
    po.setId(release.id() == 0 ? null : release.id());
    po.setProductId(release.productId());
    po.setBranchId(release.branchId());
    po.setBuildId(release.buildId());
    po.setProjectId(release.projectId());
    po.setName(release.name());
    po.setStatus(release.status());
    po.setReleaseDate(release.releaseDate());
    po.setPublishedAt(release.publishedAt());
    po.setIsMilestone(release.isMilestone() ? 1 : 0);
    po.setStoryIds(write(release.storyIds()));
    po.setBugIds(write(release.bugIds()));
    po.setNotifyAccounts(write(release.notifyAccounts()));
    po.setDescription(release.description());
    po.setCreatedBy(release.createdBy());
    po.setCreatedAt(release.createdAt());
    po.setUpdatedBy(release.updatedBy());
    po.setUpdatedAt(release.updatedAt());
    po.setLockVersion(release.lockVersion());
    return po;
  }

  private List<Long> readLongList(String json) {
    return json == null || json.isBlank() ? List.of() : jsonMapper.readValue(json, LONG_LIST);
  }

  private List<String> readStringList(String json) {
    return json == null || json.isBlank() ? List.of() : jsonMapper.readValue(json, STRING_LIST);
  }

  private String write(List<?> value) {
    return value == null || value.isEmpty() ? null : jsonMapper.writeValueAsString(value);
  }
}
