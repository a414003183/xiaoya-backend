package net.zentao.requirement.app;

import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.web.CommentRequest;
import net.zentao.requirement.api.StoryView;
import org.springframework.stereotype.Component;

/**
 * 需求动作名分派（跨域调用口，project 域执行需求看板拖拽用）：把 requirement 卡 §4 的动作名落到本域
 * 命令处理器，守卫/副作用仍由各处理器与 workflow/story.yml 裁决——本类不复制任何状态规则。
 * 无请求体的动作（change-done/close）按其自身字段校验返回 42201，拖拽路径由调用方按 42201 处理。
 */
@Component
public class StoryActionDispatcher {

  private final SubmitReviewStoryHandler submitReviewHandler;
  private final PassStoryHandler passHandler;
  private final RejectStoryHandler rejectHandler;
  private final ChangeStoryHandler changeHandler;
  private final ChangeDoneStoryHandler changeDoneHandler;
  private final CloseStoryHandler closeHandler;
  private final ActivateStoryHandler activateHandler;

  public StoryActionDispatcher(SubmitReviewStoryHandler submitReviewHandler, PassStoryHandler passHandler,
      RejectStoryHandler rejectHandler, ChangeStoryHandler changeHandler, ChangeDoneStoryHandler changeDoneHandler,
      CloseStoryHandler closeHandler, ActivateStoryHandler activateHandler) {
    this.submitReviewHandler = submitReviewHandler;
    this.passHandler = passHandler;
    this.rejectHandler = rejectHandler;
    this.changeHandler = changeHandler;
    this.changeDoneHandler = changeDoneHandler;
    this.closeHandler = closeHandler;
    this.activateHandler = activateHandler;
  }

  public StoryView fire(SessionPrincipal actor, long storyId, String action, String comment) {
    return switch (action == null ? "" : action) {
      case "submit-review" -> submitReviewHandler.handle(actor, storyId,
          new SubmitReviewStoryHandler.StorySubmitReviewRequest(null, comment));
      case "pass" -> passHandler.handle(actor, storyId, new PassStoryHandler.StoryPassRequest(comment));
      case "reject" -> rejectHandler.handle(actor, storyId, new RejectStoryHandler.StoryRejectRequest(comment));
      case "change" -> changeHandler.handle(actor, storyId);
      case "change-done" -> changeDoneHandler.handle(actor, storyId, null);
      case "close" -> closeHandler.handle(actor, storyId, new CloseStoryHandler.StoryCloseRequest(null, null, comment));
      case "activate" -> activateHandler.handle(actor, storyId,
          comment == null ? null : new CommentRequest(comment));
      default -> throw ApiException.keyed(ErrorCode.BAD_REQUEST, "story.action.notCrossDomain", action);
    };
  }
}
