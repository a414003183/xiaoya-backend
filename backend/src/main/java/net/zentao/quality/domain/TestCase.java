package net.zentao.quality.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 用例聚合根（quality 卡 §3.2；纯 Java，A1）：含嵌套 Step 值对象，version 恒 1。 */
public class TestCase {

  /** 标记态三态（03 §1 唯一例外：PATCH status 直改仅限此三态互转）。 */
  public static final Set<String> MARKER_STATUSES = Set.of("normal", "blocked", "investigate");
  public static final Set<String> STATUSES = Set.of("wait", "normal", "blocked", "investigate");
  public static final int STEPS_MAX = 100;

  /** 用例步骤值对象（case_step 行）。 */
  public record Step(int sort, String description, String expects) {}

  private final long id;
  private long productId;
  private long branchId;
  private long libraryId;
  private long categoryId;
  private Long storyId;
  private String title;
  private String precondition;
  private String keywords;
  private int priority;
  private String type;
  private List<String> stage;
  private String status;
  private List<Step> steps;
  private Long fromBugId;
  private String lastRunResult;
  private String lastRunner;
  private Instant lastRunAt;
  private List<String> reviewers;
  private Instant reviewedAt;
  private int version;
  private Map<String, Object> customFields;
  private String createdBy;
  private Instant createdAt;
  private String updatedBy;
  private Instant updatedAt;
  private int lockVersion;

  public TestCase(long id, long productId, long branchId, long libraryId, long categoryId, Long storyId,
      String title, String precondition, String keywords, int priority, String type, List<String> stage,
      String status, List<Step> steps, Long fromBugId, String lastRunResult, String lastRunner,
      Instant lastRunAt, List<String> reviewers, Instant reviewedAt, int version,
      Map<String, Object> customFields, String createdBy, Instant createdAt, String updatedBy,
      Instant updatedAt, int lockVersion) {
    this.id = id;
    this.productId = productId;
    this.branchId = branchId;
    this.libraryId = libraryId;
    this.categoryId = categoryId;
    this.storyId = storyId;
    this.title = title;
    this.precondition = precondition;
    this.keywords = keywords;
    this.priority = priority;
    this.type = type;
    this.stage = stage == null ? List.of() : List.copyOf(stage);
    this.status = status;
    this.steps = steps == null ? List.of() : List.copyOf(steps);
    this.fromBugId = fromBugId;
    this.lastRunResult = lastRunResult;
    this.lastRunner = lastRunner;
    this.lastRunAt = lastRunAt;
    this.reviewers = reviewers == null ? List.of() : List.copyOf(reviewers);
    this.reviewedAt = reviewedAt;
    this.version = version;
    this.customFields = customFields == null ? Map.of() : Map.copyOf(customFields);
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.updatedBy = updatedBy;
    this.updatedAt = updatedAt;
    this.lockVersion = lockVersion;
  }

  /** PATCH 白名单（quality 卡 §5）；null 不改；steps 整体替换（旧行全删）。 */
  public void update(String title, String precondition, String keywords, Integer priority, String type,
      List<String> stage, Long categoryId, Long storyId, String status, List<Step> steps) {
    if (title != null) {
      this.title = title;
    }
    if (precondition != null) {
      this.precondition = precondition;
    }
    if (keywords != null) {
      this.keywords = keywords;
    }
    if (priority != null) {
      this.priority = priority;
    }
    if (type != null) {
      this.type = type;
    }
    if (stage != null) {
      this.stage = List.copyOf(stage);
    }
    if (categoryId != null) {
      this.categoryId = categoryId;
    }
    if (storyId != null) {
      this.storyId = storyId == 0 ? null : storyId;
    }
    if (status != null) {
      requireMarkerTransition(status);
      this.status = status;
    }
    if (steps != null) {
      this.steps = List.copyOf(steps);
    }
  }

  /** 标记态校验（quality 卡 §4.2/§8）：仅 normal|blocked|investigate 互转，wait 进出只经 review。 */
  public void requireMarkerTransition(String target) {
    if (!MARKER_STATUSES.contains(status) || !MARKER_STATUSES.contains(target)) {
      throw new IllegalArgumentException("status 仅允许 normal|blocked|investigate 互转：" + target);
    }
  }

  /** review 请求体（fire 前落对象，供 when 分支与动态流 detail 读取）。 */
  public void reviewResult(String result) {
    this.reviewResult = result;
  }

  private String reviewResult;

  public String reviewResult() {
    return reviewResult;
  }

  /** review pass：reviewers 追加当前人（去重）、reviewedAt 落（§4.2）。 */
  public void reviewedBy(String actor) {
    if (!reviewers.contains(actor)) {
      List<String> merged = new ArrayList<>(reviewers);
      merged.add(actor);
      this.reviewers = List.copyOf(merged);
    }
    this.reviewedAt = Instant.now();
  }

  /** record-result 副作用同步（§3.5/§4.3）：lastRun 三字段。 */
  public void markLastRun(String result, String runner, Instant runAt) {
    this.lastRunResult = result;
    this.lastRunner = runner;
    this.lastRunAt = runAt;
  }

  public void applyStatus(String status) {
    this.status = status;
  }

  public void markUpdatedBy(String actor) {
    this.updatedBy = actor;
    this.updatedAt = Instant.now();
  }

  public long id() {
    return id;
  }

  public long productId() {
    return productId;
  }

  public long branchId() {
    return branchId;
  }

  public long libraryId() {
    return libraryId;
  }

  public long categoryId() {
    return categoryId;
  }

  public Long storyId() {
    return storyId;
  }

  public String title() {
    return title;
  }

  public String precondition() {
    return precondition;
  }

  public String keywords() {
    return keywords;
  }

  public int priority() {
    return priority;
  }

  public String type() {
    return type;
  }

  public List<String> stage() {
    return stage;
  }

  public String status() {
    return status;
  }

  public List<Step> steps() {
    return steps;
  }

  public Long fromBugId() {
    return fromBugId;
  }

  public String lastRunResult() {
    return lastRunResult;
  }

  public String lastRunner() {
    return lastRunner;
  }

  public Instant lastRunAt() {
    return lastRunAt;
  }

  public List<String> reviewers() {
    return reviewers;
  }

  public Instant reviewedAt() {
    return reviewedAt;
  }

  public int version() {
    return version;
  }

  public Map<String, Object> customFields() {
    return customFields;
  }

  public String createdBy() {
    return createdBy;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public String updatedBy() {
    return updatedBy;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public int lockVersion() {
    return lockVersion;
  }
}
