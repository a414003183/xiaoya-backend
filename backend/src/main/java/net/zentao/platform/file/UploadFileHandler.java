package net.zentao.platform.file;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import org.springframework.stereotype.Component;

/**
 * 上传守卫（platform 卡 §3.5/§8）。
 *
 * <p>类型策略（T60 / SEC-08，两层）：
 * <ol>
 *   <li>**拒绝清单**：主动 Web 内容（`html/svg/js` 家族）与服务端脚本/可执行 → 42201 `file=extensionBlocked`；
 *   <li>**内容魔数核对**：声明的扩展名属图片类时，内容必须是对应魔数 → 42201 `file=contentMismatch`
 *       （改名的 HTML/SVG 挡在写盘之前；`/raw` 的内联面因此只可能服务真图片）。
 * </ol>
 *
 * <p>为什么是「拒绝清单 + 魔数核对」而不是全量白名单：附件是**任意业务文件**（补丁、日志、设计稿、压缩包……），
 * 枚举白名单必然漏掉真实格式，误拒比漏拒更伤业务；服务端能机器判定的那半是「声明是图片就必须是图片」。
 * 真正的兜底在**投递方式**（不是收件口）：下载恒 `attachment` + `application/octet-stream`，内联预览只服务
 * {@code FileController.INLINE_IMAGE_TYPES} 六种图片并带 `nosniff`——所以「能传」≠「能在本站点里执行」。
 * （旧注释声称的「MIME 以服务端探测为准」是**并不存在**的机制，T60 一并订正为上述事实。）
 *
 * <p>落盘路径 {yyyyMM}/{uuid}.{ext}；体积上限与容器 `spring.servlet.multipart.max-file-size` 同值。
 */
@Component
public class UploadFileHandler {

  static final long MAX_SIZE_BYTES = 50L * 1024 * 1024;
  /** 图片类扩展名（与 {@code FileController.INLINE_IMAGE_TYPES} 必须**同集**，由用例看护）。 */
  public static final Map<String, Predicate<byte[]>> IMAGE_MAGIC = Map.of(
      "jpg", head -> startsWith(head, 0xFF, 0xD8, 0xFF),
      "jpeg", head -> startsWith(head, 0xFF, 0xD8, 0xFF),
      "png", head -> startsWith(head, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A),
      "gif", head -> startsWith(head, 'G', 'I', 'F', '8'),
      // WEBP 的签名中间隔着 4 字节长度（RIFF????WEBP），不能只看前缀
      "webp", head -> startsWith(head, 'R', 'I', 'F', 'F') && matchesAt(head, 8, 'W', 'E', 'B', 'P'),
      "bmp", head -> startsWith(head, 'B', 'M'));

  /**
   * 拒收扩展名（小写、不带点）：可在浏览器域内执行/渲染的主动内容 + 服务端脚本与可执行文件。
   * 扩展名可以随便改，所以这只是第一层——第二层是上面按内容的图片核对。
   */
  private static final Set<String> DENIED_EXTENSIONS = Set.of(
      "php", "phtml", "jsp", "jspx", "asp", "aspx", "exe", "sh", "bat", "com", "dll",
      "htm", "html", "xhtml", "shtml", "svg", "svgz", "js", "mjs", "cjs", "hta");

  /** 参与内容核对的字节数上限（签名都在头 32 字节内；有界读，不缓冲整份文件）。 */
  private static final int SNIFF_BYTES = 512;

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
    if (DENIED_EXTENSIONS.contains(extension)) {
      throw ApiException.validation(Map.of("file", "extensionBlocked"));
    }
    if (size <= 0) {
      throw ApiException.validation(Map.of("file", "required"));
    }
    if (size > MAX_SIZE_BYTES) {
      throw ApiException.validation(Map.of("file", "tooLarge"));
    }
    byte[] head = readHead(content);
    requireImageContent(extension, head);
    String relativePath = MONTH.format(Instant.now()) + "/" + UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);
    // 嗅探消耗掉的头部与余下流拼回同一份内容：存储看到的是完整文件（不把整份读进堆）
    storage.store(relativePath, new SequenceInputStream(new ByteArrayInputStream(head), content), size);

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

  /** 头部有界读（不足即全量）：读不出来按写盘失败同口径报错。 */
  private static byte[] readHead(InputStream content) {
    try {
      return content.readNBytes(SNIFF_BYTES);
    } catch (IOException e) {
      throw new IllegalStateException("上传内容读取失败", e);
    }
  }

  /** 图片类扩展名的内容核对：声明是图片就必须是图片。非图片类不在此列（任意业务文件照收）。 */
  private static void requireImageContent(String extension, byte[] head) {
    Predicate<byte[]> magic = IMAGE_MAGIC.get(extension);
    if (magic != null && !magic.test(head)) {
      throw ApiException.validation(Map.of("file", "contentMismatch"));
    }
  }

  private static boolean startsWith(byte[] content, int... prefix) {
    if (content.length < prefix.length) {
      return false;
    }
    for (int i = 0; i < prefix.length; i++) {
      if ((content[i] & 0xFF) != (prefix[i] & 0xFF)) {
        return false;
      }
    }
    return true;
  }

  private static boolean matchesAt(byte[] content, int offset, int... expected) {
    if (content.length < offset + expected.length) {
      return false;
    }
    for (int i = 0; i < expected.length; i++) {
      if ((content[offset + i] & 0xFF) != (expected[i] & 0xFF)) {
        return false;
      }
    }
    return true;
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
}
