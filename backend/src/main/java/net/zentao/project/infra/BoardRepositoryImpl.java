package net.zentao.project.infra;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.row.Row;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import net.zentao.platform.persistence.SoftDeletes;
import net.zentao.project.domain.AclEntryRepository;
import net.zentao.project.domain.Board;
import net.zentao.project.domain.BoardRepository;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** 看板仓储实现（infra：PO ↔ 领域对象；team JSON 列 + acl_entry 白名单装配）。 */
@Component
public class BoardRepositoryImpl implements BoardRepository {

  private static final QueryColumn DELETED_AT = new QueryColumn("deleted_at");
  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

  private final BoardMapper mapper;
  private final AclEntryRepository aclEntryRepository;
  private final JsonMapper jsonMapper;

  public BoardRepositoryImpl(BoardMapper mapper, AclEntryRepository aclEntryRepository, JsonMapper jsonMapper) {
    this.mapper = mapper;
    this.aclEntryRepository = aclEntryRepository;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Optional<Board> findActiveById(long id) {
    return Optional.ofNullable(mapper.selectOneByCondition(new QueryColumn("id").eq(id).and(DELETED_AT.isNull())))
        .map(this::toDomain);
  }

  @Override
  public List<Board> findActiveBySpaceId(long spaceId) {
    List<BoardPO> rows = mapper.selectListByQuery(QueryWrapper.create()
        .where(new QueryColumn("space_id").eq(spaceId).and(DELETED_AT.isNull()))
        .orderBy(new QueryColumn("sort").asc(), new QueryColumn("id").asc())); // banned-words-ok：MyBatis-Flex 构造器方法名
    if (rows.isEmpty()) {
      return List.of();
    }
    var byBoard = aclEntryRepository.accountsOf("board", rows.stream().map(BoardPO::getId).toList());
    return rows.stream().map(po -> toDomain(po, byBoard.getOrDefault(po.getId(), List.of()))).toList();
  }

  @Override
  public Board insert(Board board) {
    BoardPO po = toPo(board);
    po.setId(null);
    po.setLockVersion(0);
    mapper.insert(po);
    return findActiveById(po.getId()).orElseThrow();
  }

  @Override
  public Optional<Board> update(Board board) {
    if (mapper.update(toPo(board), false) <= 0) {
      return Optional.empty();
    }
    return findActiveById(board.id());
  }

  @Override
  public long countActiveBySpaceId(long spaceId) {
    return mapper.selectCountByCondition(new QueryColumn("space_id").eq(spaceId).and(DELETED_AT.isNull()));
  }

  @Override
  public void softDelete(long id) {
    SoftDeletes.apply("board", Row.of("deleted_at", Instant.now()), new QueryColumn("id").eq(id));
  }

  private Board toDomain(BoardPO po) {
    return toDomain(po, aclEntryRepository.accounts("board", po.getId()));
  }

  private Board toDomain(BoardPO po, List<String> whitelist) {
    return new Board(po.getId(), po.getSpaceId() == null ? 0 : po.getSpaceId(), po.getName(), po.getOwner(),
        readList(po.getTeam()), po.getDescription(), po.getAcl(), whitelist, po.getStatus(),
        po.getSort() == null ? 0 : po.getSort(), po.getCreatedBy(), po.getCreatedAt(), po.getUpdatedBy(),
        po.getUpdatedAt(), po.getClosedBy(), po.getClosedAt(), po.getLockVersion() == null ? 0 : po.getLockVersion());
  }

  private BoardPO toPo(Board board) {
    BoardPO po = new BoardPO();
    po.setId(board.id() == 0 ? null : board.id());
    po.setSpaceId(board.spaceId());
    po.setName(board.name());
    po.setOwner(board.owner());
    po.setTeam(writeList(board.team()));
    po.setDescription(board.description());
    po.setAcl(board.acl());
    po.setStatus(board.status());
    po.setSort(board.sort());
    po.setCreatedBy(board.createdBy());
    po.setCreatedAt(board.createdAt());
    po.setUpdatedBy(board.updatedBy());
    po.setUpdatedAt(board.updatedAt());
    po.setClosedBy(board.closedBy());
    po.setClosedAt(board.closedAt());
    po.setLockVersion(board.lockVersion());
    return po;
  }

  private List<String> readList(String json) {
    return json == null || json.isBlank() ? List.of() : jsonMapper.readValue(json, STRING_LIST);
  }

  private String writeList(List<String> value) {
    return value == null || value.isEmpty() ? null : jsonMapper.writeValueAsString(value);
  }
}
