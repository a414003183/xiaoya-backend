package net.zentao.platform.web;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import net.zentao.platform.activity.CommentQueryService;
import net.zentao.platform.activity.CommentRepository;
import net.zentao.platform.activity.PostCommentHandler;
import net.zentao.platform.session.SessionResolver;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** POST/GET /comments（platform 卡 §5：登录 + 对象可见）。 */
@RestController
@RequestMapping("/api/v1")
@Validated
public class CommentController {

  private final PostCommentHandler postHandler;
  private final CommentQueryService queryService;
  private final SessionResolver resolver;

  public CommentController(PostCommentHandler postHandler, CommentQueryService queryService, SessionResolver resolver) {
    this.postHandler = postHandler;
    this.queryService = queryService;
    this.resolver = resolver;
  }

  @PostMapping("/comments")
  @Operation(operationId = "createComment")
  public DataEnvelope<CommentRepository.CommentView> create(@Valid @RequestBody CommentCreateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(postHandler.post(resolver.resolve(request), body.objectType(), body.objectId(), body.content()));
  }

  @GetMapping("/comments")
  @Operation(operationId = "listComments")
  public DataEnvelope<CommentQueryService.CommentList> list(
      @RequestParam String objectType,
      @RequestParam long objectId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
      HttpServletRequest request) {
    return DataEnvelope.of(queryService.page(resolver.resolve(request), objectType, objectId, page, limit));
  }

  public record CommentCreateRequest(
      @NotBlank String objectType, @NotNull Long objectId, @NotBlank @Size(max = 10000) String content) {}
}
