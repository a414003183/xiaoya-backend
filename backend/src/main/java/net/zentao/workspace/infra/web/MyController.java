package net.zentao.workspace.infra.web;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import net.zentao.platform.activity.ActivityQueryService;
import net.zentao.platform.rbac.RequirePrivilege;
import net.zentao.platform.session.SessionResolver;
import net.zentao.platform.web.DataEnvelope;
import net.zentao.quality.api.BugList;
import net.zentao.requirement.api.StoryList;
import net.zentao.task.api.TaskList;
import net.zentao.workspace.api.MySummaryView;
import net.zentao.workspace.app.MyQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 我的地盘端点（workspace 卡 §5 /my 族 5 行）：跨域只读聚合，权限码 my-view。 */
@RestController
@RequestMapping("/api/v1")
public class MyController {

  private final MyQueryService queryService;
  private final SessionResolver resolver;

  public MyController(MyQueryService queryService, SessionResolver resolver) {
    this.queryService = queryService;
    this.resolver = resolver;
  }

  @GetMapping("/my/summary")
  @Operation(operationId = "getMySummary")
  @RequirePrivilege("my-view")
  public DataEnvelope<MySummaryView> summary(HttpServletRequest request) {
    return DataEnvelope.of(queryService.summary(resolver.resolve(request)));
  }

  @GetMapping("/my/tasks")
  @Operation(operationId = "listMyTasks")
  @RequirePrivilege("my-view")
  public DataEnvelope<TaskList> tasks(@RequestParam(required = false) String role, HttpServletRequest request) {
    return DataEnvelope.of(
        queryService.tasks(resolver.resolve(request), role, request.getParameterMap()));
  }

  @GetMapping("/my/bugs")
  @Operation(operationId = "listMyBugs")
  @RequirePrivilege("my-view")
  public DataEnvelope<BugList> bugs(@RequestParam(required = false) String role, HttpServletRequest request) {
    return DataEnvelope.of(queryService.bugs(resolver.resolve(request), role, request.getParameterMap()));
  }

  @GetMapping("/my/stories")
  @Operation(operationId = "listMyStories")
  @RequirePrivilege("my-view")
  public DataEnvelope<StoryList> stories(@RequestParam(required = false) String role, HttpServletRequest request) {
    return DataEnvelope.of(queryService.stories(resolver.resolve(request), role, request.getParameterMap()));
  }

  @GetMapping("/my/activities")
  @Operation(operationId = "listMyActivities")
  @RequirePrivilege("my-view")
  public DataEnvelope<ActivityQueryService.ActivityList> activities(
      @RequestParam(required = false) Integer limit, @RequestParam(required = false) Long beforeId,
      HttpServletRequest request) {
    return DataEnvelope.of(queryService.activities(resolver.resolve(request), limit, beforeId));
  }
}
