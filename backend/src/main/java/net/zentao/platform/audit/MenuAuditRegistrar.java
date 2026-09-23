package net.zentao.platform.audit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.zentao.platform.menu.MenuRepository;
import org.springframework.context.annotation.Configuration;

/**
 * 菜单资源的审计登记（T10 / VISION 事项 4 第 3 行「配置/开关/密钥变更 → 字段级 diff」）。
 *
 * <p>为什么注册点写在 {@code platform.audit} 而不是 {@code platform.menu}：platform 关注点之间不许成环
 * （ArchUnit A2），而 {@code audit → i18n} 与 {@code i18n → meta} 两条既有边在那里，{@code meta/menu → audit}
 * 会立刻把环闭上（实测）。故 platform 自身资源（菜单/字典/参数）的登记表统一放在 audit 侧，
 * **边方向恒为 audit → 资源包**，与业务域的「域 → audit」方向相反但同属一个无环图。
 */
@Configuration
public class MenuAuditRegistrar {

  public MenuAuditRegistrar(AuditCatalog auditCatalog, AuditSnapshotRegistry auditSnapshots,
      MenuRepository menuRepository) {
    // 写端点按 nodeKey 寻址，objectType 与快照注册键都取 menu；字段集只放导航与可见性相关的关键字段
    auditCatalog.register("menu-create", AuditCategory.CONFIG, AuditLevel.FULL,
        List.of("title", "path", "icon", "orderNo", "perm", "status"), false);
    auditCatalog.register("menu-update", AuditCategory.CONFIG, AuditLevel.FULL,
        List.of("title", "path", "icon", "orderNo", "perm", "status"), false);
    auditCatalog.register("menu-delete", AuditCategory.CONFIG, AuditLevel.FULL,
        List.of("title", "path"), false);

    // 快照 provider：**每次返回新 Map**（框架留着 before 再取 after 比对，同一个可变 Map 会让 diff 恒为空）；
    // 字段用 LinkedHashMap 装（Map.of 不收 null，而 path/icon/perm 这类字段可以为空）
    // 键寻址：身份是 nodeKey（含 `/`/`#` 的字符串），进不了 BIGINT 的 object_id
    auditSnapshots.registerKeyed("menu", nodeKey -> menuRepository.findByKey(nodeKey)
        .map(po -> {
          Map<String, Object> snapshot = new LinkedHashMap<>();
          snapshot.put("title", po.getTitle());
          snapshot.put("path", po.getPath());
          snapshot.put("icon", po.getIcon());
          snapshot.put("orderNo", po.getOrderNo());
          snapshot.put("perm", po.getPerm());
          snapshot.put("status", po.getStatus());
          return snapshot;
        })
        .orElse(null));
  }
}
