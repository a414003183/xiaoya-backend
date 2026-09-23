package net.zentao.platform.file;

import java.util.List;
import net.zentao.platform.activity.ObjectVisibilityRegistry;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
import net.zentao.platform.rbac.PrivilegeChecker;
import net.zentao.platform.session.SessionPrincipal;
import org.springframework.stereotype.Component;

/**
 * 文件列表/下载/删除的数据权限与查询（platform 卡 §7.2：绑定对象的文件「对象可见即可」，
 * 未绑定对象的文件仅上传人/超管可见）。
 *
 * <p>对象可见性走 {@link ObjectVisibilityRegistry}——各域注册自己的谓词，本类只按 objectType 转发；
 * 未注册的类型按注册表默认（可见），注册面见各域 Registrar（story/task/bug/testCase/doc/account）。
 */
@Component
public class FileQueryService {

  private final FileRepository repository;
  private final PrivilegeChecker checker;
  private final ObjectVisibilityRegistry visibility;

  public FileQueryService(FileRepository repository, PrivilegeChecker checker,
      ObjectVisibilityRegistry visibility) {
    this.repository = repository;
    this.checker = checker;
    this.visibility = visibility;
  }

  /** FileList（contract：items + total）。 */
  public record FileList(java.util.List<FileView> items, long total) {}

  public FileList page(SessionPrincipal principal, String objectType, long objectId, int page, int limit) {
    requireObjectVisible(principal, objectType, objectId);
    List<FileView> items =
        repository.page(objectType, objectId, (page - 1) * limit, limit).stream().map(FileViews::toView).toList();
    return new FileList(items, repository.countByObject(objectType, objectId));
  }

  /** 下载/预览：绑定对象可见 或 本人上传/超管；未绑定文件的可见面只有上传人/超管。 */
  public boolean canDownload(SessionPrincipal principal, FilePO po) {
    if (isUploaderOrSuper(principal, po)) {
      return true;
    }
    String objectType = po.getObjectType();
    if (objectType == null || objectType.isBlank()) {
      return false;
    }
    return visibility.isVisible(principal, objectType, po.getObjectId() == null ? 0L : po.getObjectId());
  }

  /** 列表的对象级数据权限前置：绑定对象不可见 → 40302（列表按对象取，一次判定覆盖整页）。 */
  public void requireObjectVisible(SessionPrincipal principal, String objectType, long objectId) {
    if (!visibility.isVisible(principal, objectType, objectId)) {
      throw ApiException.keyed(ErrorCode.DATA_FORBIDDEN, "file.guard.objectInvisible");
    }
  }

  public boolean canDelete(SessionPrincipal principal, FilePO po) {
    return isUploaderOrSuper(principal, po);
  }

  private boolean isUploaderOrSuper(SessionPrincipal principal, FilePO po) {
    return principal.account().equals(po.getCreatedBy()) || checker.isSuperAdmin(principal.accountId());
  }

  public void requireObjectFilters(String objectType, String objectId) {
    if (objectType == null || objectType.isBlank() || objectId == null || objectId.isBlank()) {
      throw ApiException.keyed(ErrorCode.BAD_REQUEST, "file.filter.objectRequired");
    }
  }
}
