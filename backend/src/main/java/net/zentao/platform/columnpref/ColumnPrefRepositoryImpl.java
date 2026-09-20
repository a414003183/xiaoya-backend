package net.zentao.platform.columnpref;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** 列设置仓储实现（infra；columns 走 JSON 文本列，读写口径同 account_role.labels）。 */
@Component
public class ColumnPrefRepositoryImpl implements ColumnPrefRepository {

  private static final TypeReference<List<ColumnPrefItem>> COLUMNS_TYPE = new TypeReference<>() {};

  private static final QueryColumn ACCOUNT_ID = new QueryColumn("account_id");
  private static final QueryColumn RESOURCE = new QueryColumn("resource");

  private final ColumnPrefMapper mapper;
  private final JsonMapper jsonMapper;

  public ColumnPrefRepositoryImpl(ColumnPrefMapper mapper, JsonMapper jsonMapper) {
    this.mapper = mapper;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Optional<ColumnPref> find(long accountId, String resource) {
    return Optional.ofNullable(findRow(accountId, resource)).flatMap(this::toDomain);
  }

  @Override
  public ColumnPref upsert(long accountId, String resource, List<ColumnPrefItem> columns) {
    String json = writeColumns(columns);
    ColumnPrefPO existing = findRow(accountId, resource);
    if (existing == null) {
      ColumnPrefPO po = new ColumnPrefPO();
      po.setAccountId(accountId);
      po.setResource(resource);
      po.setColumns(json);
      po.setCreatedAt(Instant.now());
      po.setLockVersion(0);
      mapper.insert(po);
      return new ColumnPref(resource, columns);
    }
    // 业务键是 (account_id, resource)，但 MyBatis-Flex 的 update(entity) 按 @Id 定位（且靠它带乐观锁），
    // 故先取行拿 id 再走主键更新（口径同 AccountRoleRepositoryImpl / GroupRepositoryImpl）。
    ColumnPrefPO update = new ColumnPrefPO();
    update.setId(existing.getId());
    update.setAccountId(accountId);
    update.setResource(resource);
    update.setColumns(json);
    update.setUpdatedAt(Instant.now());
    update.setLockVersion(existing.getLockVersion() == null ? 0 : existing.getLockVersion());
    if (mapper.update(update) <= 0) {
      // 同一账号两开标签页并发保存（列设置的全部并发面）：重读版本再写一次，不向用户抛 409
      ColumnPrefPO latest = findRow(accountId, resource);
      if (latest != null) {
        update.setLockVersion(latest.getLockVersion());
        mapper.update(update);
      }
    }
    return new ColumnPref(resource, columns);
  }

  @Override
  public void delete(long accountId, String resource) {
    mapper.deleteByCondition(ACCOUNT_ID.eq(accountId).and(RESOURCE.eq(resource)));
  }

  private ColumnPrefPO findRow(long accountId, String resource) {
    QueryWrapper query = QueryWrapper.create().where(ACCOUNT_ID.eq(accountId)).and(RESOURCE.eq(resource));
    return mapper.selectOneByQuery(query);
  }

  private Optional<ColumnPref> toDomain(ColumnPrefPO po) {
    if (po.getColumns() == null || po.getColumns().isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(new ColumnPref(po.getResource(), jsonMapper.readValue(po.getColumns(), COLUMNS_TYPE)));
    } catch (Exception e) {
      // 坏数据视为未设置（不炸读路径；写路径已由 ColumnPref.normalizeColumns 保证形状）
      return Optional.empty();
    }
  }

  private String writeColumns(List<ColumnPrefItem> columns) {
    try {
      return jsonMapper.writeValueAsString(columns);
    } catch (Exception e) {
      throw new IllegalStateException("列设置写入失败", e);
    }
  }
}
