package net.zentao.platform.file;

import java.util.List;
import java.util.Map;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.rbac.PrivilegeChecker;
import net.zentao.platform.session.SessionPrincipal;
import org.springframework.stereotype.Component;

/** 文件列表/下载/删除的数据权限与查询（platform 卡 §7.2：未绑定文件仅上传人/超管可见）。 */
@Component
public class FileQueryService {

  private final FileRepository repository;
  private final PrivilegeChecker checker;

  public FileQueryService(FileRepository repository, PrivilegeChecker checker) {
    this.repository = repository;
    this.checker = checker;
  }

  /** FileList（contract：items + total）。 */
  public record FileList(java.util.List<FileView> items, long total) {}

  public FileList page(SessionPrincipal principal, String objectType, long objectId, int page, int limit) {
    List<FileView> items =
        repository.page(objectType, objectId, (page - 1) * limit, limit).stream().map(FileViews::toView).toList();
    return new FileList(items, repository.countByObject(objectType, objectId));
  }

  public boolean canDownload(SessionPrincipal principal, FilePO po) {
    boolean unbound = po.getObjectType() == null || po.getObjectType().isBlank();
    return !unbound || isUploaderOrSuper(principal, po);
  }

  public boolean canDelete(SessionPrincipal principal, FilePO po) {
    return isUploaderOrSuper(principal, po);
  }

  private boolean isUploaderOrSuper(SessionPrincipal principal, FilePO po) {
    return principal.account().equals(po.getCreatedBy()) || checker.isSuperAdmin(principal.accountId());
  }

  /** 下载即计数 +1（platform 卡 §3.5）。 */
  public void incrementDownloads(FilePO po) {
    po.setDownloads((po.getDownloads() == null ? 0 : po.getDownloads()) + 1);
    repository.update(po);
  }

  public void requireObjectFilters(String objectType, String objectId) {
    if (objectType == null || objectType.isBlank() || objectId == null || objectId.isBlank()) {
      throw ApiException.badRequest("filters[objectType] 与 filters[objectId] 必填。");
    }
  }
}
