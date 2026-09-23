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
import net.zentao.project.domain.BoardSpace;
import net.zentao.project.domain.BoardSpaceRepository;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** 看板空间仓储实现（infra：PO ↔ 领域对象；team JSON 列 + acl_entry 白名单装配）。 */
@Component
public class BoardSpaceRepositoryImpl implements BoardSpaceRepository {

  private static final QueryColumn DELETED_AT = new QueryColumn("deleted_at");
  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

  private final BoardSpaceMapper mapper;
  private final AclEntryRepository aclEntryRepository;
  private final JsonMapper jsonMapper;

  public BoardSpaceRepositoryImpl(BoardSpaceMapper mapper, AclEntryRepository aclEntryRepository,
      JsonMapper jsonMapper) {
    this.mapper = mapper;
    this.aclEntryRepository = aclEntryRepository;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Optional<BoardSpace> findActiveById(long id) {
    return Optional.ofNullable(mapper.selectOneByCondition(new QueryColumn("id").eq(id).and(DELETED_AT.isNull())))
        .map(this::toDomain);
  }

  @Override
  public List<BoardSpace> findAllActive() {
    return withWhitelists(mapper.selectListByCondition(DELETED_AT.isNull()));
  }

  @Override
  public BoardSpace insert(BoardSpace space) {
    BoardSpacePO po = toPo(space);
    po.setId(null);
    po.setLockVersion(0);
    mapper.insert(po);
    return findActiveById(po.getId()).orElseThrow();
  }

  @Override
  public Optional<BoardSpace> update(BoardSpace space) {
    // 全量覆盖（含 null 字段）：activate 清 closedAt/closedBy 必须落库；回读行取库内自增后的 lockVersion
    if (mapper.update(toPo(space), false) <= 0) {
      return Optional.empty();
    }
    return findActiveById(space.id());
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<BoardSpace> queryPage(Object whereWrapper, int offset, int limit) {
    return withWhitelists(mapper.selectListByQuery(((QueryWrapper) whereWrapper).limit(offset, limit)));
  }

  @Override
  public long countByQuery(Object whereWrapper) {
    return mapper.selectCountByQuery((QueryWrapper) whereWrapper);
  }

  @Override
  public void softDelete(long id) {
    SoftDeletes.apply("board_space", Row.of("deleted_at", Instant.now()), new QueryColumn("id").eq(id));
  }

  private List<BoardSpace> withWhitelists(List<BoardSpacePO> rows) {
    if (rows.isEmpty()) {
      return List.of();
    }
    var bySpace = aclEntryRepository.accountsOf("board_space", rows.stream().map(BoardSpacePO::getId).toList());
    return rows.stream()
        .map(po -> toDomain(po, bySpace.getOrDefault(po.getId(), List.of())))
        .toList();
  }

  private BoardSpace toDomain(BoardSpacePO po) {
    return toDomain(po, aclEntryRepository.accounts("board_space", po.getId()));
  }

  private BoardSpace toDomain(BoardSpacePO po, List<String> whitelist) {
    return new BoardSpace(po.getId(), po.getName(), po.getType(), po.getOwner(), readList(po.getTeam()),
        po.getDescription(), po.getAcl(), whitelist, po.getStatus(), po.getSort() == null ? 0 : po.getSort(),
        po.getCreatedBy(), po.getCreatedAt(), po.getUpdatedBy(), po.getUpdatedAt(), po.getClosedBy(),
        po.getClosedAt(), po.getLockVersion() == null ? 0 : po.getLockVersion());
  }

  private BoardSpacePO toPo(BoardSpace space) {
    BoardSpacePO po = new BoardSpacePO();
    po.setId(space.id() == 0 ? null : space.id());
    po.setName(space.name());
    po.setType(space.type());
    po.setOwner(space.owner());
    po.setTeam(writeList(space.team()));
    po.setDescription(space.description());
    po.setAcl(space.acl());
    po.setStatus(space.status());
    po.setSort(space.sort());
    po.setCreatedBy(space.createdBy());
    po.setCreatedAt(space.createdAt());
    po.setUpdatedBy(space.updatedBy());
    po.setUpdatedAt(space.updatedAt());
    po.setClosedBy(space.closedBy());
    po.setClosedAt(space.closedAt());
    po.setLockVersion(space.lockVersion());
    return po;
  }

  private List<String> readList(String json) {
    return json == null || json.isBlank() ? List.of() : jsonMapper.readValue(json, STRING_LIST);
  }

  private String writeList(List<String> value) {
    return value == null || value.isEmpty() ? null : jsonMapper.writeValueAsString(value);
  }
}
