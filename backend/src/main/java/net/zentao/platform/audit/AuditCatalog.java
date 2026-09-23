package net.zentao.platform.audit;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 审计动作目录（ADR-004 决策 2，T04）：把「动作 → 分类/粒度/关键字段/是否快照」从散落的调用点收成一张表。
 *
 * <p>登记是**声明式且可叠加**的：内置一批（9 类各至少一条锚点 + 当前已存在的写端点动作），各域在
 * 自己的 {@code *Registrar} 里追加（T10 的分类落地），未登记的动作回落
 * {@link #DEFAULT_SPEC}（business/FULL）——**新写端点不登记也能被审**，只是分类粗一档。
 *
 * <p>为什么不是配置文件：分类要与动作名同源演进（改动作名忘了改配置 = 静默错分类），
 * 代码里的 register 调用点在 review 里看得见。
 */
@Component
public class AuditCatalog {

  /** 一条动作的采集口径；{@code keyFields} 为空 = 由 provider 给出的全部字段参与 diff。 */
  public record AuditSpec(AuditCategory category, AuditLevel level, List<String> keyFields, boolean snapshot) {}

  /** 未登记动作的回落口径：业务类，逐条落主表、不带 diff（不敢对未知对象做前后比对）。 */
  private static final AuditSpec DEFAULT_SPEC =
      new AuditSpec(AuditCategory.BUSINESS, AuditLevel.FULL, List.of(), false);

  private final Map<String, AuditSpec> specs = new ConcurrentHashMap<>();

  public AuditCatalog() {
    registerBuiltIns();
  }

  /** 登记（同动作重复登记 = 后登记覆盖，供域卡按需收窄内置口径）。 */
  public void register(String action, AuditCategory category, AuditLevel level, List<String> keyFields,
      boolean snapshot) {
    specs.put(action, new AuditSpec(category, level, List.copyOf(keyFields), snapshot));
  }

  public AuditSpec spec(String action) {
    return specs.getOrDefault(action, DEFAULT_SPEC);
  }

  /** 分类（唯一出口：AuditRecorder 与读侧都用它，避免两处各写一份映射）。 */
  public AuditCategory categoryOf(String action) {
    return spec(action).category();
  }

  /** 已登记动作集（门禁用/自检用）。 */
  public Map<String, AuditSpec> specs() {
    return Map.copyOf(specs);
  }

  private void registerBuiltIns() {
    // ── auth：登录/登出/会话（每次成功+失败；失败行由 SessionController 的 catch 落 reason）──
    register("login", AuditCategory.AUTH, AuditLevel.SUMMARY, List.of(), false);
    register("login-failed", AuditCategory.AUTH, AuditLevel.SUMMARY, List.of(), false);
    register("logout", AuditCategory.AUTH, AuditLevel.SUMMARY, List.of(), false);
    register("online-user-kick", AuditCategory.AUTH, AuditLevel.SUMMARY, List.of(), false);

    // ── perm：权限/角色/账号变更（字段级 diff）──
    register("account-create", AuditCategory.PERM, AuditLevel.FULL, List.of("account", "realName", "status"), false);
    register("account-update", AuditCategory.PERM, AuditLevel.FULL, List.of("account", "realName", "status"), false);
    register("account-delete", AuditCategory.PERM, AuditLevel.FULL, List.of("account", "realName", "status"), false);
    register("role-create", AuditCategory.PERM, AuditLevel.FULL, List.of("code", "name"), false);
    register("role-update", AuditCategory.PERM, AuditLevel.FULL, List.of("code", "name"), false);
    register("role-delete", AuditCategory.PERM, AuditLevel.FULL, List.of("code", "name"), false);
    register("role-grant", AuditCategory.PERM, AuditLevel.FULL, List.of(), false);

    // ── config：配置/字典/菜单/参数变更（字段级 diff）──
    register("dict-type-create", AuditCategory.CONFIG, AuditLevel.FULL, List.of(), false);
    register("dict-type-update", AuditCategory.CONFIG, AuditLevel.FULL, List.of(), false);
    register("dict-type-delete", AuditCategory.CONFIG, AuditLevel.FULL, List.of(), false);
    register("dict-item-create", AuditCategory.CONFIG, AuditLevel.FULL, List.of(), false);
    register("dict-item-update", AuditCategory.CONFIG, AuditLevel.FULL, List.of(), false);
    register("dict-item-delete", AuditCategory.CONFIG, AuditLevel.FULL, List.of(), false);
    register("setting-entry-create", AuditCategory.CONFIG, AuditLevel.FULL, List.of("value"), false);
    register("setting-entry-update", AuditCategory.CONFIG, AuditLevel.FULL, List.of("value"), false);
    register("setting-entry-delete", AuditCategory.CONFIG, AuditLevel.FULL, List.of("value"), false);
    register("menu-create", AuditCategory.CONFIG, AuditLevel.FULL,
        List.of("title", "path", "icon", "orderNo", "perm", "status"), false);
    register("menu-update", AuditCategory.CONFIG, AuditLevel.FULL,
        List.of("title", "path", "icon", "orderNo", "perm", "status"), false);
    register("menu-delete", AuditCategory.CONFIG, AuditLevel.FULL, List.of("title", "path"), false);
    register("audit-cleanup", AuditCategory.CONFIG, AuditLevel.SUMMARY, List.of(), false);

    // ── 其余五类的锚点：动作名先定，埋点随各自的卡接入（T10/T19/T22/T25/T34）──
    register("business-update", AuditCategory.BUSINESS, AuditLevel.FULL, List.of(), false);
    // 批量类共用动作名（objectType 区分资源）；「删除/批量操作」在 VISION 里是同一行粒度
    register("batch-operation", AuditCategory.BATCH, AuditLevel.SUMMARY, List.of(), false);
    // 导出/下载类：CSV（?format=csv 横切）、附件下载、语言包模板导出——三处出口各一个动作名
    register("export-csv", AuditCategory.EXPORT, AuditLevel.SUMMARY, List.of(), false);
    register("file-download", AuditCategory.EXPORT, AuditLevel.SUMMARY, List.of(), false);
    register("lang-export", AuditCategory.EXPORT, AuditLevel.SUMMARY, List.of(), false);
    // 附件与语言包：发生在业务对象上的文件动作（上传/软删）与配置变更（文案上传）
    register("file-upload", AuditCategory.BUSINESS, AuditLevel.SUMMARY, List.of(), false);
    register("file-delete", AuditCategory.BUSINESS, AuditLevel.SUMMARY, List.of(), false);
    register("lang-import", AuditCategory.CONFIG, AuditLevel.SUMMARY, List.of(), false);
    // 敏感读共用动作名（objectType 区分资源；字段清单在 @AuditSensitive / extra.fields）
    register("sensitive-view", AuditCategory.SENSITIVE, AuditLevel.SUMMARY, List.of(), false);
    // 权限拒绝（result=denied）：拦截器显式记账，动作名固定一个（详情里带被拒的端点与权限码）
    register("access-denied", AuditCategory.PERM, AuditLevel.SUMMARY, List.of(), false);
    register("api-query", AuditCategory.QUERY, AuditLevel.SAMPLED, List.of(), false);
    register("approve-publish", AuditCategory.APPROVE, AuditLevel.FULL, List.of(), true);
  }
}
