package net.zentao.platform.columnpref;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import net.zentao.platform.session.SessionPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 列设置保存/重置（platform「列设置」；个人级：account_id 恒取会话账号，无跨账号写面，故无需权限码）。
 */
@Component
public class ColumnPrefHandlers {

  private final ColumnPrefRepository repository;

  public ColumnPrefHandlers(ColumnPrefRepository repository) {
    this.repository = repository;
  }

  /** 整表覆盖保存（同账号同资源幂等）；列项非法 → 42201 带 fields.columns。 */
  @Transactional
  public ColumnPrefView save(SessionPrincipal principal, String resource, List<ColumnPrefItem> columns) {
    String normalized = ColumnPref.normalizeResource(resource);
    List<ColumnPrefItem> items = ColumnPref.normalizeColumns(columns);
    repository.upsert(principal.accountId(), normalized, items);
    return ColumnPrefView.of(normalized, new ColumnPref(normalized, items));
  }

  /** 删行 = 回页面默认；无行时静默成功。 */
  @Transactional
  public void reset(SessionPrincipal principal, String resource) {
    repository.delete(principal.accountId(), ColumnPref.normalizeResource(resource));
  }

  /** 请求体（contract {@code ColumnPrefUpdateRequest}）。 */
  public record ColumnPrefUpdateRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ColumnPrefItem> columns) {}
}
