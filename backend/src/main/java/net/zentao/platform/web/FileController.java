package net.zentao.platform.web;

import io.swagger.v3.oas.annotations.Operation;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import net.zentao.platform.audit.Audit;
import net.zentao.platform.audit.AuditHasher;
import net.zentao.platform.audit.AuditRecorder;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
import net.zentao.platform.file.FilePO;
import net.zentao.platform.file.FileQueryService;
import net.zentao.platform.file.FileRepository;
import net.zentao.platform.file.FileStorage;
import net.zentao.platform.file.FileView;
import net.zentao.platform.file.UploadFileHandler;
import net.zentao.platform.rbac.RequirePrivilege;
import net.zentao.platform.ratelimit.RateLimits;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.session.SessionResolver;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 文件五端点（platform 卡 §5：上传/按对象列表/下载/软删；A-02：图片内联预览）。 */
@RestController
@RequestMapping("/api/v1")
public class FileController {

  /**
   * 图片内联白名单（A-02：扩展名小写比较；svg 等向量/可执行格式不内联）。
   * 键集必须与 {@code UploadFileHandler.IMAGE_MAGIC} **同集**（上传侧要对同一种图核魔数），由用例看护。
   */
  public static final java.util.Map<String, MediaType> INLINE_IMAGE_TYPES = java.util.Map.of(
      "jpg", MediaType.IMAGE_JPEG,
      "jpeg", MediaType.IMAGE_JPEG,
      "png", MediaType.IMAGE_PNG,
      "gif", MediaType.IMAGE_GIF,
      "webp", MediaType.parseMediaType("image/webp"),
      "bmp", MediaType.parseMediaType("image/bmp"));

  private final UploadFileHandler uploadHandler;
  private final FileQueryService queryService;
  private final FileRepository repository;
  private final FileStorage storage;
  private final SessionResolver resolver;
  private final RateLimits rateLimits;
  private final AuditRecorder auditRecorder;

  public FileController(UploadFileHandler uploadHandler, FileQueryService queryService,
      FileRepository repository, FileStorage storage, SessionResolver resolver, RateLimits rateLimits,
      AuditRecorder auditRecorder) {
    this.uploadHandler = uploadHandler;
    this.queryService = queryService;
    this.repository = repository;
    this.storage = storage;
    this.resolver = resolver;
    this.rateLimits = rateLimits;
    this.auditRecorder = auditRecorder;
  }

  @PostMapping("/files")
  @Operation(operationId = "uploadFile")
  @RequirePrivilege("file-upload")
  @Audit(action = "file-upload", objectType = "file")
  public DataEnvelope<FileView> upload(
      @RequestParam("file") MultipartFile file,
      @RequestParam(required = false) String objectType,
      @RequestParam(required = false) Long objectId,
      jakarta.servlet.http.HttpServletRequest request) throws Exception {
    SessionPrincipal principal = resolver.resolve(request);
    // T59 SEC-07：一次请求 = 写盘 + 落 file 表，按账号计窗（超限 42901）。
    // ponytail: 闸门在 multipart 接收**之后**（Servlet 前置闸门归 T61 的安全面），只拦得住后续处理与落库
    rateLimits.requireAllowed(RateLimits.Scope.upload, principal.account());
    try (InputStream content = file.getInputStream()) {
      return DataEnvelope.of(uploadHandler.upload(principal, file.getOriginalFilename(), content,
          file.getSize(), objectType, objectId));
    }
  }

