package net.zentao.platform.menu;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.rbac.PrivilegeCatalog;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 菜单写入口（T21 / T03 纯 DB 化）。
 *
 * <p>四条硬约束：
 * ① 身份是 key（node_key，一经使用不可改；写端点按它寻址）。新建节点落在一棵合法父节点下
 * （目录 → 组；菜单 → 组/分区；按钮 → 菜单）；菜单项的 key 取它的 path（同一页面再挂一个入口时给 `db-<id>`）。
 * ② 层级最多三级（组 → 分区 → 菜单），按钮挂菜单下：侧栏只有这么多层，允许更深的层级等于让管理员建出看不见的菜单。
 * ③ perm 必须在权限编目内（写进菜单却没人能拿到这个码，菜单就永远是空的）；按钮必须有 perm。
 * ④ 菜单项要有 path 且必须命中页面注册表（组件是代码，由 path 推导）；目录与按钮不能有 path。
 *
 * <p>T03 起没有「覆盖内置节点」这条路：菜单行就是菜单，改它走 PATCH、删它就是真的从树上消失。
 */
@Component
public class SaveMenuHandler {

  private static final List<String> STATUSES = List.of("active", "disabled");
  private static final List<String> TYPES = List.of(MenuPO.DIR, MenuPO.MENU, MenuPO.BUTTON);
  private static final int MAX_TITLE = 120;
  private static final int MAX_PATH = 255;
  private static final int MAX_PERM = 64;
  private static final int MAX_ICON = 64;
  private static final int MAX_KEY = 255;

  private final MenuRepository repository;
  private final MenuPageRegistry registry;
  private final MenuQueryService queryService;
  private final PrivilegeCatalog catalog;

  public SaveMenuHandler(MenuRepository repository, MenuPageRegistry registry, MenuQueryService queryService,
      PrivilegeCatalog catalog) {
    this.repository = repository;
    this.registry = registry;
    this.queryService = queryService;
    this.catalog = catalog;
  }

