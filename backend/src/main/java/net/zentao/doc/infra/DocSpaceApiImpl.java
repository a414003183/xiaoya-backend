package net.zentao.doc.infra;

import java.util.List;
import net.zentao.doc.api.DocSpaceApi;
import net.zentao.doc.app.DocAccess;
import net.zentao.platform.session.SessionPrincipal;
import org.springframework.stereotype.Component;

/** doc 域跨域读接口实现（doc 卡 §7 跨域供给；A2：其他域只经 doc.api）。 */
@Component
public class DocSpaceApiImpl implements DocSpaceApi {

  private final DocAccess access;

  public DocSpaceApiImpl(DocAccess access) {
    this.access = access;
  }

  @Override
  public List<Long> visibleSpaceIds(SessionPrincipal principal) {
    return access.visibleSpaceIds(principal);
  }
}
