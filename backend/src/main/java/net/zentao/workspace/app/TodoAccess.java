package net.zentao.workspace.app;

import net.zentao.platform.error.ApiException;
import net.zentao.workspace.domain.Todo;

/**
 * 待办数据权限判定（workspace 卡 §7）：个人域对象，读/写均按当事人收敛，超管豁免。
 * 功能权限码（todo-view/todo-edit…）由 {@code @RequirePrivilege} 单独把关（40301），此处只判数据权限（40302）。
 */
final class TodoAccess {

  private TodoAccess() {}

  /** 详情读：isPrivate=false 持码可读；=true 仅创建人/负责人可读。 */
  static void requireReadable(String account, Todo todo, boolean superAdmin) {
    if (superAdmin) {
      return;
    }
    if (todo.isPrivate() && !isParticipant(account, todo)) {
      throw ApiException.dataForbidden("私有待办仅创建人与负责人可见。");
    }
  }

  /** 写动作（PATCH/状态机/指派）：仅创建人/负责人可发。 */
  static void requireWritable(String account, Todo todo, boolean superAdmin) {
    if (superAdmin) {
      return;
    }
    if (!isParticipant(account, todo)) {
      throw ApiException.dataForbidden("仅待办的创建人或负责人可操作。");
    }
  }

  private static boolean isParticipant(String account, Todo todo) {
    return account.equals(todo.createdBy()) || account.equals(todo.assignee());
  }
}
