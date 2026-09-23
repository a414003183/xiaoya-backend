package net.zentao.project.infra;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.row.Row;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import net.zentao.platform.persistence.SoftDeletes;
import net.zentao.project.domain.Card;
import net.zentao.project.domain.CardRepository;
import org.springframework.stereotype.Component;

/** 看板卡片仓储实现（infra：PO ↔ 领域对象）。 */
@Component
public class CardRepositoryImpl implements CardRepository {

  private static final QueryColumn DELETED_AT = new QueryColumn("deleted_at");

  private final CardMapper mapper;

  public CardRepositoryImpl(CardMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<Card> findActiveById(long id) {
    return Optional.ofNullable(mapper.selectOneByCondition(new QueryColumn("id").eq(id).and(DELETED_AT.isNull())))
        .map(CardRepositoryImpl::toDomain);
  }

  @Override
  public List<Card> findActiveByBoardId(long boardId) {
    return mapper.selectListByQuery(QueryWrapper.create()
        .where(new QueryColumn("board_id").eq(boardId).and(DELETED_AT.isNull()))
        .orderBy(new QueryColumn("lane_id").asc(), new QueryColumn("sort").asc(), // banned-words-ok：MyBatis-Flex 构造器方法名
            new QueryColumn("id").asc()))
        .stream()
        .map(CardRepositoryImpl::toDomain)
        .toList();
  }

  @Override
  public long countActiveInLane(long laneId) {
    return mapper.selectCountByCondition(new QueryColumn("lane_id").eq(laneId).and(DELETED_AT.isNull()));
  }

  @Override
  public long countActiveInLaneExcluding(long laneId, long excludeCardId) {
    return mapper.selectCountByCondition(new QueryColumn("lane_id").eq(laneId)
        .and(new QueryColumn("id").ne(excludeCardId))
        .and(DELETED_AT.isNull()));
  }

  @Override
  public Card insert(Card card) {
    CardPO po = toPo(card);
    po.setId(null);
    po.setLockVersion(0);
    mapper.insert(po);
    return toDomain(mapper.selectOneById(po.getId()));
  }

  @Override
  public Optional<Card> update(Card card) {
    // 全量覆盖（含 null 字段）+ 乐观锁：version 不符 → update 行数 0 → 上层 40901
    if (mapper.update(toPo(card), false) <= 0) {
      return Optional.empty();
    }
    return findActiveById(card.id());
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<Card> queryPage(Object whereWrapper, int offset, int limit) {
    return mapper.selectListByQuery(((QueryWrapper) whereWrapper).limit(offset, limit)).stream()
        .map(CardRepositoryImpl::toDomain)
        .toList();
  }

  @Override
  public long countByQuery(Object whereWrapper) {
    return mapper.selectCountByQuery((QueryWrapper) whereWrapper);
  }

  @Override
  public long countActiveByBoardId(long boardId) {
    return mapper.selectCountByCondition(new QueryColumn("board_id").eq(boardId).and(DELETED_AT.isNull()));
  }

  @Override
  public void softDelete(long id) {
    SoftDeletes.apply("board_card", Row.of("deleted_at", Instant.now()), new QueryColumn("id").eq(id));
  }

  private static Card toDomain(CardPO po) {
    return new Card(po.getId(), po.getBoardId() == null ? 0 : po.getBoardId(),
        po.getLaneId() == null ? 0 : po.getLaneId(), po.getName(), po.getDescription(), po.getStatus(),
        po.getPriority() == null ? 3 : po.getPriority(), po.getAssignee(), po.getBeginDate(), po.getEndDate(),
        po.getEstimateHours(), po.getProgress() == null ? 0 : po.getProgress(), po.getColor(),
        po.getArchived() != null && po.getArchived() == 1, po.getSort() == null ? 0 : po.getSort(),
        po.getCreatedBy(), po.getCreatedAt(), po.getUpdatedBy(), po.getUpdatedAt(),
        po.getLockVersion() == null ? 0 : po.getLockVersion());
  }

  private static CardPO toPo(Card card) {
    CardPO po = new CardPO();
    po.setId(card.id() == 0 ? null : card.id());
    po.setBoardId(card.boardId());
    po.setLaneId(card.laneId());
    po.setName(card.name());
    po.setDescription(card.description());
    po.setStatus(card.status());
    po.setPriority(card.priority());
    po.setAssignee(card.assignee());
    po.setBeginDate(card.beginDate());
    po.setEndDate(card.endDate());
    po.setEstimateHours(card.estimateHours());
    po.setProgress(card.progress());
    po.setColor(card.color());
    po.setArchived(card.archived() ? 1 : 0);
    po.setSort(card.sort());
    po.setCreatedBy(card.createdBy());
    po.setCreatedAt(card.createdAt());
    po.setUpdatedBy(card.updatedBy());
    po.setUpdatedAt(card.updatedAt());
    po.setLockVersion(card.lockVersion());
    return po;
  }
}
