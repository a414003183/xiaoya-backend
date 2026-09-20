package net.zentao.doc.app;

import net.zentao.doc.domain.Doc;
import net.zentao.doc.domain.DocVersion;
import net.zentao.platform.workflow.WorkflowTarget;

/**
 * 文档状态机作用对象适配（platform 卡 §4.3）：域聚合不实现 platform 接口，
 * 由 app 层在 fire 前套上操作人上下文与工作副本（守卫/动态流 detail 从 v0 取值）。
 */
record DocTarget(Doc doc, DocVersion working, boolean hasDraftChanges, String actor) implements WorkflowTarget {

  @Override
  public String objectType() {
    return "doc";
  }

  @Override
  public long objectId() {
    return doc.id();
  }

  @Override
  public String status() {
    return doc.status();
  }

  @Override
  public void applyStatus(String status) {
    doc.applyStatus(status);
  }

  @Override
  public Object field(String name) {
    return switch (name) {
      case "title" -> working == null ? doc.title() : working.title();
      case "content" -> working == null ? null : working.content();
      case "files" -> working == null ? null : working.files();
      case "hasDraftChanges" -> hasDraftChanges;
      case "notifyAccounts" -> doc.notifyAccounts();
      case "createdBy" -> doc.createdBy();
      default -> null;
    };
  }

  @Override
  public void setField(String name, Object value) {
    throw new IllegalArgumentException("doc 状态机未声明的 fieldSet 字段：" + name);
  }
}
