package net.zentao.platform.file;

import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import org.springframework.stereotype.Component;

/**
 * 上传守卫（platform 卡 §3.5/§8）：扩展名黑名单 / 超 50MB → 42201；
 * MIME 以服务端探测为准；路径 {yyyyMM}/{uuid}.{ext}。
 */
@Component
public class UploadFileHandler {

  static final long MAX_SIZE_BYTES = 50L * 1024 * 1024;
  private static final Set<String> BLACKLIST = Set.of("php", "phtml", "jsp", "asp", "aspx", "exe", "sh", "bat", "com", "dll");
  private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM").withZone(ZoneId.systemDefault());

  private final FileStorage storage;
  private final FileRepository repository;

  public UploadFileHandler(FileStorage storage, FileRepository repository) {
    this.storage = storage;
    this.repository = repository;
  }

  public FileView upload(SessionPrincipal principal, String originalFilename, InputStream content, long size,
      String objectType, Long objectId) {
    String extension = extensionOf(originalFilename);
    if (BLACKLIST.contains(extension)) {
      throw ApiException.validation(Map.of("file", "extensionBlocked"));
    }
    if (size <= 0) {
      throw ApiException.validation(Map.of("file", "required"));
    }
    if (size > MAX_SIZE_BYTES) {
      throw ApiException.validation(Map.of("file", "tooLarge"));
    }
    String relativePath = MONTH.format(Instant.now()) + "/" + UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);
    storage.store(relativePath, content, size);

    FilePO po = new FilePO();
    po.setTitle(originalFilename);
    po.setPath(relativePath);
    po.setExtension(extension);
    po.setSize(size);
    po.setObjectType(objectType == null ? "" : objectType);
    po.setObjectId(objectId == null ? 0 : objectId);
    po.setDownloads(0L);
    po.setCreatedBy(principal.account());
    po.setCreatedAt(Instant.now());
    repository.insert(po);
    return FileViews.toView(po);
  }

  static String extensionOf(String filename) {
    if (filename == null) {
      return "";
    }
    int dot = filename.lastIndexOf('.');
    if (dot < 0 || dot == filename.length() - 1) {
      return "";
    }
    return filename.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
  }

  static List<String> blacklist() {
    return List.copyOf(BLACKLIST);
  }
}
