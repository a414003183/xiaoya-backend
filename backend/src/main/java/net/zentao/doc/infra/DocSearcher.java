package net.zentao.doc.infra;

import java.util.List;
import net.zentao.doc.app.DocQueryService;
import net.zentao.platform.search.SearchResultView;
import net.zentao.platform.search.SearchScope;
import net.zentao.platform.session.SessionPrincipal;
import org.springframework.stereotype.Component;

/** doc 搜索 scope 执行器（platform 卡 §5.2：searchable = title/keywords/当前正文，DataScope 先于 LIKE）。 */
@Component
public class DocSearcher implements SearchScope.Executor {

  private final DocQueryService queryService;

  public DocSearcher(DocQueryService queryService) {
    this.queryService = queryService;
  }

  @Override
  public List<SearchResultView> search(String q, int limit, SessionPrincipal principal) {
    return queryService.search(q, limit, principal);
  }
}
