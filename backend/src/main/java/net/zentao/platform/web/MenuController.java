package net.zentao.platform.web;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import net.zentao.platform.audit.Audit;
import net.zentao.platform.audit.AuditDiff;
import net.zentao.platform.menu.MenuQueryService;
import net.zentao.platform.menu.SaveMenuHandler;
import net.zentao.platform.rbac.RequirePrivilege;
import net.zentao.platform.session.SessionResolver;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 菜单管理（T19 P2-1 / T21 / T03 纯 DB 化）：`menu` 表是唯一数据源，页面注册表只提供组件对应关系。
 *
 * <p>`/menus/my` 是侧栏数据源，登录即可访问（它就是「我的菜单」，不挂权限码——见 privilege-whitelist.txt）；
 * 写端点要 `menu-manage`（菜单是导航与权限可见性的入口，能改菜单 ≈ 能改所有人的入口）；
 * `/menus/grantable` 是角色授权页读的那棵树，要 `group-priv-edit`（能授权的人不一定是菜单管理员）。
 *
 * <p>寻址：写端点按 `nodeKey` 走**查询参数**——页面节点的 key 就是它的 path（含 `/`）、按钮 key 含 `#`，
 * 路径里的 `%2F` 会被 Tomcat 直接 400，故不能用路径参数（T03 实测，见契约 PATCH /menus 的说明）。
 */
@RestController
@RequestMapping("/api/v1")
public class MenuController {

  private final MenuQueryService queryService;
  private final SaveMenuHandler saveHandler;
  private final SessionResolver resolver;

  public MenuController(MenuQueryService queryService, SaveMenuHandler saveHandler, SessionResolver resolver) {
    this.queryService = queryService;
    this.saveHandler = saveHandler;
    this.resolver = resolver;
  }

  @GetMapping("/menus/tree")
  @Operation(operationId = "listMenus")
  @RequirePrivilege("menu-manage")
  public DataEnvelope<MenuQueryService.MenuTree> tree() {
    return DataEnvelope.of(queryService.tree());
  }

  @GetMapping("/menus/my")
  @Operation(operationId = "listMyMenus")
  public DataEnvelope<MenuQueryService.MenuTree> my(HttpServletRequest request) {
    return DataEnvelope.of(queryService.myTree(resolver.resolve(request)));
  }

  /** 角色授权页的数据源（T24）：与 /menus/tree 同一棵树，只是入口权限不同——授权的人不一定是菜单管理员。 */
  @GetMapping("/menus/grantable")
  @Operation(operationId = "listGrantableMenus")
  @RequirePrivilege("role-priv-edit")
  public DataEnvelope<MenuQueryService.MenuTree> grantable() {
    return DataEnvelope.of(queryService.tree());
  }

  /**
   * 前端路由表（T26 动态路由 / T03 纯 DB）：DB 菜单行 ∪ 注册表里尚无 DB 行的隐藏页，返回全部页面的
   * path/component/perm/activeMenu。登录即可读——它就是「这个系统有哪些页面」，与侧栏（按权限过滤的
   * /menus/my）不是一回事。
   */
  @GetMapping("/menus/routes")
  @Operation(operationId = "listMenuRoutes")
  public DataEnvelope<MenuQueryService.RouteTable> routes() {
    return DataEnvelope.of(queryService.routes());
  }

  /** 页面注册表（T03）：菜单表单按 path 自动匹配组件的数据源。 */
  @GetMapping("/menus/page-registry")
  @Operation(operationId = "listMenuPageRegistry")
  @RequirePrivilege("menu-manage")
  public DataEnvelope<MenuQueryService.PageRegistry> pageRegistry() {
    return DataEnvelope.of(queryService.pageRegistry());
  }

  @PostMapping("/menus")
  @Operation(operationId = "createMenu")
  @RequirePrivilege("menu-manage")
  @Audit(action = "menu-create", objectType = "menu")
  public DataEnvelope<MenuQueryService.MenuNode> create(@RequestBody SaveMenuHandler.MenuRequest body) {
    return DataEnvelope.of(saveHandler.create(body));
  }

  @PatchMapping("/menus")
  @Operation(operationId = "updateMenu")
  @RequirePrivilege("menu-manage")
  @Audit(action = "menu-update", objectType = "menu")
  @AuditDiff(objectType = "menu", idParam = "nodeKey")
  public DataEnvelope<MenuQueryService.MenuNode> update(@RequestParam("nodeKey") String nodeKey,
      @RequestBody SaveMenuHandler.MenuUpdateRequest body) {
    return DataEnvelope.of(saveHandler.update(nodeKey, body));
  }

  @DeleteMapping("/menus")
  @Operation(operationId = "deleteMenu")
  @RequirePrivilege("menu-manage")
  @Audit(action = "menu-delete", objectType = "menu")
  @AuditDiff(objectType = "menu", idParam = "nodeKey")
  public DataEnvelope<Void> delete(@RequestParam("nodeKey") String nodeKey) {
    saveHandler.delete(nodeKey);
    return DataEnvelope.empty();
  }
}