  @GetMapping("/files")
  @Operation(operationId = "listFiles")
  public DataEnvelope<FileQueryService.FileList> list(
      @RequestParam(name = "filters[objectType]", required = false) String objectType,
      @RequestParam(name = "filters[objectId]", required = false) String objectId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "50") int limit,
      jakarta.servlet.http.HttpServletRequest request) {
    queryService.requireObjectFilters(objectType, objectId);
    var principal = resolver.resolve(request);
    return DataEnvelope.of(queryService.page(principal, objectType, objectIdOf(objectId), page, limit));
  }

  /** filters[objectId] 是字符串过滤位：非数字即 40001，别让 NumberFormatException 冒成 50001。 */
  private static long objectIdOf(String objectId) {
    try {
      return Long.parseLong(objectId.trim());
    } catch (NumberFormatException notNumeric) {
      throw ApiException.keyed(ErrorCode.BAD_REQUEST, "filters.param.integer", "filters[objectId]");
    }
  }

  @GetMapping("/files/{fileId}/download")
  @Operation(operationId = "downloadFile")
  public ResponseEntity<byte[]> download(@PathVariable long fileId, jakarta.servlet.http.HttpServletRequest request)
      throws Exception {
    var principal = resolver.resolve(request);
    FilePO po = repository.findVisibleById(fileId).orElseThrow(() -> ApiException.notFound("entity.file"));
    if (!queryService.canDownload(principal, po)) {
      throw ApiException.keyed(ErrorCode.DATA_FORBIDDEN, "file.guard.invisible");
    }
    try (InputStream content = storage.read(po.getPath())) {
      if (content == null) {
        throw ApiException.notFound("entity.file");
      }
      // 下载即计数 +1（platform 卡 §3.5）；行级自增，见 FileRepository#incrementDownloads（T57/BE-05）
      repository.incrementDownloads(po.getId());
      byte[] bytes = content.readAllBytes();
      // T10：下载类审计——文件名/大小/文件哈希/累计下载数 + 来源 IP（VISION 事项 4 第 6 行）
      auditRecorder.recordDownload(principal.account(), "file-download", "file", po.getId(),
          "GET /api/v1/files/" + fileId + "/download", request.getRemoteAddr(), request.getHeader("User-Agent"),
          java.util.Map.of(
              "title", po.getTitle(),
              "size", po.getSize() == null ? bytes.length : po.getSize(),
              "sha256", AuditHasher.sha256(bytes)));
      return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
              .filename(po.getTitle(), StandardCharsets.UTF_8)
              .build()
              .toString())
          // T60：与 /raw 同款——attachment + octet-stream 之外再加一道"别猜类型"（纵深防御）
          .header("X-Content-Type-Options", "nosniff")
          .contentType(MediaType.APPLICATION_OCTET_STREAM)
          .body(bytes);
    }
  }

  @DeleteMapping("/files/{fileId}")
  @Operation(operationId = "deleteFile")
  @Audit(action = "file-delete", objectType = "file")
  public DataEnvelope<Void> remove(@PathVariable long fileId, jakarta.servlet.http.HttpServletRequest request) {
    var principal = resolver.resolve(request);
    FilePO po = repository.findVisibleById(fileId).orElseThrow(() -> ApiException.notFound("entity.file"));
    if (!queryService.canDelete(principal, po)) {
      throw ApiException.keyed(ErrorCode.DATA_FORBIDDEN, "file.guard.deleteOwnOnly");
    }
    repository.softDelete(po);
    return DataEnvelope.empty();
  }

  @GetMapping("/files/{fileId}/raw")
  @Operation(operationId = "getRawFile")
  public ResponseEntity<byte[]> raw(@PathVariable long fileId, jakarta.servlet.http.HttpServletRequest request)
      throws Exception {
    var principal = resolver.resolve(request);
    FilePO po = repository.findVisibleById(fileId).orElseThrow(() -> ApiException.notFound("entity.file"));
    if (!queryService.canDownload(principal, po)) {
      throw ApiException.keyed(ErrorCode.DATA_FORBIDDEN, "file.guard.invisible");
    }
    String extension = po.getExtension() == null ? "" : po.getExtension().toLowerCase(java.util.Locale.ROOT);
    MediaType contentType = INLINE_IMAGE_TYPES.get(extension);
    if (contentType == null) {
      throw ApiException.keyed(ErrorCode.BAD_REQUEST, "file.guard.inlineImageOnly");
    }
    try (InputStream content = storage.read(po.getPath())) {
      if (content == null) {
        throw ApiException.notFound("entity.file");
      }
      // 不计 downloads：预览与下载（/download 计数）语义分离
      return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().build().toString())
          .header("X-Content-Type-Options", "nosniff")
          .contentType(contentType)
          .body(content.readAllBytes());
    }
  }
}
