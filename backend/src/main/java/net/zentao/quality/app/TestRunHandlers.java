package net.zentao.quality.app;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.zentao.org.api.AccountApi;
import net.zentao.platform.meta.FieldDefValidator;
import net.zentao.platform.activity.ActivityRecorder;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.web.CommentRequest;
import net.zentao.platform.workflow.WorkflowEngine;
import net.zentao.platform.workflow.WorkflowTarget;
import net.zentao.product.api.ProductApi;
import net.zentao.project.api.ExecutionApi;
import net.zentao.project.api.ProjectView;
import net.zentao.quality.api.ResultView;
import net.zentao.quality.api.TestRunView;
import net.zentao.quality.domain.Result;
import net.zentao.quality.domain.ResultRepository;
import net.zentao.quality.domain.TestCase;
import net.zentao.quality.domain.TestCaseRepository;
import net.zentao.quality.domain.TestRun;
import net.zentao.quality.domain.TestRunRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 测试单命令（quality 卡 §5 TestRun 14 端点中除 record-result 的全部写路径）。
 * 创建校验执行可见性（ExecutionApi.requireExecution → 40401/40302），projectId 由执行冗余；
 * close 的 realFinishedAt 守卫产 42201。
 */
@Component
public class TestRunHandlers {

  static final Set<String> TYPES = Set.of("integrate", "system", "acceptance", "performance", "safety");

  private final TestRunRepository repository;
  private final ResultRepository resultRepository;
  private final TestCaseRepository caseRepository;
  private final ProductApi productApi;
  private final ExecutionApi executionApi;
  private final AccountApi accountApi;
  private final WorkflowEngine engine;
  private final ActivityRecorder activityRecorder;
  private final FieldDefValidator fieldDefValidator;

  public TestRunHandlers(TestRunRepository repository, ResultRepository resultRepository,
      TestCaseRepository caseRepository, ProductApi productApi, ExecutionApi executionApi,
      AccountApi accountApi, WorkflowEngine engine, ActivityRecorder activityRecorder,
      FieldDefValidator fieldDefValidator) {
    this.fieldDefValidator = fieldDefValidator;
    this.repository = repository;
    this.resultRepository = resultRepository;
    this.caseRepository = caseRepository;
    this.productApi = productApi;
    this.executionApi = executionApi;
    this.accountApi = accountApi;
    this.engine = engine;
    this.activityRecorder = activityRecorder;
  }

