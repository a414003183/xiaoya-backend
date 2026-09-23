package net.zentao.platform.web;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import net.zentao.platform.audit.Audit;
import net.zentao.platform.audit.AuditHasher;
import net.zentao.platform.audit.AuditRecorder;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.i18n.LangPackMessageSource;
import net.zentao.platform.langimport.LangImportQueryService;
import net.zentao.platform.langimport.LangImportService;
import net.zentao.platform.langimport.LangImportView;
import net.zentao.platform.i18n.LangOverrideQueryService;
import net.zentao.platform.langimport.LangXlsx;
import net.zentao.platform.rbac.RequirePrivilege;
import net.zentao.platform.session.SessionResolver;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 多语言上传四端点（platform 卡 §3.12；T21 起「文案覆盖」编辑器已删，这里是唯一的文案维护面）：
 * 覆盖层全量读取（前端运行时合并）/ 语言包导出 / Excel 上传 / 上传记录列表。
 * 四端点同属 `lang-manage`（含导出与只读的日志列表——文案资产的读写面整体收在同一权限码下）。
 */
@RestController
@RequestMapping("/api/v1")
public class LangImportController {

  /** 导出文件名：随语言包资产走，界面按此名保存（zh-CN 大写是 ISO 习惯写法，语言码本身是 zh-cn）。 */
  private static final String EXPORT_FILE_NAME = "lang-zh-CN.xlsx";

  private final LangImportService importService;
  private final LangImportQueryService queryService;
  private final LangOverrideQueryService overrideQueryService;
  private final SessionResolver resolver;
  private final LangPackMessageSource messageSource;
  private final AuditRecorder auditRecorder;

  public LangImportController(
      LangImportService importService,
      LangImportQueryService queryService,
      LangOverrideQueryService overrideQueryService,
      SessionResolver resolver,
      LangPackMessageSource messageSource,
      AuditRecorder auditRecorder) {
    this.importService = importService;
    this.queryService = queryService;
    this.overrideQueryService = overrideQueryService;
    this.resolver = resolver;
    this.messageSource = messageSource;
    this.auditRecorder = auditRecorder;
  }

  /**
   * 覆盖层全量读取：**登录即可，无功能码**（domains/i18n.md 的口径）。
   * 它是前端运行时合并的数据源（`LangOverrides` 挂在 app-router 上，每个登录用户都会拉），
   * 要 `lang-manage` 的话普通用户拿不到覆盖文案 = 「上传即生效」对他们不成立（T05 实测修正：
   * 此前本端点确实要 lang-manage，与域卡登记的「登录」口径不符，且让普通用户每次进页面吃一个 403）。
   */
  @GetMapping("/lang-items/overrides")
  @Operation(operationId = "listLangOverrides")
  public DataEnvelope<LangOverrideQueryService.LangOverrideList> listOverrides(
      @RequestParam(defaultValue = "zh-cn") String lang) {
    return DataEnvelope.of(overrideQueryService.overrides(lang));
  }

  @GetMapping("/lang-items/export")
  @Operation(operationId = "exportLangItems")
  @RequirePrivilege("lang-manage")
  public ResponseEntity<byte[]> export(HttpServletRequest request) {
    byte[] payload = importService.export();
    // T10：语言包模板导出也是「导出/下载」类——条数即工作表行数上限不做统计，落大小与文件哈希
    auditRecorder.recordDownload(resolver.resolve(request).account(), "lang-export", "langItem", null,
        "GET /api/v1/lang-items/export", request.getRemoteAddr(), request.getHeader("User-Agent"),
        java.util.Map.of("file", EXPORT_FILE_NAME, "size", payload.length, "sha256", AuditHasher.sha256(payload)));
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
            .filename(EXPORT_FILE_NAME, StandardCharsets.UTF_8)
            .build()
            .toString())
        .contentType(MediaType.parseMediaType(LangXlsx.CONTENT_TYPE))
        .body(payload);
  }

  @PostMapping("/lang-imports")
  @Operation(operationId = "createLangImport")
  @RequirePrivilege("lang-manage")
  @Audit(action = "lang-import", objectType = "langImport")
  public DataEnvelope<LangImportView> create(
      @RequestParam("file") MultipartFile file,
      HttpServletRequest request) throws Exception {
    // T60/SEC-09：不再 readAllBytes——体积闸门在服务层「读之前」判（超 5MB 不进堆，50MB 容器上限下也不）
    LangImportService.ImportResult result = importService.importFile(
        resolver.resolve(request), file.getOriginalFilename(), file.getSize(), file.getInputStream());
    if (result.failed()) {
      // 失败记录已在服务层同事务落库（lang_import.status=failed），此处的 422 只负责把原因码交给前端
      throw ApiException.validation(result.errors());
    }
    // 上传=文案变更：清掉消息解析的覆盖层缓存，后端错误/审计文案**当次刷新起即时生效**
    // （事务已在 importFile 返回时提交，此处清缓存不会读到未提交数据）
    messageSource.invalidate();
    return DataEnvelope.of(result.log());
  }

  @GetMapping("/lang-imports")
  @Operation(operationId = "listLangImports")
  @RequirePrivilege("lang-manage")
  public DataEnvelope<LangImportQueryService.LangImportList> list(HttpServletRequest request) {
    return DataEnvelope.of(queryService.page(request.getParameterMap()));
  }
}
