package net.zentao.project.app;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
import net.zentao.platform.filters.FieldRegistry;
import net.zentao.platform.filters.FilterPredicate;
import net.zentao.platform.filters.Filters;
import net.zentao.platform.filters.LikePatterns;
import net.zentao.platform.rbac.DataScope;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.project.domain.Board;
import net.zentao.project.domain.BoardRepository;
import net.zentao.project.domain.BoardSpace;
import net.zentao.project.domain.BoardSpaceRepository;
import net.zentao.project.domain.BoardVisibility;
import net.zentao.project.domain.CardRepository;
import org.springframework.stereotype.Component;

/**
 * 看板域查询（project 卡 §3.3/§3.6 DSL 白名单 + §7 DataScope 注入可见 id 集，A6）。
 * 同时是看板/空间的「读侧可见性闸门」：lane/card 处理器复用 {@link #requireVisibleBoard}。
 */
@Component
public class BoardQueryService {

  private static final FieldRegistry SPACE_REGISTRY = FieldRegistry.allowing(
      Set.of("type", "status", "acl", "owner", "createdAt", "id"),
      Set.of("id", "name", "sort", "createdAt"),
      Set.of("name"));

  private static final Map<String, String> SPACE_COLUMNS = Map.ofEntries(
      Map.entry("type", "type"),
      Map.entry("status", "status"),
      Map.entry("acl", "acl"),
      Map.entry("owner", "owner"),
      Map.entry("createdAt", "created_at"),
      Map.entry("id", "id"),
      Map.entry("name", "name"),
      Map.entry("sort", "sort"));

  private static final FieldRegistry CARD_REGISTRY = FieldRegistry.allowing(
      Set.of("boardId", "laneId", "status", "priority", "assignee", "archived", "createdAt", "id"),
      Set.of("id", "sort", "priority", "createdAt"),
      Set.of("name"));

  private static final Map<String, String> CARD_COLUMNS = Map.ofEntries(
      Map.entry("boardId", "board_id"),
      Map.entry("laneId", "lane_id"),
      Map.entry("status", "status"),
      Map.entry("priority", "priority"),
      Map.entry("assignee", "assignee"),
      Map.entry("archived", "archived"),
      Map.entry("createdAt", "created_at"),
      Map.entry("id", "id"),
      Map.entry("name", "name"),
      Map.entry("sort", "sort"));

  private final BoardSpaceRepository spaceRepository;
  private final BoardRepository boardRepository;
  private final CardRepository cardRepository;
  private final DataScope dataScope;

  public BoardQueryService(BoardSpaceRepository spaceRepository, BoardRepository boardRepository,
      CardRepository cardRepository, DataScope dataScope) {
    this.spaceRepository = spaceRepository;
    this.boardRepository = boardRepository;
    this.cardRepository = cardRepository;
    this.dataScope = dataScope;
  }

  /** BoardSpaceList 载荷（contract：items + total；列表端点 boards 恒空数组）。 */
  public record BoardSpaceList(List<BoardSpaceHandlers.BoardSpaceView> items, long total) {}

  /** CardList 载荷（contract：items + total）。 */
  public record CardList(List<CardHandlers.CardView> items, long total) {}

  public BoardSpaceList pageSpaces(SessionPrincipal principal, Map<String, String[]> params) {
    Filters filters = Filters.parse(params, SPACE_REGISTRY);
    BoardVisibility.Viewer viewer = viewer(principal);
    QueryCondition injected = new QueryColumn("deleted_at").isNull();
    if (!viewer.superAdmin()) {
      injected = injected.and(idIn(BoardVisibility.visibleSpaceIds(spaceRepository.findAllActive(), viewer)));
    }
    QueryCondition keyword = keywordCondition(filters.q());
    if (keyword != null) {
      injected = injected.and(keyword);
    }
    List<BoardSpaceHandlers.BoardSpaceView> items = spaceRepository
        .queryPage(FilterPredicate.compile(filters, SPACE_COLUMNS::get, specialOf(principal), injected),
            filters.offset(), filters.limit())
        .stream()
        .map(space -> BoardSpaceHandlers.BoardSpaceView.of(space, List.of()))
        .toList();
    QueryWrapper countQuery =
        FilterPredicate.compile(filters.forCount(), SPACE_COLUMNS::get, specialOf(principal), injected);
    return new BoardSpaceList(items, spaceRepository.countByQuery(countQuery));
  }

