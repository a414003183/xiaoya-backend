package net.zentao.platform.menu;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

/**
 * menu 表 PO（T21）：一棵可维护菜单树的一行。
 *
 * <p>身份是 {@code nodeKey}（覆盖内置节点 = 内置节点的自然键；新增节点 = {@code db-<id>}），父子按 key 认；
 * {@code path} 只是「菜单项指向哪个页面」的可编辑字段，不再唯一（同一页面挂两个入口是合法用法）。
 */
@Table("menu")
public class MenuPO {

  public static final String DIR = "dir";
  public static final String MENU = "menu";
  public static final String BUTTON = "button";

  @Id(keyType = KeyType.Auto)
  private Long id;

  private String nodeKey;
  private String parentKey;
  private String nodeType;
  private String title;
  /** 页面实现（组件名）：仅菜单项有；内置项的组件来自基线，新增项由管理端选择后落库。 */
  private String component;
  private String path;
  private String icon;
  private String perm;
  private Integer orderNo;
  private String status;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getNodeKey() {
    return nodeKey;
  }

  public void setNodeKey(String nodeKey) {
    this.nodeKey = nodeKey;
  }

  public String getParentKey() {
    return parentKey;
  }

  public void setParentKey(String parentKey) {
    this.parentKey = parentKey;
  }

  public String getNodeType() {
    return nodeType;
  }

  public void setNodeType(String nodeType) {
    this.nodeType = nodeType;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getComponent() {
    return component;
  }

  public void setComponent(String component) {
    this.component = component;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public String getIcon() {
    return icon;
  }

  public void setIcon(String icon) {
    this.icon = icon;
  }

  public String getPerm() {
    return perm;
  }

  public void setPerm(String perm) {
    this.perm = perm;
  }

  public Integer getOrderNo() {
    return orderNo;
  }

  public void setOrderNo(Integer orderNo) {
    this.orderNo = orderNo;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }
}
