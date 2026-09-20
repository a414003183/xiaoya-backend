package net.zentao.platform.columnpref;

import java.util.List;
import java.util.Optional;

/**
 * 列设置仓储（platform「列设置」能力）：账号 × 资源 至多一行，读写恒按账号过滤。
 * 实现见 {@code net.zentao.platform.columnpref.ColumnPrefRepositoryImpl}（JSON 文本列）。
 */
public interface ColumnPrefRepository {

  /** 未设置返回 empty（前端据此用页面默认列）。 */
  Optional<ColumnPref> find(long accountId, String resource);

  /** 整表覆盖保存（同账号同资源幂等）。 */
  ColumnPref upsert(long accountId, String resource, List<ColumnPrefItem> columns);

  /** 删行 = 回页面默认；无行时静默成功。 */
  void delete(long accountId, String resource);
}
