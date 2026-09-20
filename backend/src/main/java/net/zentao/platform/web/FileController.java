package net.zentao.platform.web;

import io.swagger.v3.oas.annotations.Operation;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.file.FilePO;
import net.zentao.platform.file.FileQueryService;
import net.zentao.platform.file.FileRepository;
import net.zentao.platform.file.FileStorage;
import net.zentao.platform.file.FileView;
import net.zentao.platform.file.UploadFileHandler;
import net.zentao.platform.rbac.RequirePrivilege;
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

  /** 图片内联白名单（A-02：扩展名小写比较；svg 等向量/可执行格式不内联）。 */
  private static final java.util.Map<String, MediaType> INLINE_IMAGE_TYPES = java.util.Map.of(
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

  public FileController(UploadFileHandler uploadHandler, FileQueryService queryService,
      FileRepository repository, FileStorage storage, SessionResolver resolver) {
    this.uploadHandler = uploadHandler;
    this.queryService = queryService;
    this.repository = repository;
    this.storage = storage;
    this.resolver = resolver;
  }

  @PostMapping("/files")
  @Operation(operationId = "uploadFile")
  @RequirePrivilege("file-upload")
  public DataEnvelope<FileView> upload(
      @RequestParam("file") MultipartFile file,
      @RequestParam(required = false) String objectType,
      @RequestParam(required = false) Long objectId,
      jakarta.servlet.http.HttpServletRequest request) throws Exception {
    try (InputStream content = file.getInputStream()) {
      return DataEnvelope.of(uploadHandler.upload(resolver.resolve(request), file.getOriginalFilename(), content,
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
    long id = Long.parseLong(objectId == null ? "0" : objectId);
    return DataEnvelope.of(queryService.page(principal, objectType, id, page, limit));
  }

  @GetMapping("/files/{fileId}/download")
  @Operation(operationId = "downloadFile")
  public ResponseEntity<byte[]> download(@PathVariable long fileId, jakarta.servlet.http.HttpServletRequest request)
      throws Exception {
    var principal = resolver.resolve(request);
    FilePO po = repository.findVisibleById(fileId).orElseThrow(() -> ApiException.notFound("文件"));
    if (!queryService.canDownload(principal, po)) {
      throw ApiException.dataForbidden("文件不可见。");
    }
    try (InputStream content = storage.read(po.getPath())) {
      if (content == null) {
        throw ApiException.notFound("文件");
      }
      queryService.incrementDownloads(po);
      String encoded = URLEncoder.encode(po.getTitle(), StandardCharsets.UTF_8).replace("+", "%20");
      byte[] bytes = content.readAllBytes();
      return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
              .filename(po.getTitle(), StandardCharsets.UTF_8)
              .build()
              .toString())
          .contentType(MediaType.APPLICATION_OCTET_STREAM)
          .body(bytes);
    }
  }

  @DeleteMapping("/files/{fileId}")
  @Operation(operationId = "deleteFile")
  public DataEnvelope<Void> remove(@PathVariable long fileId, jakarta.servlet.http.HttpServletRequest request) {
    var principal = resolver.resolve(request);
    FilePO po = repository.findVisibleById(fileId).orElseThrow(() -> ApiException.notFound("文件"));
    if (!queryService.canDelete(principal, po)) {
      throw ApiException.dataForbidden("仅上传人或超管可删除。");
    }
    repository.softDelete(po);
    return DataEnvelope.empty();
  }

  @GetMapping("/files/{fileId}/raw")
  @Operation(operationId = "getRawFile")
  public ResponseEntity<byte[]> raw(@PathVariable long fileId, jakarta.servlet.http.HttpServletRequest request)
      throws Exception {
    var principal = resolver.resolve(request);
    FilePO po = repository.findVisibleById(fileId).orElseThrow(() -> ApiException.notFound("文件"));
    if (!queryService.canDownload(principal, po)) {
      throw ApiException.dataForbidden("文件不可见。");
    }
    String extension = po.getExtension() == null ? "" : po.getExtension().toLowerCase(java.util.Locale.ROOT);
    MediaType contentType = INLINE_IMAGE_TYPES.get(extension);
    if (contentType == null) {
      throw ApiException.badRequest("仅支持图片内联预览（jpg/jpeg/png/gif/webp/bmp）。");
    }
    try (InputStream content = storage.read(po.getPath())) {
      if (content == null) {
        throw ApiException.notFound("文件");
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
