package net.zentao.doc.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 文档仓储（doc 卡 §3.2）。 */
public interface DocRepository {

  Optional<Doc> findActiveById(long id);

  List<Doc> findActiveByIds(List<Long> ids);

  Doc insert(Doc doc);

  /** 全量覆盖；回读行后返回（lockVersion 由库内自增，回内存聚合会给出过期版本）。 */
  Optional<Doc> update(Doc doc);

  /** 免乐观锁路径：path 为派生列（同 project.updatePath 口径）。 */
  void updatePath(long id, String path);

  /** 免乐观锁路径：views 计数不影响其他字段的乐观锁（doc 卡 §4 阅读计数）。 */
  void updateViews(long id, int views);

  /** 软删单行（deleted_at 置位）。 */
  void softDelete(long id, String actor, Instant at);

  /** 软删子树：根 + path 前缀命中的全部后代（doc 卡 §4 delete 级联）。 */
  int softDeleteSubtree(String pathPrefix, String actor, Instant at);

  /** 子树重建：path 前缀整体替换（doc 卡 §4 move 级联）。 */
  void replacePathPrefix(String oldPrefix, String newPrefix);

  /** 某文档（含自身）是否为 root 的后代：move 成环判定用（42201）。 */
  boolean existsInSubtree(long rootId, String rootPath, long candidateId);

  /** 某目录下的未删文档数（目录真实删除守卫 42203）。 */
  long countLiveByCategory(long categoryId);

  Map<Long, Long> countLiveBySpaces(List<Long> spaceIds);

  List<Doc> queryPage(Object whereWrapper, int offset, int limit);

  long countByQuery(Object whereWrapper);
}
