package net.zentao.platform.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** 发评论（platform 卡 §2/§8）：同事务双写 comment + activity(commented) 镜像；对象不可见 40302。 */
@SpringBootTest
class PostCommentHandlerTest {

  @Autowired
  PostCommentHandler handler;

  @Autowired
  ObjectVisibilityRegistry visibility;

  @Autowired
  JdbcTemplate jdbcTemplate;

  private final SessionPrincipal actor = new SessionPrincipal(1, "admin");

  @BeforeEach
  void resetVisibility() {
    visibility.register("secret", (principal, objectId) -> false);
    visibility.register("account", (principal, objectId) -> true);
  }

  @Test
  @DisplayName("发评论同事务落 comment 行 + activity(commented) 镜像")
  void writesCommentAndActivityMirror() {
    CommentRepository.CommentView view = handler.post(actor, "account", 1, "测试评论内容");
    assertTrue(view.id() > 0);

    Integer commentRows = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM comment WHERE id = ?", Integer.class, view.id());
    assertEquals(1, commentRows);

    Integer activityRows = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM activity WHERE object_type = 'account' AND object_id = 1 AND action = 'commented' AND remark = ?",
        Integer.class, "测试评论内容");
    assertEquals(1, activityRows, "activity 应有 commented 镜像");
  }

  @Test
  @DisplayName("对象不可见 → 40302，且不落任何行")
  void invisibleObjectRejected() {
    AtomicBoolean checked = new AtomicBoolean(false);
    visibility.register("secret", (principal, objectId) -> {
      checked.set(true);
      return false;
    });
    ApiException exception = assertThrows(ApiException.class,
        () -> handler.post(actor, "secret", 42, "不应落库"));
    assertEquals(40302, exception.errorCode().code());
    assertTrue(checked.get(), "可见性谓词应被调用");

    Integer commentRows = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM comment WHERE object_type = 'secret'", Integer.class);
    assertEquals(0, commentRows);
  }
}
