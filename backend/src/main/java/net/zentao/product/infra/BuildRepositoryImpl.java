package net.zentao.product.infra;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.row.Row;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import net.zentao.platform.persistence.SoftDeletes;
import net.zentao.product.domain.Build;
import net.zentao.product.domain.BuildRepository;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** 构建仓储实现（infra；软删走 deleted_at）。 */
@Component
public class BuildRepositoryImpl implements BuildRepository {

  private static final QueryColumn DELETED_AT = new QueryColumn("deleted_at");
  private static final TypeReference<List<Long>> LONG_LIST = new TypeReference<>() {};

  private final BuildMapper mapper;
  private final JsonMapper jsonMapper;

  public BuildRepositoryImpl(BuildMapper mapper, JsonMapper jsonMapper) {
    this.mapper = mapper;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Optional<Build> findActiveById(long id) {
    return Optional.ofNullable(mapper.selectOneByCondition(new QueryColumn("id").eq(id).and(DELETED_AT.isNull())))
        .map(this::toDomain);
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<Build> queryPage(Object whereWrapper, int offset, int limit) {
    return mapper.selectListByQuery(((QueryWrapper) whereWrapper).limit(offset, limit)).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public long countByQuery(Object whereWrapper) {
    return mapper.selectCountByQuery((QueryWrapper) whereWrapper);
  }

  @Override
  public Build insert(Build build) {
    BuildPO po = toPo(build);
    po.setId(null);
    po.setLockVersion(0);
    mapper.insert(po);
    return toDomain(mapper.selectOneById(po.getId()));
  }

  @Override
  public Optional<Build> update(Build build) {
    return mapper.update(toPo(build), false) > 0 ? Optional.of(build) : Optional.empty();
  }

  @Override
  public Optional<Build> softDelete(long id) {
    Optional<Build> build = findActiveById(id);
    if (build.isEmpty()) {
      return Optional.empty();
    }
    SoftDeletes.apply("build", Row.of("deleted_at", Instant.now()), new QueryColumn("id").eq(id));
    return build;
  }

  @Override
  public boolean existsActiveByProduct(long productId) {
    return mapper.selectCountByCondition(
        new QueryColumn("product_id").eq(productId).and(DELETED_AT.isNull())) > 0;
  }

  private Build toDomain(BuildPO po) {
    return new Build(po.getId(), po.getProductId(), po.getBranchId() == null ? 0 : po.getBranchId(),
        po.getExecutionId() == null ? 0 : po.getExecutionId(), po.getProjectId() == null ? 0 : po.getProjectId(),
        po.getName(), po.getScmPath(), po.getFilePath(), po.getBuildDate(), po.getBuilder(),
        readLongList(po.getStoryIds()), readLongList(po.getBugIds()), po.getDescription(), po.getCreatedBy(),
        po.getCreatedAt(), po.getUpdatedBy(), po.getUpdatedAt(),
        po.getLockVersion() == null ? 0 : po.getLockVersion());
  }

  private BuildPO toPo(Build build) {
    BuildPO po = new BuildPO();
    po.setId(build.id() == 0 ? null : build.id());
    po.setProductId(build.productId());
    po.setBranchId(build.branchId());
    po.setExecutionId(build.executionId());
    po.setProjectId(build.projectId());
    po.setName(build.name());
    po.setScmPath(build.scmPath());
    po.setFilePath(build.filePath());
    po.setBuildDate(build.buildDate());
    po.setBuilder(build.builder());
    po.setStoryIds(write(build.storyIds()));
    po.setBugIds(write(build.bugIds()));
    po.setDescription(build.description());
    po.setCreatedBy(build.createdBy());
    po.setCreatedAt(build.createdAt());
    po.setUpdatedBy(build.updatedBy());
    po.setUpdatedAt(build.updatedAt());
    po.setLockVersion(build.lockVersion());
    return po;
  }

  private List<Long> readLongList(String json) {
    return json == null || json.isBlank() ? List.of() : jsonMapper.readValue(json, LONG_LIST);
  }

  private String write(List<?> value) {
    return value == null || value.isEmpty() ? null : jsonMapper.writeValueAsString(value);
  }
}
