package net.zentao.platform.menu;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.zentao.platform.rbac.PrivilegeCatalog;
import net.zentao.platform.rbac.PrivilegeChecker;
import net.zentao.platform.session.SessionPrincipal;
import org.springframework.stereotype.Component;

/**
 * 菜单树装配（T03 纯 DB 化 / ADR-001）：唯一数据源是 `menu` 表（整树由 V42 从页面注册表播种）。
 *
 * <p>三层节点，各有真源：
 * 一级目录/分区（容器）、菜单项（页面）、隐藏页都是 **DB 行**（可增删改/换父/排序，没有「内置节点」概念）；
 * 按钮仍是**代码派生**——① 页面文件里的 `HasPerm`/`hasPerm` 字面量（页面级精确归属）；② 权限编目里按
 * **域前缀**归属到该域首页的码（工作流动作码如 `task-finish` 只出现在后端 `@RequirePrivilege` 与 meta.actions
 * 里，页面扫不到）。它随代码变化，故不播种进 DB，但 DB 里同 key（`页面key#权限码`）的行可覆盖其标题/排序/状态。
 *
 * <p>三个出口：
 * {@link #tree()} 管理视图（全量，含停用/隐藏页/按钮）；
 * {@link #myTree} 侧栏视图（停用丢、隐藏页丢、按钮丢、无权限码的项丢、空容器丢）；
 * {@link #routes()} 前端路由表（DB 菜单行 ∪ 注册表里尚无 DB 行的隐藏页；component 是代码实现）。
 */
@Component
public class MenuQueryService {

  /** 渲染类型（树节点 kind）。 */
  public static final String GROUP = "group";
  public static final String SECTION = "section";
  public static final String ITEM = "item";
  public static final String BUTTON = "button";

  /** 叶子节点的排序兜底：管理员没填 orderNo 时排在内置项之后。 */
  private static final int DEFAULT_ORDER = 999;
  /** 按钮在页面节点下的排序基数（都排在菜单项自己的子级后面，除非管理员改过 order）。 */
  private static final int BUTTON_ORDER_BASE = 900;

  private static final Comparator<MenuNode> BY_ORDER =
      Comparator.comparingInt(MenuNode::orderNo).thenComparing(MenuNode::key);

  /** 树节点：容器、菜单项、按钮同型（children 表达层级，kind 表达类型）。身份是 key，写端点按它寻址。 */
  public record MenuNode(String key,
      String parentKey,
      @Schema(allowableValues = {"group", "section", "item", "button"}) String kind, String title, String path,
      String component, String icon, int orderNo, String perm,
      @Schema(allowableValues = {"active", "disabled"}) String status, boolean hidden, List<MenuNode> children) {}

  public record MenuTree(List<MenuNode> items) {}

  /** 前端路由表条目（T26 动态路由）：组件按 component 名从代码里的组件表取，路径/权限/标题是数据。 */
  public record RouteEntry(String path, String component, String title, String perm, String activeMenu,
      @Schema(allowableValues = {"active", "disabled"}) String status, boolean hidden) {}

  public record RouteTable(List<RouteEntry> items) {}

  /** 页面注册表条目（T03）：path → component 的代码侧对应关系，菜单表单按 path 自动匹配组件。 */
  public record PageRegistryEntry(String key, String path, String component, String titleKey, String perm,
      boolean hide) {}

  public record PageRegistry(List<PageRegistryEntry> items) {}

  private final MenuPageRegistry registry;
  private final MenuRepository repository;
  private final PrivilegeChecker privilegeChecker;
  private final PrivilegeCatalog catalog;

  public MenuQueryService(MenuPageRegistry registry, MenuRepository repository, PrivilegeChecker privilegeChecker,
      PrivilegeCatalog catalog) {
    this.registry = registry;
    this.repository = repository;
    this.privilegeChecker = privilegeChecker;
    this.catalog = catalog;
  }

  /** 管理视图：全量 DB 树（含停用、隐藏页与按钮）。 */
  public MenuTree tree() {
    return build(null);
  }

  /** 侧栏视图：当前账号可见（停用丢、隐藏页丢、按钮丢、无权限码丢、空容器丢）。 */
  public MenuTree myTree(SessionPrincipal principal) {
    return build(principal);
  }

