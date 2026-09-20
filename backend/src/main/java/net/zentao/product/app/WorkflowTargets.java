package net.zentao.product.app;

import net.zentao.platform.workflow.WorkflowTarget;
import net.zentao.product.domain.Branch;
import net.zentao.product.domain.Plan;
import net.zentao.product.domain.Product;
import net.zentao.product.domain.Release;

/**
 * 产品域状态机作用对象适配（platform 卡 §4.3）：域聚合不实现 platform 接口，
 * 由 app 层在 fire 前套上操作人上下文——聚合因此只保留业务状态，不携带工作流上下文。
 */
final class WorkflowTargets {

  private WorkflowTargets() {}

  record ProductTarget(Product product, String actor) implements WorkflowTarget {
    @Override
    public String objectType() {
      return "product";
    }

    @Override
    public long objectId() {
      return product.id();
    }

    @Override
    public String status() {
      return product.status();
    }

    @Override
    public void applyStatus(String status) {
      product.applyStatus(status);
    }

    @Override
    public Object field(String name) {
      return switch (name) {
        case "title" -> product.name();
        case "createdBy" -> product.createdBy();
        case "notifyAccounts" -> product.whitelist();
        default -> null;
      };
    }

    @Override
    public void setField(String name, Object value) {
      product.setField(name, value);
    }
  }

  record BranchTarget(Branch branch, String actor) implements WorkflowTarget {
    @Override
    public String objectType() {
      return "branch";
    }

    @Override
    public long objectId() {
      return branch.id();
    }

    @Override
    public String status() {
      return branch.status();
    }

    @Override
    public void applyStatus(String status) {
      branch.applyStatus(status);
    }

    @Override
    public Object field(String name) {
      return switch (name) {
        case "title" -> branch.name();
        case "createdBy" -> branch.createdBy();
        case "isDefault" -> branch.isDefault();
        default -> null;
      };
    }

    @Override
    public void setField(String name, Object value) {
      branch.setField(name, value);
    }
  }

  record PlanTarget(Plan plan, String actor) implements WorkflowTarget {
    @Override
    public String objectType() {
      return "plan";
    }

    @Override
    public long objectId() {
      return plan.id();
    }

    @Override
    public String status() {
      return plan.status();
    }

    @Override
    public void applyStatus(String status) {
      plan.applyStatus(status);
    }

    @Override
    public Object field(String name) {
      return switch (name) {
        case "title" -> plan.title();
        case "createdBy" -> plan.createdBy();
        case "closedReason" -> plan.closedReason();
        case "objectType" -> plan.linkObjectType();
        case "ids" -> plan.linkIds();
        default -> null;
      };
    }

    @Override
    public void setField(String name, Object value) {
      plan.setField(name, value);
    }
  }

  record ReleaseTarget(Release release, String actor) implements WorkflowTarget {
    @Override
    public String objectType() {
      return "release";
    }

    @Override
    public long objectId() {
      return release.id();
    }

    @Override
    public String status() {
      return release.status();
    }

    @Override
    public void applyStatus(String status) {
      release.applyStatus(status);
    }

    @Override
    public Object field(String name) {
      return switch (name) {
        case "title" -> release.name();
        case "createdBy" -> release.createdBy();
        case "notifyAccounts" -> release.notifyAccounts();
        case "objectType" -> release.linkObjectType();
        case "ids" -> release.linkIds();
        default -> null;
      };
    }

    @Override
    public void setField(String name, Object value) {
      release.setField(name, value);
    }
  }
}
