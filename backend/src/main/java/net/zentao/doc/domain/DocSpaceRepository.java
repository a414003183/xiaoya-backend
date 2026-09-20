package net.zentao.doc.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 文档库仓储（doc 卡 §3.1）。 */
public interface DocSpaceRepository {

  Optional<DocSpace> findActiveById(long id);

  List<DocSpace> findAllActive();

  List<DocSpace> findActiveByIds(List<Long> ids);

  /** mine 库幂等口径：同一创建人已有未删 mine 库即复用（doc 卡 §5）。 */
  Optional<DocSpace> findMineByCreator(String account);

  DocSpace insert(DocSpace space);

  /** 全量覆盖；回读行后返回（lockVersion 由库内自增，回内存聚合会给出过期版本）。 */
  Optional<DocSpace> update(DocSpace space);

  /** isDefault 唯一置位（同一归属对象至多一个 true）：清掉同归属其他库的主库标记。 */
  void clearDefaultFlag(String type, long ownerId, long excludeSpaceId);

  /** 软删（库内无未删文档为前置守卫，由 app 层判定）。 */
  void softDelete(long id, String actor, java.time.Instant at);

  List<DocSpace> queryPage(Object whereWrapper, int offset, int limit);

  long countByQuery(Object whereWrapper);

  /** 库内未删文档数（派生 docCount）。 */
  Map<Long, Long> countLiveDocsBySpaces(List<Long> spaceIds);
}