  /** 页面注册表（写入口与表单用的代码侧清单）。 */
  public PageRegistry pageRegistry() {
    List<PageRegistryEntry> items = new ArrayList<>();
    for (MenuPageRegistry.Page page : registry.pages()) {
      items.add(new PageRegistryEntry(page.key(), page.path(), page.component(), page.title(), page.perm(),
          page.hidden()));
    }
    return new PageRegistry(List.copyOf(items));
  }

  /**
   * 前端路由表：**全部页面**（含隐藏页）的 path/component/title/perm/activeMenu。
   * 数据源 = DB 里带 component 的菜单行 ∪ 页面注册表里尚无 DB 行的隐藏页（兜底：新页面不至漏路由）。
   */
  public RouteTable routes() {
    List<RouteEntry> items = new ArrayList<>();
    Set<String> covered = new LinkedHashSet<>();
    for (MenuPO row : repository.listAll()) {
      if (!MenuPO.MENU.equals(row.getNodeType()) || row.getComponent() == null || row.getComponent().isBlank()
          || row.getPath() == null || row.getPath().isBlank()) {
        continue;
      }
      boolean hidden = hiddenOf(row);
      items.add(new RouteEntry(row.getPath(), row.getComponent(), row.getTitle(), row.getPerm(),
          activeMenuOf(row, hidden), row.getStatus() == null ? MenuRepository.ACTIVE : row.getStatus(), hidden));
      covered.add(row.getPath());
      covered.add(row.getNodeKey());
    }
    for (MenuPageRegistry.Page page : registry.hiddenPages()) {
      if (covered.contains(page.path()) || covered.contains(page.key())) {
        continue;
      }
      items.add(new RouteEntry(page.path(), page.component(), page.title(), page.perm(), page.activeMenu(),
          MenuRepository.ACTIVE, true));
    }
    return new RouteTable(List.copyOf(items));
  }

  /** 单个 DB 行的节点（写接口的返回体：管理页据此就地更新一行）。 */
  public MenuNode nodeOf(MenuPO row) {
    return flat(tree()).getOrDefault(row.getNodeKey(), bareNode(row));
  }

  /** 节点在树里的渲染类型（写入口校验「上级能不能挂这种子节点」用）。 */
  public java.util.Optional<String> kindOf(String key) {
    return java.util.Optional.ofNullable(flat(tree()).get(key)).map(MenuNode::kind);
  }

  /** key → 节点（管理视图的扁平索引）。 */
  public Map<String, MenuNode> flat(MenuTree tree) {
    Map<String, MenuNode> flat = new LinkedHashMap<>();
    collect(tree.items(), flat);
    return flat;
  }

  private static void collect(List<MenuNode> nodes, Map<String, MenuNode> flat) {
    for (MenuNode node : nodes) {
      flat.put(node.key(), node);
      collect(node.children(), flat);
    }
  }

  private MenuTree build(SessionPrincipal principal) {
    List<MenuPO> rows = repository.listAll();
    Map<String, MenuPO> byKey = new LinkedHashMap<>();
    Map<String, List<MenuPO>> byParent = new LinkedHashMap<>();
    for (MenuPO row : rows) {
      if (row.getNodeKey() == null) {
        continue;
      }
      byKey.put(row.getNodeKey(), row);
      byParent.computeIfAbsent(row.getParentKey() == null ? "" : row.getParentKey(), key -> new ArrayList<>()).add(row);
    }
    Ctx ctx = new Ctx(byKey, byParent, catalogButtons());
    Set<String> visiting = new LinkedHashSet<>();
    List<MenuNode> roots = new ArrayList<>();
    for (MenuPO row : ctx.childrenOf(null)) {
      MenuNode node = slot(row.getNodeKey(), null, ctx, visiting, principal);
      if (node != null) {
        roots.add(node);
      }
    }
    roots.sort(BY_ORDER);
    return new MenuTree(List.copyOf(roots));
  }

