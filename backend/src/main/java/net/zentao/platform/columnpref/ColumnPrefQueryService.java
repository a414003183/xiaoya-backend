package net.zentao.platform.columnpref;

import net.zentao.platform.session.SessionPrincipal;
import org.springframework.stereotype.Component;

/** 列设置读取（platform「列设置」；个人级：只读当前账号自己的行，未设置回 columns=null）。 */
@Component
public class ColumnPrefQueryService {

  private final ColumnPrefRepository repository;

  public ColumnPrefQueryService(ColumnPrefRepository repository) {
    this.repository = repository;
  }

  public ColumnPrefView get(SessionPrincipal principal, String resource) {
    String normalized = ColumnPref.normalizeResource(resource);
    return ColumnPrefView.of(normalized, repository.find(principal.accountId(), normalized).orElse(null));
  }
}
