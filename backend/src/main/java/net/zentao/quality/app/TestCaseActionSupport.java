package net.zentao.quality.app;

import java.util.function.Consumer;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.workflow.WorkflowEngine;
import net.zentao.platform.workflow.WorkflowTarget;
import net.zentao.product.api.ProductApi;
import net.zentao.quality.api.TestCaseView;
import net.zentao.quality.domain.TestCase;
import net.zentao.quality.domain.TestCaseRepository;

/**
 * 用例动作公共骨架：库用例（libraryId≠0）全员可读（§7 Library 面），产品用例走产品 ACL。
 * reviewResult 为请求体驱动的临时字段（fire 前落对象，供 when 分支与动态流 detail 读取）。
 */
final class TestCaseActionSupport {

  private TestCaseActionSupport() {}

  static TestCase require(SessionPrincipal actor, TestCaseRepository repository, ProductApi productApi,
      long caseId) {
    TestCase testCase = repository.findActiveById(caseId).orElseThrow(() -> ApiException.notFound("用例"));
    if (testCase.libraryId() == 0 && !productApi.canAccess(actor, testCase.productId())) {
      throw ApiException.dataForbidden("无权访问该用例。");
    }
    return testCase;
  }

  static TestCaseView fire(SessionPrincipal actor, TestCaseRepository repository, ProductApi productApi,
      WorkflowEngine engine, long caseId, String action, String comment, Consumer<TestCase> beforeFire) {
    TestCase testCase = require(actor, repository, productApi, caseId);
    beforeFire.accept(testCase);
    engine.fire(new TestCaseTarget(testCase, actor.account()), action, comment);
    testCase.markUpdatedBy(actor.account());
    return TestCaseView.of(save(repository, testCase));
  }

  static TestCase save(TestCaseRepository repository, TestCase testCase) {
    return repository.update(testCase).orElseThrow(() -> ApiException.lockConflict("数据已被他人修改，请刷新后重试。"));
  }

  record TestCaseTarget(TestCase testCase, String actor) implements WorkflowTarget {

    @Override
    public String objectType() {
      return "testCase";
    }

    @Override
    public long objectId() {
      return testCase.id();
    }

    @Override
    public String status() {
      return testCase.status();
    }

    @Override
    public void applyStatus(String status) {
      testCase.applyStatus(status);
    }

    @Override
    public Object field(String name) {
      return switch (name) {
        case "title" -> testCase.title();
        case "createdBy" -> testCase.createdBy();
        case "reviewers" -> testCase.reviewers();
        case "result" -> testCase.reviewResult();
        default -> null;
      };
    }

    @Override
    public void setField(String name, Object value) {
      throw new IllegalArgumentException("用例未声明的 fieldSet 字段：" + name);
    }
  }
}
