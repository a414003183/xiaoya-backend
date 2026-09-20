package net.zentao.project.domain;

import java.util.List;
import java.util.Optional;

/** 看板仓储（domain 接口；实现 infra，A1）。 */
public interface BoardRepository {

  Optional<Board> findActiveById(long id);

  /** 空间下未删看板（空间详情 boards[] 与可见性判定的数据源），按 sort/id 升序。 */
  List<Board> findActiveBySpaceId(long spaceId);

  Board insert(Board board);

  Optional<Board> update(Board board);

  /** 空间下未删看板数（空间删除守卫）。 */
  long countActiveBySpaceId(long spaceId);

  /** 软删（A-07）：置 deleted_at。 */
  void softDelete(long id);
}
