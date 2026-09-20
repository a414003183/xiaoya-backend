package net.zentao.project.domain;

import java.util.List;
import java.util.Optional;

/** 看板空间仓储（domain 接口；实现 infra，A1）。 */
public interface BoardSpaceRepository {

  Optional<BoardSpace> findActiveById(long id);

  /** 全部未删空间（可见集判定的数据源，§7）。 */
  List<BoardSpace> findAllActive();

  BoardSpace insert(BoardSpace space);

  Optional<BoardSpace> update(BoardSpace space);

  List<BoardSpace> queryPage(Object whereWrapper, int offset, int limit);

  long countByQuery(Object whereWrapper);

  /** 软删（A-07）：置 deleted_at。 */
  void softDelete(long id);
}
