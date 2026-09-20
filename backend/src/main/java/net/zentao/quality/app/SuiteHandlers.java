package net.zentao.quality.app;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.zentao.platform.activity.ActivityRecorder;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.product.api.ProductApi;
import net.zentao.quality.api.SuiteView;
import net.zentao.quality.domain.Suite;
import net.zentao.quality.domain.SuiteRepository;
import net.zentao.quality.domain.TestCaseRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 套件/用例库命令（quality 卡 §5：Suite 6 端点中 4 个写 + Library 2 个写）。
 * /suites 面写入排除 library 型；/libraries 面强制 type=library、productId=0；
 * link-cases 幂等（UNIQUE + 集合合并），关联用例须为同产品用例（库面须为库内用例）。
 */
@Component
public class SuiteHandlers {

  private static final Set<String> SUITE_TYPES = Set.of("public", "private");

  private final SuiteRepository repository;
  private final SuiteQueryService queryService;
  private final TestCaseRepository caseRepository;
  private final ProductApi productApi;
  private final ActivityRecorder activityRecorder;

  public SuiteHandlers(SuiteRepository repository, SuiteQueryService queryService,
      TestCaseRepository caseRepository, ProductApi productApi, ActivityRecorder activityRecorder) {
    this.repository = repository;
    this.queryService = queryService;
    this.caseRepository = caseRepository;
    this.productApi = productApi;
    this.activityRecorder = activityRecorder;
  }

