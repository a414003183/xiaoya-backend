package net.zentao.platform.menu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 页面注册表（T03 / ADR-001）：读 `menu/navigation.json`——由 `tools/route-codegen.mjs` 从页面文件生成、
 * 与前端 `routes.tsx` 同源同批（不手抄一份，避免两处漂移）。
 *
 * <p>T03 起菜单的唯一数据源是 `menu` 表（整树由 Flyway 迁移 V42 从这里播种），本类退居二线，只服务三件事：
 * ① `/menus/routes` 补「注册表里有、DB 里还没有行」的隐藏页；② 菜单表单/写入口按 path 自动匹配 component；
 * ③ 页面节点的**按钮存在性**（页面文件里 `HasPerm` 扫出来的那批，代码派生——它随代码变化，不是数据）。
 *
 * <p>产物两块：{@code groups} 是容器树（组 →（分区 →）页面引用），{@code pages} 是**全部页面**（含 @hide）
 * 的元数据：`component`（页面实现，代码所有，管理端不可改）、`path`（路由，管理端可改）、`title`、`perm`、
 * `hidden`、`activeMenu`（隐藏页挂在哪条菜单下）、`buttons`（该页面的按钮权限码）。
 */
@Component
public class MenuPageRegistry {

  /** 一个页面的注册表元数据（代码侧真源）。 */
  public record Page(String key, String path, String component, String title, String perm, String icon, int order,
      boolean hidden, String activeMenu, String menu, List<String> buttons) {}

  /** 容器与菜单项同型：容器有 children，菜单项有 path（生成器保证二者互斥）。 */
  public record Node(String key, String parentKey, String kind, String title, String path, String icon, int order,
      String perm, List<Node> children) {}

  private static final String RESOURCE = "menu/navigation.json";

  private final List<Node> groups;
  private final Map<String, Page> pagesByPath = new LinkedHashMap<>();

  public MenuPageRegistry() {
    ObjectMapper objectMapper = new ObjectMapper();
    try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
      JsonNode root = objectMapper.readTree(in);
      for (JsonNode page : root.path("pages")) {
        pagesByPath.put(page.path("path").asText(), toPage(page));
      }
      this.groups = loadGroups(root);
    } catch (IOException e) {
      throw new UncheckedIOException("页面注册表 " + RESOURCE + " 读取失败（跑 pnpm routes 重新生成）", e);
    }
  }

  /** 注册表里的容器树（组/分区带页面引用），T03 的播种迁移按它建 menu 行。 */
  public List<Node> groups() {
    return groups;
  }

  /** 全部页面（含隐藏页），按注册表顺序。 */
  public List<Page> pages() {
    return List.copyOf(pagesByPath.values());
  }

  public Optional<Page> page(String path) {
    return Optional.ofNullable(pagesByPath.get(path));
  }

  /** 页面按组件名（同一个组件可能被挂成多条菜单项，取第一条即可——组件的默认路径就是它）。 */
  public Optional<Page> pageByComponent(String component) {
    return pagesByPath.values().stream().filter(page -> page.component().equals(component)).findFirst();
  }

  /** 全部隐藏页的 path（@hide 的详情/批量/编辑等上下文页）。 */
  public List<Page> hiddenPages() {
    return pagesByPath.values().stream().filter(Page::hidden).toList();
  }

  private static Page toPage(JsonNode json) {
    List<String> buttons = new ArrayList<>();
    for (JsonNode button : json.path("buttons")) {
      buttons.add(button.asText());
    }
    return new Page(json.path("key").asText(), json.path("path").asText(), opt(json, "component"),
        json.path("title").asText(), opt(json, "perm"), opt(json, "icon"), json.path("order").asInt(999),
        json.path("hidden").asBoolean(false), opt(json, "activeMenu"), opt(json, "menu"), List.copyOf(buttons));
  }

  private List<Node> loadGroups(JsonNode root) {
    List<Node> result = new ArrayList<>();
    for (JsonNode group : root.path("groups")) {
      String groupKey = group.path("key").asText();
      List<Node> children = new ArrayList<>();
      for (JsonNode child : group.path("children")) {
        if (child.hasNonNull("page")) {
          pageNode(child.path("page").asText(), groupKey).ifPresent(children::add);
          continue;
        }
        String sectionKey = child.path("key").asText();
        List<Node> items = new ArrayList<>();
        for (JsonNode item : child.path("children")) {
          pageNode(item.path("page").asText(), sectionKey).ifPresent(items::add);
        }
        children.add(new Node(sectionKey, groupKey, MenuQueryService.SECTION, child.path("title").asText(), null,
            opt(child, "icon"), child.path("order").asInt(999), null, List.copyOf(items)));
      }
      result.add(new Node(groupKey, null, MenuQueryService.GROUP, group.path("title").asText(), null,
          opt(group, "icon"), group.path("order").asInt(0), null, List.copyOf(children)));
    }
    return List.copyOf(result);
  }

  /** 页面引用 → 菜单项节点：字段全来自 pages 索引（组树只带 path，不重复页面字段）。 */
  private Optional<Node> pageNode(String path, String parentKey) {
    Page page = pagesByPath.get(path);
    if (page == null || page.hidden()) {
      return Optional.empty();
    }
    return Optional.of(new Node(page.path(), parentKey, MenuQueryService.ITEM, page.title(), page.path(),
        page.icon(), page.order(), page.perm(), List.of()));
  }

  private static String opt(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? null : value.asText();
  }
}
