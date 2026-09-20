package net.zentao.platform.web;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.langimport.LangImportQueryService;
import net.zentao.platform.langimport.LangImportService;
import net.zentao.platform.langimport.LangImportView;
import net.zentao.platform.langimport.LangOverrideQueryService;
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
 * 多语言上传四端点（platform 卡 §3.12）：覆盖层全量读取 / 语言包导出 / Excel 上传 / 上传记录列表。
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

  public LangImportController(
      LangImportService importService,
      LangImportQueryService queryService,
      LangOverrideQueryService overrideQueryService,
      SessionResolver resolver) {
    this.importService = importService;
    this.queryService = queryService;
    this.overrideQueryService = overrideQueryService;
    this.resolver = resolver;
  }

  @GetMapping("/lang-items/overrides")
  @Operation(operationId = "listLangOverrides")
  @RequirePrivilege("lang-manage")
  public DataEnvelope<LangOverrideQueryService.LangOverrideList> listOverrides(
      @RequestParam(defaultValue = "zh-cn") String lang) {
    return DataEnvelope.of(overrideQueryService.overrides(lang));
  }

  @GetMapping("/lang-items/export")
  @Operation(operationId = "exportLangItems")
  @RequirePrivilege("lang-manage")
  public ResponseEntity<byte[]> export() {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
            .filename(EXPORT_FILE_NAME, StandardCharsets.UTF_8)
            .build()
            .toString())
        .contentType(MediaType.parseMediaType(LangXlsx.CONTENT_TYPE))
        .body(importService.export());
  }

  @PostMapping("/lang-imports")
  @Operation(operationId = "createLangImport")
  @RequirePrivilege("lang-manage")
  public DataEnvelope<LangImportView> create(
      @RequestParam("file") MultipartFile file,
      @RequestParam String lang,
      HttpServletRequest request) throws Exception {
    try (InputStream content = file.getInputStream()) {
      LangImportService.ImportResult result =
          importService.importFile(resolver.resolve(request), lang, file.getOriginalFilename(), content.readAllBytes());
      if (result.failed()) {
        // 失败记录已在服务层同事务落库（lang_import.status=failed），此处的 422 只负责把原因码交给前端
        throw ApiException.validation(result.errors());
      }
      return DataEnvelope.of(result.log());
    }
  }

  @GetMapping("/lang-imports")
  @Operation(operationId = "listLangImports")
  @RequirePrivilege("lang-manage")
  public DataEnvelope<LangImportQueryService.LangImportList> list(HttpServletRequest request) {
    return DataEnvelope.of(queryService.page(request.getParameterMap()));
  }
}
