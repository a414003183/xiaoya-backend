package net.zentao.platform.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import net.zentao.platform.notification.MarkNotificationReadHandler;
import net.zentao.platform.notification.NotificationQueryService;
import net.zentao.platform.notification.NotificationSseRegistry;
import net.zentao.platform.notification.NotificationView;
import net.zentao.platform.session.SessionResolver;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 通知四端点（platform 卡 §5）：列表 / 未读数 / 标记已读 / SSE 订阅。 */
@RestController
@RequestMapping("/api/v1")
public class NotificationController {

  private final NotificationQueryService queryService;
  private final MarkNotificationReadHandler markReadHandler;
  private final NotificationSseRegistry sseRegistry;
  private final SessionResolver resolver;

  public NotificationController(
      NotificationQueryService queryService,
      MarkNotificationReadHandler markReadHandler,
      NotificationSseRegistry sseRegistry,
      SessionResolver resolver) {
    this.queryService = queryService;
    this.markReadHandler = markReadHandler;
    this.sseRegistry = sseRegistry;
    this.resolver = resolver;
  }

  @GetMapping("/notifications")
  @Operation(operationId = "listNotifications")
  public DataEnvelope<NotificationQueryService.NotificationList> list(HttpServletRequest request) {
    return DataEnvelope.of(queryService.page(resolver.resolve(request), request.getParameterMap()));
  }

  @GetMapping("/notifications/unread-count")
  @Operation(operationId = "getNotificationUnreadCount")
  public DataEnvelope<NotificationUnreadCountView> unreadCount(HttpServletRequest request) {
    return DataEnvelope.of(new NotificationUnreadCountView(queryService.unreadCount(resolver.resolve(request))));
  }

  @PostMapping("/notifications/{notificationId}/read")
  @Operation(operationId = "markNotificationRead")
  public DataEnvelope<NotificationView> markRead(@PathVariable long notificationId, HttpServletRequest request) {
    return DataEnvelope.of(markReadHandler.markRead(resolver.resolve(request), notificationId));
  }

  @GetMapping(value = "/notifications/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @Operation(operationId = "subscribeNotificationStream", responses = {
      @ApiResponse(responseCode = "200", content = @Content(mediaType = "text/event-stream",
          schema = @Schema(type = "string"))),
  })
  public SseEmitter stream(
      @RequestHeader(value = "Last-Event-ID", required = false) Long lastEventId,
      HttpServletRequest request) {
    return sseRegistry.subscribe(resolver.resolve(request).account(), lastEventId);
  }

  public record NotificationUnreadCountView(long count) {}
}