  public record TestRunCreateRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long executionId, Long buildId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name, String owner, Integer priority,
      @Schema(allowableValues = {"acceptance", "integrate", "performance", "safety", "system"}) String type,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate beginDate,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate endDate, String description,
      List<String> members, List<String> notifyAccounts, Map<String, Object> customFields) {}

  public record TestRunUpdateRequest(
      String name, String owner, Integer priority,
      @Schema(allowableValues = {"acceptance", "integrate", "performance", "safety", "system"}) String type,
      LocalDate beginDate, LocalDate endDate,
      Long buildId, String description, List<String> members, List<String> notifyAccounts,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer lockVersion) {}

  public record TestRunCloseRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant realFinishedAt, String comment) {}

  public record TestRunLinkCasesRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Long> caseIds, String assignee) {}

  public record AssignRunCaseRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String assignee) {}

  /** 详情/动作前置：40401 → 产品不可见 40302（quality 卡 §7）。 */
  TestRun require(SessionPrincipal actor, long testRunId) {
    TestRun run = repository.findActiveById(testRunId).orElseThrow(() -> ApiException.notFound("测试单"));
    if (!productApi.canAccess(actor, run.productId())) {
      throw ApiException.dataForbidden("无权访问该测试单。");
    }
    return run;
  }

  /** 软删测试单（A-07）：test_run_case 历史行保留；已回填 reportId 的行不清；删后详情 40401。 */
  @Transactional
  public void delete(SessionPrincipal actor, long testRunId) {
    TestRun run = require(actor, testRunId);
    repository.softDelete(run.id(), actor.account(), Instant.now());
    activityRecorder.record(actor.account(), "testRun", run.id(), "deleted", null, null);
  }

  @Transactional
  public TestRunView create(SessionPrincipal actor, long productId, TestRunCreateRequest command) {
    productApi.requireVisible(actor, productId);
    if (command.executionId() == null) {
      throw ApiException.validation(Map.of("executionId", "required"));
    }
    ProjectView execution = executionApi.requireExecution(actor, command.executionId());
    validate(command.name(), command.priority(), command.type(), command.beginDate(), command.endDate(),
        command.owner(), command.members(), command.notifyAccounts());
    fieldDefValidator.validate("testRun", command.customFields() == null ? java.util.Map.of() : command.customFields(), true);
    TestRun run = repository.insert(new TestRun(
        0,
        productId,
        execution.parentId(),
        command.executionId(),
        command.buildId() == null ? 0 : command.buildId(),
        command.name().trim(),
        command.owner(),
        command.priority() == null ? 3 : command.priority(),
        command.type(),
        command.beginDate(),
        command.endDate(),
        null,
        null,
        command.description(),
        command.members(),
        command.notifyAccounts(),
        "wait",
        null,
        command.customFields(),
        actor.account(),
        Instant.now(),
        null,
        null,
        0));
    return TestRunView.of(run);
  }

  @Transactional
  public TestRunView update(SessionPrincipal actor, long testRunId, TestRunUpdateRequest command) {
    TestRun run = require(actor, testRunId);
    if (command.lockVersion() == null || command.lockVersion() != run.lockVersion()) {
      throw ApiException.lockConflict("数据已被他人修改，请刷新后重试。");
    }
    LocalDate beginDate = command.beginDate() == null ? run.beginDate() : command.beginDate();
    LocalDate endDate = command.endDate() == null ? run.endDate() : command.endDate();
    validate(command.name(), command.priority(), command.type(), beginDate, endDate, command.owner(),
        command.members(), command.notifyAccounts());
    run.update(command.name(), command.owner(), command.priority(), command.type(), command.beginDate(),
        command.endDate(), command.buildId(), command.description(), command.members(),
        command.notifyAccounts());
    run.markUpdatedBy(actor.account());
    return TestRunView.of(save(run));
  }

  @Transactional
  public TestRunView start(SessionPrincipal actor, long testRunId) {
    return fire(actor, testRunId, "start", null, run -> {});
  }

  @Transactional
  public TestRunView block(SessionPrincipal actor, long testRunId, CommentRequest command) {
    return fire(actor, testRunId, "block", commentOf(command), run -> {});
  }

  @Transactional
  public TestRunView activate(SessionPrincipal actor, long testRunId, CommentRequest command) {
    return fire(actor, testRunId, "activate", commentOf(command), run -> {});
  }

  @Transactional
  public TestRunView close(SessionPrincipal actor, long testRunId, TestRunCloseRequest command) {
    if (command.realFinishedAt() == null) {
      throw ApiException.validation(Map.of("realFinishedAt", "required"));
    }
    return fire(actor, testRunId, "close", command.comment(), run -> {
      try {
        run.requireClosable(command.realFinishedAt());
      } catch (IllegalArgumentException e) {
        throw ApiException.validation(Map.of("realFinishedAt", "outOfRange"));
      }
      run.closeWith(command.realFinishedAt());
    });
  }

  @Transactional
  public long linkCases(SessionPrincipal actor, long testRunId, TestRunLinkCasesRequest command) {
    TestRun run = require(actor, testRunId);
    if (command.caseIds() == null || command.caseIds().isEmpty()) {
      throw ApiException.validation(Map.of("caseIds", "required"));
    }
    if (command.assignee() != null) {
      validateAccounts(List.of(command.assignee()));
    }
    List<Long> distinct = command.caseIds().stream().distinct().toList();
    long matched = caseRepository.findActiveByIds(distinct).stream()
        .filter(testCase -> testCase.productId() == run.productId() && testCase.libraryId() == 0)
        .count();
    if (matched != distinct.size()) {
      throw ApiException.validation(Map.of("caseIds", "crossProduct"));
    }
    resultRepository.insertAll(testRunId, distinct, command.assignee());
    run.markUpdatedBy(actor.account());
    repository.update(run).orElseThrow(() -> ApiException.lockConflict("数据已被他人修改，请刷新后重试。"));
    return testRunId;
  }

  @Transactional
  public long unlinkCases(SessionPrincipal actor, long testRunId, List<Long> caseIds) {
    TestRun run = require(actor, testRunId);
    resultRepository.delete(testRunId, caseIds == null ? List.of() : caseIds);
    run.markUpdatedBy(actor.account());
    repository.update(run).orElseThrow(() -> ApiException.lockConflict("数据已被他人修改，请刷新后重试。"));
    return testRunId;
  }

  /** 指派执行人（行须已存在 → 40401）。 */
  @Transactional
  public ResultView assignCase(SessionPrincipal actor, long testRunId, long caseId, AssignRunCaseRequest command) {
    require(actor, testRunId);
    if (command.assignee() == null || command.assignee().isBlank()) {
      throw ApiException.validation(Map.of("assignee", "required"));
    }
    validateAccounts(List.of(command.assignee()));
    Result result = resultRepository.find(testRunId, caseId)
        .orElseThrow(() -> ApiException.notFound("执行清单行"));
    result.assignTo(command.assignee());
    resultRepository.update(result);
    TestCase testCase = caseRepository.findActiveByIds(List.of(caseId)).stream().findFirst().orElse(null);
    return ResultView.of(result, testCase == null ? null : testCase.title(),
        testCase == null ? null : testCase.priority());
  }

  TestRunView fire(SessionPrincipal actor, long testRunId, String action, String comment,
      java.util.function.Consumer<TestRun> beforeFire) {
    TestRun run = require(actor, testRunId);
    beforeFire.accept(run);
    engine.fire(new TestRunTarget(run, actor.account(), null), action, comment);
    run.markUpdatedBy(actor.account());
    return TestRunView.of(save(run));
  }

  /** record-result 专用：target 携带结果值（yml 守卫 result-required 与动态流 detail 读取）。 */
  void fireResult(SessionPrincipal actor, TestRun run, String comment, String resultValue) {
    engine.fire(new TestRunTarget(run, actor.account(), resultValue), "record-result", comment);
    run.markUpdatedBy(actor.account());
    save(run);
  }

  TestRun save(TestRun run) {
    return repository.update(run).orElseThrow(() -> ApiException.lockConflict("数据已被他人修改，请刷新后重试。"));
  }

  private void validate(String name, Integer priority, String type, LocalDate beginDate, LocalDate endDate,
      String owner, List<String> members, List<String> notifyAccounts) {
    Map<String, String> errors = new java.util.LinkedHashMap<>();
    if (name != null) {
      String trimmed = name.trim();
      if (trimmed.isEmpty()) {
        errors.put("name", "required");
      } else if (trimmed.length() > 90) {
        errors.put("name", "maxLength");
      }
    }
    if (priority != null && (priority < 1 || priority > 4)) {
      errors.put("priority", "invalidRange");
    }
    if (type != null && !TYPES.contains(type)) {
      errors.put("type", "invalid");
    }
    if (beginDate != null && endDate != null && endDate.isBefore(beginDate)) {
      errors.put("endDate", "beforeBegin");
    }
    if (!errors.isEmpty()) {
      throw ApiException.validation(errors);
    }
    List<String> referenced = new java.util.ArrayList<>();
    referenced.add(owner);
    if (members != null) {
      referenced.addAll(members);
    }
    if (notifyAccounts != null) {
      referenced.addAll(notifyAccounts);
    }
    List<String> missing = accountApi.missingAccounts(referenced.stream().filter(java.util.Objects::nonNull).toList());
    if (!missing.isEmpty()) {
      if (owner != null && missing.contains(owner)) {
        throw ApiException.validation(Map.of("owner", "notFound"));
      }
      throw ApiException.validation(Map.of(members != null && members.stream().anyMatch(missing::contains)
          ? "members" : "notifyAccounts", "notFound"));
    }
  }

  private void validateAccounts(List<String> accounts) {
    if (!accountApi.missingAccounts(accounts).isEmpty()) {
      throw ApiException.validation(Map.of("assignee", "notFound"));
    }
  }

  private static String commentOf(CommentRequest command) {
    return command == null ? null : command.comment();
  }

  /** workflow 作用对象适配；resultValue 为 record-result 的请求体字段（fire 前落，守卫与 detail 读取）。 */
  record TestRunTarget(TestRun run, String actor, String resultValue) implements WorkflowTarget {

    @Override
    public String objectType() {
      return "testRun";
    }

    @Override
    public long objectId() {
      return run.id();
    }

    @Override
    public String status() {
      return run.status();
    }

    @Override
    public void applyStatus(String status) {
      run.applyStatus(status);
    }

    @Override
    public Object field(String name) {
      return switch (name) {
        case "title" -> run.name();
        case "createdBy" -> run.createdBy();
        case "owner" -> run.owner();
        case "members" -> run.members();
        case "notifyAccounts" -> run.notifyAccounts();
        case "realFinishedAt" -> run.realFinishedAt();
        case "resultValue" -> resultValue;
        default -> null;
      };
    }

    @Override
    public void setField(String name, Object value) {
      run.setField(name, value);
    }
  }
}