  /** 新建体（T03：没有 key 入口，也没有 component 手选——页面节点的 key 与组件都由 path 推导）。 */
  public record MenuRequest(String parentKey,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"dir", "menu", "button"}) String type,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
      @Schema(description = "菜单项的路由路径，必须命中页面注册表（GET /menus/page-registry）") String path,
      String icon, Integer orderNo, String perm,
      @Schema(allowableValues = {"active", "disabled"}) String status) {}

  /** 更新体（字段全可选：只改传了的；path 传空串 = 清空，仅菜单项有意义）。 */
  public record MenuUpdateRequest(String title, String component, String path, String icon, String perm,
      Integer orderNo, @Schema(allowableValues = {"active", "disabled"}) String status) {}

  @Transactional
  public MenuQueryService.MenuNode create(MenuRequest command) {
    String type = validatedType(command.type());
    String title = validatedTitle(command.title());
    String perm = validatedPerm(command.perm(), type);
    String icon = validatedIcon(command.icon());
    String status = validatedStatus(command.status());
    String parentKey = validatedParentKey(command.parentKey(), type);
    String path = validatedPath(command.path(), type);
    String nodeKey = null;
    String component = null;
    if (MenuPO.MENU.equals(type)) {
      // 组件是代码：path 必须命中页面注册表，nodeKey 取该 path（同一页面再挂一个入口时退化为 db-<id>）
      component = registry.page(path).orElseThrow(() -> ApiException.validation(Map.of("path", "unknown")))
          .component();
      nodeKey = repository.findByKey(path).isPresent() ? null : path;
    }
    MenuPO po = new MenuPO();
    po.setNodeKey(nodeKey == null ? pendingKey() : nodeKey);
    po.setParentKey(parentKey);
    po.setNodeType(type);
    po.setTitle(title);
    po.setComponent(component);
    po.setPath(path);
    po.setIcon(icon);
    po.setPerm(perm);
    po.setOrderNo(command.orderNo() == null ? 999 : command.orderNo());
    po.setStatus(status);
    repository.insert(po);
    if (nodeKey == null) {
      // 新增节点的 key 里带主键：父子关系与寻址都按 key 认，插入后才知道 id（同事务内补齐）
      po.setNodeKey(MenuRepository.NEW_KEY_PREFIX + po.getId());
      repository.update(po);
    }
    return queryService.nodeOf(po);
  }

  @Transactional
  public MenuQueryService.MenuNode update(String nodeKey, MenuUpdateRequest command) {
    MenuPO po = repository.findByKey(nodeKey).orElseThrow(() -> ApiException.notFound("entity.menu"));
    if (command.title() != null) {
      po.setTitle(validatedTitle(command.title()));
    }
    if (command.component() != null && !command.component().isBlank()) {
      po.setComponent(requirePageByComponent(command.component()).component());
    }
    if (command.path() != null) {
      String path = validatedPath(command.path(), po.getNodeType());
      // 路径是数据、组件是代码：改了 path 就按注册表重新匹配（未命中注册表则保留原组件）
      registry.page(path).ifPresent(page -> po.setComponent(page.component()));
      po.setPath(path);
    }
    if (command.icon() != null) {
      po.setIcon(validatedIcon(command.icon()));
    }
    if (command.perm() != null) {
      po.setPerm(validatedPerm(command.perm(), po.getNodeType()));
    }
    if (command.orderNo() != null) {
      po.setOrderNo(command.orderNo());
    }
    if (command.status() != null) {
      po.setStatus(validatedStatus(command.status()));
    }
    repository.update(po);
    return queryService.nodeOf(po);
  }

  /** 删除：该节点连子孙一起从树上消失（物理删除——menu 表没有 deleted_at/lock_version 列）。 */
  @Transactional
  public void delete(String nodeKey) {
    MenuPO po = repository.findByKey(nodeKey).orElseThrow(() -> ApiException.notFound("entity.menu"));
    for (MenuPO row : repository.subtree(po)) {
      repository.delete(row.getId());
    }
  }

  /** 上级节点：目录只能挂在「组」下（或没有上级 = 一级模块）；菜单挂在组/分区；按钮挂在菜单下。 */
  private String validatedParentKey(String parentKey, String type) {
    if (parentKey == null || parentKey.isBlank()) {
      if (MenuPO.DIR.equals(type)) {
        return null;
      }
      throw ApiException.validation(Map.of("parentKey", "required"));
    }
    String key = parentKey.trim().length() > MAX_KEY ? "" : parentKey.trim();
    String parentKind = queryService.kindOf(key)
        .orElseThrow(() -> ApiException.validation(Map.of("parentKey", "unknown")));
    boolean allowed = switch (type) {
      case MenuPO.DIR -> MenuQueryService.GROUP.equals(parentKind);
      case MenuPO.BUTTON -> MenuQueryService.ITEM.equals(parentKind);
      default -> MenuQueryService.GROUP.equals(parentKind) || MenuQueryService.SECTION.equals(parentKind);
    };
    if (!allowed) {
      throw ApiException.validation(Map.of("parentKey", "kind"));
    }
    return key;
  }

  /** 新增节点的临时 key（插入后立刻被 db-<id> 覆盖；唯一约束要求它当场不撞车）。 */
  private static String pendingKey() {
    return MenuRepository.NEW_KEY_PREFIX + "pending-" + UUID.randomUUID();
  }

  /** 组件名 = 页面实现（来自页面文件）；管理端只能从已有页面里选，不能自己造名字。 */
  private MenuPageRegistry.Page requirePageByComponent(String component) {
    String name = component.trim();
    return registry.pageByComponent(name)
        .orElseThrow(() -> ApiException.validation(Map.of("component", "unknown")));
  }

  private static String validatedTitle(String title) {
    if (title == null || title.isBlank() || title.trim().length() > MAX_TITLE) {
      throw ApiException.validation(Map.of("title", "required"));
    }
    return title.trim();
  }

  /** 菜单项必须有路由路径；目录与按钮不是页面入口，路径一律清空。 */
  private static String validatedPath(String path, String type) {
    if (!MenuPO.MENU.equals(type)) {
      return null;
    }
    if (path == null || !path.trim().startsWith("/") || path.trim().length() > MAX_PATH) {
      throw ApiException.validation(Map.of("path", "pattern"));
    }
    return path.trim();
  }

  private static String validatedIcon(String icon) {
    if (icon == null || icon.isBlank()) {
      return null;
    }
    String name = icon.trim();
    if (name.length() > MAX_ICON) {
      throw ApiException.validation(Map.of("icon", "pattern"));
    }
    return name;
  }

  /** 按钮必须带权限码；空 = 无权限要求（菜单项可空：对所有人可见）。 */
  private String validatedPerm(String perm, String type) {
    if (perm == null || perm.isBlank()) {
      if (MenuPO.BUTTON.equals(type)) {
        throw ApiException.validation(Map.of("perm", "required"));
      }
      return null;
    }
    String code = perm.trim();
    if (code.length() > MAX_PERM || !catalog.isRegistered(code)) {
      throw ApiException.validation(Map.of("perm", "unknown"));
    }
    return code;
  }

  private static String validatedType(String type) {
    if (type == null || !TYPES.contains(type)) {
      throw ApiException.validation(Map.of("type", "pattern"));
    }
    return type;
  }

  private static String validatedStatus(String status) {
    if (status == null) {
      return MenuRepository.ACTIVE;
    }
    if (!STATUSES.contains(status)) {
      throw ApiException.validation(Map.of("status", "pattern"));
    }
    return status;
  }
}
