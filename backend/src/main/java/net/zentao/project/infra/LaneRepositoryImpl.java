package net.zentao.project.infra;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.row.Row;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import net.zentao.project.domain.Lane;
import net.zentao.project.domain.LaneRepository;
import org.springframework.stereotype.Component;

/** 看板列仓储实现（infra：PO ↔ 领域对象；软删走 deleted_at）。 */
@Component
public class LaneRepositoryImpl implements LaneRepository {

  private static final QueryColumn DELETED_AT = new QueryColumn("deleted_at");

  private final LaneMapper mapper;

  public LaneRepositoryImpl(LaneMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<Lane> findActiveById(long id) {
    return Optional.ofNullable(mapper.selectOneByCondition(new QueryColumn("id").eq(id).and(DELETED_AT.isNull())))
        .map(LaneRepositoryImpl::toDomain);
  }

  @Override
  public List<Lane> findActiveByBoardId(long boardId) {
    return mapper.selectListByQuery(QueryWrapper.create()
        .where(new QueryColumn("board_id").eq(boardId).and(DELETED_AT.isNull()))
        .orderBy(new QueryColumn("sort").asc(), new QueryColumn("id").asc())) // banned-words-ok：MyBatis-Flex 构造器方法名
        .stream()
        .map(LaneRepositoryImpl::toDomain)
        .toList();
  }

  @Override
  public Lane insert(Lane lane) {
    LanePO po = toPo(lane);
    po.setId(null);
    mapper.insert(po);
    return toDomain(mapper.selectOneById(po.getId()));
  }

  @Override
  public void update(Lane lane) {
    mapper.update(toPo(lane), false);
  }

  @Override
  public void softDelete(long id) {
    Db.updateByCondition("board_lane", Row.of("deleted_at", Instant.now()), new QueryColumn("id").eq(id));
  }

  private static Lane toDomain(LanePO po) {
    return new Lane(po.getId(), po.getBoardId() == null ? 0 : po.getBoardId(), po.getName(), po.getColor(),
        po.getWipLimit() == null ? -1 : po.getWipLimit(), po.getArchived() != null && po.getArchived() == 1,
        po.getSort() == null ? 0 : po.getSort());
  }

  private static LanePO toPo(Lane lane) {
    LanePO po = new LanePO();
    po.setId(lane.id() == 0 ? null : lane.id());
    po.setBoardId(lane.boardId());
    po.setName(lane.name());
    po.setColor(lane.color());
    po.setWipLimit(lane.wipLimit());
    po.setArchived(lane.archived() ? 1 : 0);
    po.setSort(lane.sort());
    return po;
  }
}
