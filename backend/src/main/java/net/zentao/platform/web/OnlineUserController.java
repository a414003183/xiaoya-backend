package net.zentao.platform.web;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import net.zentao.platform.audit.Audit;
import net.zentao.platform.rbac.RequirePrivilege;
import net.zentao.platform.session.OnlineUserQueryService;
import net.zentao.platform.session.SessionRepository;
import net.zentao.platform.session.SessionResolver;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 在线用户（T13 P1-1）：读 session 表现存行 + 强退（删行 → 该 cookie 下一请求 40101）。
 *
 * <p>两个端点的 id 都是会话行主键 = token 的 sha256，不是 cookie 值（T51 SEC-03：库里也只有摘要）：
 * 原样出网不等于交出会话（摘要冒充不了 cookie），但足以定位并强退任意在线会话。
 * 强退幂等（行已消失也算成功）：管理意图是「这条会话没了」，重复点或会话刚过期都不是错误。
 */
@RestController
@RequestMapping("/api/v1")
public class OnlineUserController {

  private final OnlineUserQueryService queryService;
  private final SessionRepository repository;
  private final SessionResolver resolver;

  public OnlineUserController(
      OnlineUserQueryService queryService, SessionRepository repository, SessionResolver resolver) {
    this.queryService = queryService;
    this.repository = repository;
    this.resolver = resolver;
  }

  @GetMapping("/online-users")
  @Operation(operationId = "listOnlineUsers")
  @RequirePrivilege("online-user-view")
  public DataEnvelope<OnlineUserQueryService.OnlineUserList> list(HttpServletRequest request) {
    return DataEnvelope.of(queryService.page(request.getParameterMap(), resolver.resolve(request).sessionId()));
  }

  @DeleteMapping("/online-users/{sessionId}")
  @Operation(operationId = "kickOnlineUser")
  @RequirePrivilege("online-user-kick")
  @Audit(action = "online-user-kick", objectType = "session")
  public DataEnvelope<Void> kick(@PathVariable String sessionId) {
    repository.delete(sessionId);
    return DataEnvelope.empty();
  }
}
