package net.zentao.project.domain;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 看板可见性判定（project 卡 §7，纯函数）：
 * 空间 open=全员 / private=仅 owner·team·白名单；看板 open=全员 / private=owner·team·白名单·空间负责人 /
 * extend=空间可见即可见；超管不受限（open/private 为绝对规则，extend 才是继承，§3.4）。
 *
 * ponytail: 全量小集合内存判定（与 ProjectVisibility 同口径）；升级路径 = 可见集下推为 SQL 的 IN 集合。
 */
public final class BoardVisibility {

  private BoardVisibility() {}

  /** 可见者上下文：账号 + 超管（看板维度无组 ACL 追加集，§7 未列）。 */
  public record Viewer(String account, boolean superAdmin) {}

  /** 空间可见：open 全员；private 仅 owner/team/白名单。 */
  public static boolean isVisible(BoardSpace space, Viewer viewer) {
    if (viewer.superAdmin()) {
      return true;
    }
    String account = viewer.account();
    if (account == null) {
      return false;
    }
    return "open".equals(space.acl())
        || account.equals(space.owner())
        || space.team().contains(account)
        || space.whitelist().contains(account);
  }

  /** 看板可见：open 全员；private 加空间负责人；extend 继承空间；超管不受限。 */
  public static boolean isVisible(Board board, BoardSpace space, Viewer viewer) {
    if (viewer.superAdmin()) {
      return true;
    }
    String account = viewer.account();
    if (account == null) {
      return false;
    }
    if (account.equals(board.owner()) || board.team().contains(account) || board.whitelist().contains(account)) {
      return true;
    }
    return switch (board.acl()) {
      case "open" -> true;
      case "private" -> space != null && account.equals(space.owner());
      case "extend" -> space != null && isVisible(space, viewer);
      default -> false;
    };
  }

  /** 可见空间 id 集（列表 DataScope 注入用）。 */
  public static Set<Long> visibleSpaceIds(List<BoardSpace> spaces, Viewer viewer) {
    Set<Long> visible = new LinkedHashSet<>();
    for (BoardSpace space : spaces) {
      if (isVisible(space, viewer)) {
        visible.add(space.id());
      }
    }
    return visible;
  }

  /** 可见看板 id 集（空间详情 boards[] 与列表过滤用；空间缺失（脏数据）时只认看板自身规则）。 */
  public static Set<Long> visibleBoardIds(List<Board> boards, Map<Long, BoardSpace> spacesById, Viewer viewer) {
    Set<Long> visible = new LinkedHashSet<>();
    for (Board board : boards) {
      if (isVisible(board, spacesById.get(board.spaceId()), viewer)) {
        visible.add(board.id());
      }
    }
    return visible;
  }

  /** 按 id 索引（可见集判定的空间上下文）。 */
  public static Map<Long, BoardSpace> byId(List<BoardSpace> spaces) {
    Map<Long, BoardSpace> byId = new LinkedHashMap<>();
    for (BoardSpace space : spaces) {
      byId.put(space.id(), space);
    }
    return byId;
  }
}
