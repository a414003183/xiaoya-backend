package net.zentao.platform.activity;

import java.time.Instant;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 发表评论（platform 卡 §2）：同事务向 activity 镜像一条 commented，动态流保持单表渲染；
 * 对象不可见 → 40302。
 */
@Component
public class PostCommentHandler {

  private final CommentRepository commentRepository;
  private final ActivityRecorder activityRecorder;
  private final ObjectVisibilityRegistry visibility;

  public PostCommentHandler(CommentRepository commentRepository, ActivityRecorder activityRecorder,
      ObjectVisibilityRegistry visibility) {
    this.commentRepository = commentRepository;
    this.activityRecorder = activityRecorder;
    this.visibility = visibility;
  }

  @Transactional
  public CommentRepository.CommentView post(SessionPrincipal principal, String objectType, long objectId, String content) {
    if (!visibility.isVisible(principal, objectType, objectId)) {
      throw ApiException.dataForbidden("对象不可见。");
    }
    CommentPO po = new CommentPO();
    po.setObjectType(objectType);
    po.setObjectId(objectId);
    po.setContent(content);
    po.setCreatedBy(principal.account());
    po.setCreatedAt(Instant.now());
    commentRepository.insert(po);
    activityRecorder.record(principal.account(), objectType, objectId, "commented", null, content);
    return commentRepository.toView(po);
  }
}