  /** 空间详情（boards[] 只含可见看板；不可见 → 40302）。 */
  public BoardSpaceHandlers.BoardSpaceView spaceDetail(SessionPrincipal principal, long spaceId) {
    BoardSpace space = requireVisibleSpace(principal, spaceId);
    BoardVisibility.Viewer viewer = viewer(principal);
    List<BoardHandlers.BoardView> boards = boardRepository.findActiveBySpaceId(spaceId).stream()
        .filter(board -> BoardVisibility.isVisible(board, space, viewer))
        .map(BoardHandlers.BoardView::of)
        .toList();
    return BoardSpaceHandlers.BoardSpaceView.of(space, boards);
  }

  /** 看板卡片列表（§3.6 DSL：filters[archived] 传 1/0）。 */
  public CardList pageCards(SessionPrincipal principal, long boardId, Map<String, String[]> params) {
    requireVisibleBoard(principal, boardId);
    Filters filters = Filters.parse(params, CARD_REGISTRY);
    QueryCondition injected = new QueryColumn("deleted_at").isNull().and(new QueryColumn("board_id").eq(boardId));
    QueryCondition keyword = keywordCondition(filters.q());
    if (keyword != null) {
      injected = injected.and(keyword);
    }
    List<CardHandlers.CardView> items = cardRepository
        .queryPage(FilterPredicate.compile(filters, CARD_COLUMNS::get, specialOf(principal), injected),
            filters.offset(), filters.limit())
        .stream()
        .map(CardHandlers.CardView::of)
        .toList();
    QueryWrapper countQuery =
        FilterPredicate.compile(filters.forCount(), CARD_COLUMNS::get, specialOf(principal), injected);
    return new CardList(items, cardRepository.countByQuery(countQuery));
  }

  /** 空间可见性闸门（BoardSpaceHandlers/BoardHandlers 共用）：不存在 → 40401，不可见 → 40302。 */
  public BoardSpace requireVisibleSpace(SessionPrincipal principal, long spaceId) {
    BoardSpace space = spaceRepository.findActiveById(spaceId).orElseThrow(() -> ApiException.notFound("entity.boardSpace"));
    if (!BoardVisibility.isVisible(space, viewer(principal))) {
      throw ApiException.keyed(ErrorCode.DATA_FORBIDDEN, "boardSpace.guard.forbidden");
    }
    return space;
  }

  /** 看板可见性闸门（LaneHandlers/CardHandlers 共用）：不存在 → 40401，不可见 → 40302。 */
  public Board requireVisibleBoard(SessionPrincipal principal, long boardId) {
    Board board = boardRepository.findActiveById(boardId).orElseThrow(() -> ApiException.notFound("entity.board"));
    BoardSpace space = spaceRepository.findActiveById(board.spaceId()).orElse(null);
    if (!BoardVisibility.isVisible(board, space, viewer(principal))) {
      throw ApiException.keyed(ErrorCode.DATA_FORBIDDEN, "board.guard.forbidden");
    }
    return board;
  }

  private BoardVisibility.Viewer viewer(SessionPrincipal principal) {
    return new BoardVisibility.Viewer(principal.account(), dataScope.isSuperAdmin(principal));
  }

  /** 可见集为空 → 恒假条件，避免 IN () 语法错误。 */
  private static QueryCondition idIn(java.util.Collection<Long> ids) {
    return new QueryColumn("id").in(ids.isEmpty() ? List.of(-1L) : List.copyOf(ids));
  }

  /** 特殊量：@me = 当前账号（@null/@notNull 已在解析层处理）。 */
  private static java.util.function.Function<String, Optional<String>> specialOf(SessionPrincipal principal) {
    return value -> "@me".equals(value) ? Optional.of(principal.account()) : Optional.empty();
  }

  private static QueryCondition keywordCondition(String q) {
    return q == null || q.isBlank() ? null : new QueryColumn("name").likeRaw(LikePatterns.contains(q));
  }
}
