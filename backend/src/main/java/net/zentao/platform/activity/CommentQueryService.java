package net.zentao.platform.activity;

import java.util.List;
import java.util.Map;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
import net.zentao.platform.session.SessionPrincipal;
import org.springframework.stereotype.Component;

/** 评论列表查询（platform 卡 §5：对象不可见 → 40302）。 */
@Component
public class CommentQueryService {

  private final CommentRepository repository;
  private final ObjectVisibilityRegistry visibility;

  public CommentQueryService(CommentRepository repository, ObjectVisibilityRegistry visibility) {
    this.repository = repository;
    this.visibility = visibility;
  }

  public CommentList page(SessionPrincipal principal, String objectType, long objectId, int page, int limit) {
    if (!visibility.isVisible(principal, objectType, objectId)) {
      throw ApiException.keyed(ErrorCode.DATA_FORBIDDEN, "comment.guard.objectInvisible");
    }
    Map<String, Object> result = repository.page(objectType, objectId, null, page, limit);
    @SuppressWarnings("unchecked")
    List<CommentRepository.CommentView> items = (List<CommentRepository.CommentView>) result.get("items");
    return new CommentList(items, (Long) result.get("total"));
  }

  /** CommentList（contract：items + total；schema 名与契约对齐）。 */
  public record CommentList(List<CommentRepository.CommentView> items, long total) {}
}
