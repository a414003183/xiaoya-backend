package net.zentao.quality.app;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.zentao.platform.activity.ActivityRecorder;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.i18n.MessageResolver;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.product.api.ProductApi;
import net.zentao.quality.domain.TestCase;
import net.zentao.quality.domain.TestCaseRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 从用例库导入（quality 卡 §5/§8）：复制库用例为产品用例（productId=路径、libraryId=0），
 * 步骤一并复制；库用例本身不动、不出现在产品用例列表。
 */
@Component
public class ImportFromLibraryHandler {

  private final TestCaseRepository repository;
  private final ProductApi productApi;
  private final ActivityRecorder activityRecorder;


  private final MessageResolver messages;

  public ImportFromLibraryHandler(TestCaseRepository repository, ProductApi productApi,
      ActivityRecorder activityRecorder,
      MessageResolver messages) {
    this.repository = repository;
    this.productApi = productApi;
    this.activityRecorder = activityRecorder;
    this.messages = messages;
  }

  public record TestCaseImportRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long libraryId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Long> caseIds) {}

  public record TestCaseImportResult(long importedCount) {}

  @Transactional
  public TestCaseImportResult handle(SessionPrincipal actor, long productId, TestCaseImportRequest command) {
    productApi.requireVisible(actor, productId);
    if (command.caseIds() == null || command.caseIds().isEmpty()) {
      throw ApiException.validation(Map.of("caseIds", "required"));
    }
    List<Long> distinct = command.caseIds().stream().distinct().toList();
    List<TestCase> sources = repository.findActiveByIds(distinct);
    if (sources.size() != distinct.size()
        || sources.stream().anyMatch(source -> source.libraryId() != command.libraryId())) {
      throw ApiException.validation(Map.of("caseIds", "notInLibrary"));
    }
    Map<Long, List<TestCase.Step>> steps = repository.findSteps(distinct);
    long imported = 0;
    for (TestCase source : sources) {
      TestCase copy = repository.insert(new TestCase(
          0,
          productId,
          source.branchId(),
          0,
          source.categoryId(),
          source.storyId(),
          source.title(),
          source.precondition(),
          source.keywords(),
          source.priority(),
          source.type(),
          source.stage(),
          source.status().equals("wait") ? "wait" : "normal",
          steps.getOrDefault(source.id(), List.of()),
          null,
          null,
          null,
          null,
          source.reviewers(),
          null,
          1,
          null,
          actor.account(),
          Instant.now(),
          null,
          null,
          0));
      activityRecorder.record(actor.account(), "testCase", copy.id(), "created", null,
          messages.plain("activity.remark.caseLibraryImport", null));
      imported += 1;
    }
    return new TestCaseImportResult(imported);
  }
}