  /** 一个 key 的节点：字段全来自 DB 行；children = 代码派生的按钮 + 挂在它下面的 DB 子行。 */
  private MenuNode slot(String key, String parentKind, Ctx ctx, Set<String> visiting, SessionPrincipal principal) {
    if (!visiting.add(key)) {
      return null; // 已渲染过（按钮的覆盖行）或成环：跳过，写入口也会挡
    }
    MenuPO row = ctx.rows().get(key);
    if (row == null) {
      return null;
    }
    String kind = kindOfRow(row, parentKind);
    List<MenuNode> children = new ArrayList<>();
    if (ITEM.equals(kind)) {
      children.addAll(itemButtons(key, ctx, visiting));
    }
    for (MenuPO childRow : ctx.childrenOf(key)) {
      MenuNode node = slot(childRow.getNodeKey(), kind, ctx, visiting, principal);
      if (node != null) {
        children.add(node);
      }
    }
    children.sort(BY_ORDER);
    MenuNode node = new MenuNode(key, row.getParentKey(), kind, row.getTitle(), row.getPath(), row.getComponent(),
        row.getIcon(), row.getOrderNo() == null ? DEFAULT_ORDER : row.getOrderNo(), row.getPerm(), row.getStatus(),
        hiddenOf(row), List.copyOf(children));
    if (principal != null && !visible(node, principal)) {
      return null;
    }
    return node;
  }

  /**
   * 页面节点（kind=item）的按钮子级：页面文件扫出来的 + 权限编目按域归属的。
   *
   * <p>按钮 key = `页面key#权限码`：既是它的树内身份，也是覆盖入口（DB 建同 key 的行即可改名/停用）。
   * 渲染前先占住 key（`visiting`），免得同 key 的覆盖行又被下面的 DB 子行循环重复挂一遍。
   */
  private List<MenuNode> itemButtons(String pageKey, Ctx ctx, Set<String> visiting) {
    Set<String> codes = new LinkedHashSet<>();
    registry.page(pageKey).map(MenuPageRegistry.Page::buttons).ifPresent(codes::addAll);
    codes.addAll(ctx.catalogButtons().getOrDefault(pageKey, List.of()));
    List<MenuNode> buttons = new ArrayList<>();
    int index = 0;
    for (String code : codes) {
      String key = pageKey + "#" + code;
      if (visiting.add(key)) {
        buttons.add(buttonNode(key, pageKey, code, ctx, BUTTON_ORDER_BASE + index));
      }
      index += 1;
    }
    return buttons;
  }

  /**
   * 按钮节点：DB 行同 key 即覆盖（改名/停用/排序）；否则用代码派生的默认值。
   *
   * <p>标题是 i18n 键 `priv.<code>`（语言包里的权限码名字表，188 条，zh/en 同步）：按钮要显示「创建待办」
   * 而不是 `todo-create`，且名字得跟着语言切换走——这正是项目里文案的统一出口。
   */
  private MenuNode buttonNode(String key, String pageKey, String code, Ctx ctx, int order) {
    MenuPO row = ctx.rows().get(key);
    return row == null
        ? new MenuNode(key, pageKey, BUTTON, "priv." + code, null, null, null, order, code,
            MenuRepository.ACTIVE, false, List.of())
        : new MenuNode(key, row.getParentKey() == null ? pageKey : row.getParentKey(), BUTTON, row.getTitle(), null,
            null, row.getIcon(), row.getOrderNo() == null ? order : row.getOrderNo(), row.getPerm(), row.getStatus(),
            false, List.of());
  }

  /**
   * 权限编目里「没有页面声明」的码按域前缀归属：`task-finish` → 页面 perm 为 `task-view` 的那个页面。
   * 这些码只出现在后端守卫与 meta.actions 里（页面用 `hasPerm(privileges, action.code)` 渲染），
   * 扫不到字面量，但它们的域就是页面所在的功能域。
   *
   * <p>候选页排序：可见页优先 → 末段不是 `:param`（列表页）优先 → order 小 → path 短 → 字典序，
   * 保证同一份数据每次构建结果一致（前端树稳定，不随 HashMap 顺序抖）。
   */
  private Map<String, List<String>> catalogButtons() {
    Map<String, List<MenuPageRegistry.Page>> candidatesByPrefix = new LinkedHashMap<>();
    Set<String> declared = new LinkedHashSet<>();
    Set<String> pagePerms = new LinkedHashSet<>();
    for (MenuPageRegistry.Page page : registry.pages()) {
      declared.addAll(page.buttons());
      if (page.perm() != null) {
        pagePerms.add(page.perm());
      }
      if (page.perm() != null && page.perm().endsWith("-view")) {
        String prefix = page.perm().substring(0, page.perm().length() - "-view".length());
        candidatesByPrefix.computeIfAbsent(prefix, key -> new ArrayList<>()).add(page);
      }
    }
    Map<String, List<String>> result = new LinkedHashMap<>();
    for (String code : catalog.allCodes()) {
      // 已经是某个页面的自身权限码（页面节点就在树里）或已作为按钮声明过 → 不重复挂
      if (declared.contains(code) || pagePerms.contains(code)) {
        continue;
      }
      int dash = code.indexOf('-');
      if (dash <= 0) {
        continue;
      }
      List<MenuPageRegistry.Page> candidates = candidatesByPrefix.get(code.substring(0, dash));
      if (candidates == null) {
        continue;
      }
      MenuPageRegistry.Page home = candidates.stream().min(PAGE_PREFERENCE).orElseThrow();
      result.computeIfAbsent(home.path(), key -> new ArrayList<>()).add(code);
    }
    return result;
  }

