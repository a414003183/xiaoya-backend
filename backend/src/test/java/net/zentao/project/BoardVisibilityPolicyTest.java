package net.zentao.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.zentao.project.domain.Board;
import net.zentao.project.domain.BoardSpace;
import net.zentao.project.domain.BoardVisibility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T-6 看板可见集矩阵（project 卡 §7，纯函数，无 Spring）。 */
class BoardVisibilityPolicyTest {

  private static BoardSpace space(long id, String acl, String owner, List<String> team, List<String> whitelist) {
    return new BoardSpace(id, "S" + id, "cooperation", owner, team, null, acl, whitelist, "active", 0, "boss",
        Instant.now(), null, null, null, null, 0);
  }

  private static Board board(long id, long spaceId, String acl, String owner, List<String> team,
      List<String> whitelist) {
    return new Board(id, spaceId, "B" + id, owner, team, null, acl, whitelist, "active", 0, "boss", Instant.now(),
        null, null, null, null, 0);
  }

  private static BoardVisibility.Viewer viewer(String account) {
    return new BoardVisibility.Viewer(account, false);
  }

  private static final BoardVisibility.Viewer SUPER_ADMIN = new BoardVisibility.Viewer("admin", true);

  @Test
  @DisplayName("空间：open 全员可见；private 仅 owner/team/白名单；超管全见")
  void spaceOpenAndPrivate() {
    BoardSpace open = space(1, "open", null, List.of(), List.of());
    BoardSpace priv = space(2, "private", "owner1", List.of("team1"), List.of("wl1"));

    assertTrue(BoardVisibility.isVisible(open, viewer("outsider")), "open 空间全员可见");
    assertFalse(BoardVisibility.isVisible(priv, viewer("outsider")), "private 空间外人不可见");
    assertTrue(BoardVisibility.isVisible(priv, viewer("owner1")));
    assertTrue(BoardVisibility.isVisible(priv, viewer("team1")));
    assertTrue(BoardVisibility.isVisible(priv, viewer("wl1")));
    assertTrue(BoardVisibility.isVisible(priv, SUPER_ADMIN), "超管不受限");

    assertEquals(Set.of(1L), BoardVisibility.visibleSpaceIds(List.of(open, priv), viewer("outsider")));
    assertEquals(Set.of(1L, 2L), BoardVisibility.visibleSpaceIds(List.of(open, priv), viewer("team1")));
    assertEquals(Set.of(1L, 2L), BoardVisibility.visibleSpaceIds(List.of(open, priv), SUPER_ADMIN));
  }

  @Test
  @DisplayName("看板：open 全员、private 加空间负责人、extend 继承空间可见性")
  void boardAclMatrix() {
    BoardSpace openSpace = space(10, "open", "spaceOwner", List.of(), List.of());
    BoardSpace privSpace = space(11, "private", "spaceOwner", List.of("spaceTeam"), List.of("spaceWl"));
    Board openBoard = board(100, 11, "open", null, List.of(), List.of());
    Board privBoard = board(101, 10, "private", "bOwner", List.of("bTeam"), List.of("bWl"));
    Board extendBoard = board(102, 11, "extend", null, List.of(), List.of());
    Board extendOpenBoard = board(103, 10, "extend", null, List.of(), List.of());

    assertTrue(BoardVisibility.isVisible(openBoard, privSpace, viewer("outsider")), "open 为绝对规则，全员可见");
    assertFalse(BoardVisibility.isVisible(privBoard, openSpace, viewer("outsider")));
    assertTrue(BoardVisibility.isVisible(privBoard, openSpace, viewer("bOwner")));
    assertTrue(BoardVisibility.isVisible(privBoard, openSpace, viewer("bTeam")));
    assertTrue(BoardVisibility.isVisible(privBoard, openSpace, viewer("bWl")));
    assertTrue(BoardVisibility.isVisible(privBoard, openSpace, viewer("spaceOwner")), "空间负责人可见 private 看板");

    assertFalse(BoardVisibility.isVisible(extendBoard, privSpace, viewer("outsider")), "extend 继承 private 空间");
    assertTrue(BoardVisibility.isVisible(extendBoard, privSpace, viewer("spaceTeam")));
    assertTrue(BoardVisibility.isVisible(extendBoard, privSpace, viewer("spaceOwner")));
    assertTrue(BoardVisibility.isVisible(extendBoard, privSpace, viewer("spaceWl")));
    assertTrue(BoardVisibility.isVisible(extendOpenBoard, openSpace, viewer("outsider")), "extend 继承 open 空间");

    Map<Long, BoardSpace> spaces = BoardVisibility.byId(List.of(openSpace, privSpace));
    List<Board> boards = List.of(openBoard, privBoard, extendBoard);
    assertEquals(Set.of(100L), BoardVisibility.visibleBoardIds(boards, spaces, viewer("outsider")));
    assertEquals(Set.of(100L, 102L), BoardVisibility.visibleBoardIds(boards, spaces, viewer("spaceTeam")));
    assertEquals(Set.of(100L, 101L, 102L), BoardVisibility.visibleBoardIds(boards, spaces, viewer("spaceOwner")));
    assertEquals(Set.of(100L, 101L, 102L), BoardVisibility.visibleBoardIds(boards, spaces, SUPER_ADMIN));
  }
}
