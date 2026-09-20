package net.zentao.doc.domain;

import java.util.List;
import java.util.Set;

/**
 * doc 双层数据权限判定（doc 卡 §7，纯函数）：
 * 先过<b>库门禁</b>（{@link #isSpaceVisible}），再判<b>文档 ACL</b>（{@link #isDocReadable}/{@link #isDocEditable}）；
 * 功能权限码（doc-view/doc-edit…）与数据权限独立（40301 / 40302）。
 *
 * <p>归属对象可见性由调用方跨域取好（ProductApi/ProjectApi/ExecutionApi 的 scope），本类不依赖任何域。
 */
public final class DocAclPolicy {

  private DocAclPolicy() {}

  /** 归属对象可见集（app 层从各域 api 的 VisibleScope 适配；{@code visibleToAll} = 超管不受限）。 */
  public record ObjectVisibility(boolean visibleToAll, Set<Long> ids) {

    public static final ObjectVisibility NONE = new ObjectVisibility(false, Set.of());

    public boolean contains(long id) {
      return visibleToAll || ids.contains(id);
    }
  }

  /**
   * 库门禁：open 全员；private = createdBy + 白名单（账号/组命中）+ 超管；
   * default = 继承归属对象可见性；type=mine 仅 createdBy（<b>超管也不行</b>）。
   */
  public static boolean isSpaceVisible(DocSpace space, String account, boolean superAdmin,
      List<Long> accountGroups, ObjectVisibility products, ObjectVisibility projects, ObjectVisibility executions) {
    if ("mine".equals(space.type())) {
      return account != null && account.equals(space.createdBy());
    }
    if (superAdmin) {
      return true;
    }
    if (account == null) {
      return false;
    }
    return switch (space.acl()) {
      case "open" -> true;
      case "private" -> account.equals(space.createdBy()) || space.whitelist().matches(account, accountGroups);
      case "default" -> switch (space.type()) {
        case "product" -> products.contains(space.productId());
        case "project" -> projects.contains(space.projectId());
        case "execution" -> executions.contains(space.executionId());
        default -> false;
      };
      default -> false;
    };
  }

  /** 文档可读：open 全员可读；private = createdBy/超管/editors/readers 命中；其余不可见（按不存在处理）。 */
  public static boolean isDocReadable(Doc doc, String account, boolean superAdmin, List<Long> accountGroups) {
    if (superAdmin) {
      return true;
    }
    if (account == null) {
      return false;
    }
    return "open".equals(doc.acl())
        || account.equals(doc.createdBy())
        || doc.editors().matches(account, accountGroups)
        || doc.readers().matches(account, accountGroups);
  }

  /** 文档可编辑：open 由功能权限码决定（本判定放行）；private = createdBy/超管/editors，readers 只读。 */
  public static boolean isDocEditable(Doc doc, String account, boolean superAdmin, List<Long> accountGroups) {
    if (superAdmin) {
      return true;
    }
    if (account == null) {
      return false;
    }
    return "open".equals(doc.acl())
        || account.equals(doc.createdBy())
        || doc.editors().matches(account, accountGroups);
  }

  /** 草稿工作副本（v0）仅可编辑者可读（doc 卡 §4 末条）。 */
  public static boolean canReadDraft(Doc doc, String account, boolean superAdmin, List<Long> accountGroups) {
    return isDocEditable(doc, account, superAdmin, accountGroups);
  }
}
