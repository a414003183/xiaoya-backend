package net.zentao.project.domain;

import java.util.List;
import java.util.Optional;

/** 看板列仓储（domain 接口；实现 infra，A1）。 */
public interface LaneRepository {

  Optional<Lane> findActiveById(long id);

  /** 看板下未删列（整板 lanes[] 数据源），按 sort/id 升序。 */
  List<Lane> findActiveByBoardId(long boardId);

  Lane insert(Lane lane);

  /** 全量写回（lane 无乐观锁列，LaneUpdateRequest 亦无 lockVersion）。 */
  void update(Lane lane);

  void softDelete(long id);
}