  public record SuiteCreateRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name, String description,
      @Schema(allowableValues = {"private", "public"}) String type, Integer sort) {}

  public record SuiteUpdateRequest(String name, String description,
      @Schema(allowableValues = {"private", "public"}) String type, Integer sort,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer lockVersion) {}

  public record SuiteLinkCasesRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Long> caseIds) {}

  /** 库面创建（contract：LibraryCreateRequest = name/description）。 */
  public record LibraryCreateRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name, String description) {

    SuiteCreateRequest toSuite() {
      return new SuiteCreateRequest(name, description, null, null);
    }
  }

  /** 库面更新（contract：LibraryUpdateRequest = name/description + lockVersion）。 */
  public record LibraryUpdateRequest(String name, String description,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer lockVersion) {}

  @Transactional
  public SuiteView createSuite(SessionPrincipal actor, long productId, SuiteCreateRequest command) {
    productApi.requireVisible(actor, productId);
    String type = command.type() == null ? "public" : command.type();
    return insert(actor, productId, type, command.name(), command.description(), command.sort(), "suite");
  }

  @Transactional
  public SuiteView createLibrary(SessionPrincipal actor, LibraryCreateRequest command) {
    return insert(actor, 0, Suite.TYPE_LIBRARY, command.name(), command.description(), null, "library");
  }

  private SuiteView insert(SessionPrincipal actor, long productId, String type, String name,
      String description, Integer sort, String objectType) {
    validateName(name);
    if (!Suite.TYPE_LIBRARY.equals(type) && !SUITE_TYPES.contains(type)) {
      throw ApiException.validation(Map.of("type", "invalid"));
    }
    Suite suite = repository.insert(new Suite(
        0, productId, name.trim(), description, type, sort == null ? 0 : Math.max(0, sort), null,
        actor.account(), Instant.now(), null, null, 0));
    activityRecorder.record(actor.account(), objectType, suite.id(), "created", null, null);
    return SuiteView.detailOf(suite, 0);
  }

  @Transactional
  public SuiteView updateSuite(SessionPrincipal actor, long suiteId, SuiteUpdateRequest command) {
    Suite suite = queryService.requireSuite(actor, suiteId);
    return update(actor, suite, command, "suite");
  }

  @Transactional
  public SuiteView updateLibrary(SessionPrincipal actor, long libraryId, LibraryUpdateRequest command) {
    Suite suite = queryService.requireLibrary(libraryId);
    // 库面仅 name/description 可改（quality 卡 §5）
    return update(actor, suite, new SuiteUpdateRequest(command.name(), command.description(), null, null,
        command.lockVersion()), "library");
  }

  /** 软删套件（A-07）：suite_case 关联行保留自然失效；删后详情 40401。 */
  @Transactional
  public void deleteSuite(SessionPrincipal actor, long suiteId) {
    Suite suite = queryService.requireSuite(actor, suiteId);
    repository.softDelete(suite.id(), actor.account(), Instant.now());
    activityRecorder.record(actor.account(), "suite", suite.id(), "deleted", null, null);
  }

  /** 软删用例库（A-07）：库内存在未删用例（productId=0 且 libraryId=本库）→ 42203。 */
  @Transactional
  public void deleteLibrary(SessionPrincipal actor, long libraryId) {
    Suite library = queryService.requireLibrary(libraryId);
    if (caseRepository.countActiveInLibrary(library.id()) > 0) {
      throw ApiException.guardNotSatisfied("用例库内存在未删用例，无法删除。");
    }
    repository.softDelete(library.id(), actor.account(), Instant.now());
    activityRecorder.record(actor.account(), "library", library.id(), "deleted", null, null);
  }

  private SuiteView update(SessionPrincipal actor, Suite suite, SuiteUpdateRequest command, String objectType) {
    if (command.lockVersion() == null || command.lockVersion() != suite.lockVersion()) {
      throw ApiException.lockConflict("数据已被他人修改，请刷新后重试。");
    }
    validateName(command.name());
    if (command.type() != null && !SUITE_TYPES.contains(command.type()) && !suite.isLibrary()) {
      throw ApiException.validation(Map.of("type", "invalid"));
    }
    suite.update(command.name(), command.description(), command.type(), command.sort());
    suite.markUpdatedBy(actor.account());
    Suite saved = repository.update(suite)
        .orElseThrow(() -> ApiException.lockConflict("数据已被他人修改，请刷新后重试。"));
    activityRecorder.record(actor.account(), objectType, saved.id(), "edited", null, null);
    return SuiteView.detailOf(saved, repository.countCases(List.of(saved.id())).getOrDefault(saved.id(), 0L));
  }

  @Transactional
  public SuiteView linkCases(SessionPrincipal actor, long suiteId, SuiteLinkCasesRequest command) {
    Suite suite = queryService.requireSuite(actor, suiteId);
    List<Long> ids = validatedCaseIds(suite, command.caseIds());
    suite.linkCases(ids);
    return persistRelation(actor, suite, "linkedCases");
  }

  @Transactional
  public SuiteView unlinkCases(SessionPrincipal actor, long suiteId, SuiteLinkCasesRequest command) {
    Suite suite = queryService.requireSuite(actor, suiteId);
    List<Long> ids = command.caseIds() == null ? List.of() : command.caseIds();
    suite.unlinkCases(ids);
    return persistRelation(actor, suite, "unlinkedCases");
  }

  private SuiteView persistRelation(SessionPrincipal actor, Suite suite, String action) {
    repository.replaceCases(suite.id(), suite.caseIds());
    suite.markUpdatedBy(actor.account());
    repository.update(suite).orElseThrow(() -> ApiException.lockConflict("数据已被他人修改，请刷新后重试。"));
    activityRecorder.record(actor.account(), suite.isLibrary() ? "library" : "suite", suite.id(), action,
        null, null);
    return SuiteView.detailOf(suite, suite.caseIds().size());
  }

  private List<Long> validatedCaseIds(Suite suite, List<Long> caseIds) {
    if (caseIds == null || caseIds.isEmpty()) {
      throw ApiException.validation(Map.of("caseIds", "required"));
    }
    List<Long> distinct = caseIds.stream().distinct().toList();
    long matched = caseRepository.findActiveByIds(distinct).stream()
        .filter(testCase -> suite.isLibrary()
            ? testCase.libraryId() == suite.id()
            : testCase.productId() == suite.productId() && testCase.libraryId() == 0)
        .count();
    if (matched != distinct.size()) {
      throw ApiException.validation(Map.of("caseIds", "crossProduct"));
    }
    return distinct;
  }

  private static void validateName(String name) {
    if (name == null || name.trim().isEmpty()) {
      throw ApiException.validation(Map.of("name", "required"));
    }
    if (name.trim().length() > 255) {
      throw ApiException.validation(Map.of("name", "maxLength"));
    }
  }
}