  /** 归属页偏好：可见优先 → 列表页（末段不是 :param）优先 → order 小 → path 短 → 字典序（结果稳定）。 */
  private static final Comparator<MenuPageRegistry.Page> PAGE_PREFERENCE = Comparator
      .comparing(MenuPageRegistry.Page::hidden)
      .thenComparing(page -> page.path().lastIndexOf('/') >= 0
          && page.path().substring(page.path().lastIndexOf('/') + 1).startsWith(":") ? 1 : 0)
      .thenComparingInt(MenuPageRegistry.Page::order)
      .thenComparingInt(page -> page.path().length())
      .thenComparing(MenuPageRegistry.Page::path);

  /** 「是不是隐藏页」仍来自页面注解（@hide 是代码事实，不是数据）：先按 key，再按当前 path 兜底。 */
  private boolean hiddenOf(MenuPO row) {
    return registry.page(row.getNodeKey()).or(() -> registry.page(row.getPath()))
        .map(MenuPageRegistry.Page::hidden).orElse(false);
  }

  /** 隐藏页的菜单高亮目标：DB 里就是它的 parent_key（宿主页面），注册表里的 activeMenu 作兜底。 */
  private String activeMenuOf(MenuPO row, boolean hidden) {
    if (!hidden) {
      return null;
    }
    if (row.getParentKey() != null && !row.getParentKey().isBlank()) {
      return row.getParentKey();
    }
    return registry.page(row.getNodeKey()).map(MenuPageRegistry.Page::activeMenu).orElse(null);
  }

  private static String kindOfRow(MenuPO row, String parentKind) {
    return switch (row.getNodeType() == null ? MenuPO.MENU : row.getNodeType()) {
      case MenuPO.DIR -> parentKind == null ? GROUP : SECTION;
      case MenuPO.BUTTON -> BUTTON;
      default -> ITEM;
    };
  }

  /** 可见性：管理视图（principal=null）全放行；侧栏视图按状态 + 权限码过滤，隐藏页与按钮不进侧栏。 */
  private boolean visible(MenuNode node, SessionPrincipal principal) {
    if (!MenuRepository.ACTIVE.equals(node.status())) {
      return false;
    }
    if (BUTTON.equals(node.kind()) || node.hidden()) {
      return false;
    }
    if (GROUP.equals(node.kind()) || SECTION.equals(node.kind())) {
      return !node.children().isEmpty();
    }
    return node.perm() == null || node.perm().isBlank() || privilegeChecker.hasPrivilege(principal, node.perm());
  }

  /** 树里找不到（父节点被删留下的悬空行）时的兜底节点：至少让写接口回一个体面的对象。 */
  private MenuNode bareNode(MenuPO row) {
    return new MenuNode(row.getNodeKey(), row.getParentKey(), kindOfRow(row, null), row.getTitle(), row.getPath(),
        row.getComponent(), row.getIcon(), row.getOrderNo() == null ? DEFAULT_ORDER : row.getOrderNo(), row.getPerm(),
        row.getStatus(), hiddenOf(row), List.of());
  }

  private record Ctx(Map<String, MenuPO> rows, Map<String, List<MenuPO>> byParent,
      Map<String, List<String>> catalogButtons) {

    List<MenuPO> childrenOf(String parentKey) {
      return byParent.getOrDefault(parentKey == null ? "" : parentKey, List.of());
    }
  }
}
