package net.zentao.project.infra;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.row.Row;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import net.zentao.platform.persistence.SoftDeletes;
import net.zentao.project.domain.Stage;
import net.zentao.project.domain.StageRepository;
import org.springframework.stereotype.Component;

/** 阶段字典仓储实现（infra：PO ↔ 领域对象）。 */
@Component
public class StageRepositoryImpl implements StageRepository {

  private static final QueryColumn DELETED_AT = new QueryColumn("deleted_at");

  private final StageMapper mapper;

  public StageRepositoryImpl(StageMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<Stage> findActiveById(long id) {
    return Optional.ofNullable(mapper.selectOneByCondition(new QueryColumn("id").eq(id).and(DELETED_AT.isNull())))
        .map(StageRepositoryImpl::toDomain);
  }

  @Override
  public List<Stage> findAllActive() {
    return mapper.selectListByQuery(QueryWrapper.create().where(DELETED_AT.isNull())
        .orderBy(new QueryColumn("sort").asc(), new QueryColumn("id").asc())).stream() // banned-words-ok：MyBatis-Flex 构造器方法名
        .map(StageRepositoryImpl::toDomain)
        .toList();
  }

  @Override
  public Stage insert(Stage stage) {
    StagePO po = toPo(stage);
    po.setId(null);
    mapper.insert(po);
    return toDomain(mapper.selectOneById(po.getId()));
  }

  @Override
  public void update(Stage stage) {
    mapper.update(toPo(stage), false);
  }

  @Override
  public void softDelete(long id) {
    SoftDeletes.apply("stage", Row.of("deleted_at", Instant.now()), new QueryColumn("id").eq(id));
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<Stage> queryPage(Object whereWrapper, int offset, int limit) {
    return mapper.selectListByQuery(((QueryWrapper) whereWrapper).limit(offset, limit)).stream()
        .map(StageRepositoryImpl::toDomain)
        .toList();
  }

  @Override
  public long countByQuery(Object whereWrapper) {
    return mapper.selectCountByQuery((QueryWrapper) whereWrapper);
  }

  private static Stage toDomain(StagePO po) {
    return new Stage(po.getId(), po.getName(), po.getPercent(), po.getType(), po.getProjectModel(),
        po.getSort() == null ? 0 : po.getSort(), po.getCreatedBy(), po.getCreatedAt(), po.getUpdatedBy(),
        po.getUpdatedAt());
  }

  private static StagePO toPo(Stage stage) {
    StagePO po = new StagePO();
    po.setId(stage.id() == 0 ? null : stage.id());
    po.setName(stage.name());
    po.setPercent(stage.percent());
    po.setType(stage.type());
    po.setProjectModel(stage.projectModel());
    po.setSort(stage.sort());
    po.setCreatedBy(stage.createdBy());
    po.setCreatedAt(stage.createdAt());
    po.setUpdatedBy(stage.updatedBy());
    po.setUpdatedAt(stage.updatedAt());
    return po;
  }
}
