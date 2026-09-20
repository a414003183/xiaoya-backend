package net.zentao.project.domain;

import java.util.List;
import java.util.Optional;

/** 看板卡片仓储（domain 接口；实现 infra，A1）。 */
public interface CardRepository {

  Optional<Card> findActiveById(long id);

  /** 看板下未删卡片（整板 cards[] 数据源），按 lane_id/sort/id 升序。 */
  List<Card> findActiveByBoardId(long boardId);

  /** 列内未删卡片数（WIP 上限与删列守卫）。 */
  long countActiveInLane(long laneId);

  /** 列内除某张卡之外的未删卡片数（拖拽同列不改 WIP 计数）。 */
  long countActiveInLaneExcluding(long laneId, long excludeCardId);

  Card insert(Card card);

  Optional<Card> update(Card card);

  List<Card> queryPage(Object whereWrapper, int offset, int limit);

  long countByQuery(Object whereWrapper);

  /** 看板下未删卡片数（看板删除守卫）。 */
  long countActiveByBoardId(long boardId);

  /** 软删（A-07）：置 deleted_at。 */
  void softDelete(long id);
}
