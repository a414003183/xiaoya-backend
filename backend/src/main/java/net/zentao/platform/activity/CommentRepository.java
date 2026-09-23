package net.zentao.platform.activity;

import com.mybatisflex.core.query.QueryColumn;
import java.util.LinkedHashMap;
import java.util.Map;
import net.zentao.platform.filters.LikePatterns;
import org.springframework.stereotype.Component;

/** comment 表读写（platform 卡 §3.3）。 */
@Component
public class CommentRepository {

  private static final QueryColumn OBJECT_TYPE = new QueryColumn("object_type");
  private static final QueryColumn OBJECT_ID = new QueryColumn("object_id");
  private static final QueryColumn CONTENT = new QueryColumn("content");
  private static final QueryColumn CREATED_AT = new QueryColumn("created_at");

  private final CommentMapper mapper;

  public CommentRepository(CommentMapper mapper) {
    this.mapper = mapper;
  }

  public void insert(CommentPO po) {
    mapper.insert(po);
  }

  public Map<String, Object> page(String objectType, long objectId, String q, int page, int limit) {
    var condition = OBJECT_TYPE.eq(objectType).and(OBJECT_ID.eq(objectId));
    if (q != null && !q.isBlank()) {
      condition = condition.and(CONTENT.likeRaw(LikePatterns.contains(q)));
    }
    long total = mapper.selectCountByQuery(com.mybatisflex.core.query.QueryWrapper.create().where(condition));
    var rows = mapper.selectListByQuery(com.mybatisflex.core.query.QueryWrapper.create()
        .where(condition)
        .orderBy(CREATED_AT.desc(), new QueryColumn("id").desc())  // banned-words-ok：MyBatis-Flex 构造器方法名
        .limit((page - 1) * limit, limit));
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("items", rows.stream().map(this::toView).toList());
    result.put("total", total);
    return result;
  }

  /** CommentView（contract：id/objectType/objectId/content/createdBy/createdAt）。 */
  public record CommentView(
      long id, String objectType, long objectId, String content, String createdBy, java.time.Instant createdAt) {}

  public CommentView toView(CommentPO po) {
    return new CommentView(po.getId(), po.getObjectType(), po.getObjectId(), po.getContent(),
        po.getCreatedBy(), po.getCreatedAt());
  }
}
