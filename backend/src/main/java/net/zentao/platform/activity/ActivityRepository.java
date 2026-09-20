package net.zentao.platform.activity;

import com.mybatisflex.core.query.QueryColumn;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** activity 表读写（platform 卡 §2，append-only）。 */
@Component
public class ActivityRepository {

  private static final QueryColumn ID = new QueryColumn("id");
  private static final QueryColumn OBJECT_TYPE = new QueryColumn("object_type");
  private static final QueryColumn OBJECT_ID = new QueryColumn("object_id");

  private final ActivityMapper mapper;
  private final JsonMapper jsonMapper;

  public ActivityRepository(ActivityMapper mapper, JsonMapper jsonMapper) {
    this.mapper = mapper;
    this.jsonMapper = jsonMapper;
  }

  public void insert(ActivityPO po) {
    mapper.insert(po);
  }

  public Optional<ActivityPO> findById(long id) {
    return Optional.ofNullable(mapper.selectOneById(id));
  }

  /** 游标分页：倒序、beforeId 之前的 limit+1 条（多取一条判定 hasMore）。 */
  public List<ActivityPO> cursor(String objectType, long objectId, String actor, Integer limit, Long beforeId) {
    var condition = OBJECT_TYPE.eq(objectType).and(OBJECT_ID.eq(objectId));
    if (actor != null && !actor.isBlank()) {
      condition = condition.and(new QueryColumn("actor").eq(actor));
    }
    if (beforeId != null) {
      condition = condition.and(ID.lt(beforeId));
    }
    return mapper.selectListByQuery(
        com.mybatisflex.core.query.QueryWrapper.create()
            .where(condition)
            .orderBy(ID.desc())  // banned-words-ok：MyBatis-Flex 构造器方法名
            .limit(limit == null ? 51 : limit + 1));
  }

  /** 按操作人的跨对象游标分页（workspace 卡 §5 /my/activities：actor=@me，不限对象）。 */
  public List<ActivityPO> cursorByActor(String actor, Integer limit, Long beforeId) {
    var condition = new QueryColumn("actor").eq(actor);
    if (beforeId != null) {
      condition = condition.and(ID.lt(beforeId));
    }
    return mapper.selectListByQuery(
        com.mybatisflex.core.query.QueryWrapper.create()
            .where(condition)
            .orderBy(ID.desc())  // banned-words-ok：MyBatis-Flex 构造器方法名
            .limit(limit == null ? 51 : limit + 1));
  }

  List<ActivityRepository.DetailField> parseDetail(String json) {    if (json == null || json.isBlank()) {
      return List.of();
    }
    return jsonMapper.readValue(json, new TypeReference<List<DetailField>>() {});
  }

  String writeDetail(List<DetailField> detail) {
    if (detail == null || detail.isEmpty()) {
      return null;
    }
    return jsonMapper.writeValueAsString(detail);
  }

  /** 字段 diff 项（platform 卡 §3.2：[{field, oldValue, newValue}]）。 */
  public record DetailField(String field, String oldValue, String newValue) {}
}
