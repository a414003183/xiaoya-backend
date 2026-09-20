package net.zentao.platform.langimport;

import com.mybatisflex.core.query.QueryWrapper;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/** lang_import 表读写（platform 卡 §3.12）：只追加、按 DSL 条件分页读取；无改写/删除路径（故不注入软删条件）。 */
@Component
public class LangImportRepository {

  private final LangImportMapper mapper;

  public LangImportRepository(LangImportMapper mapper) {
    this.mapper = mapper;
  }

  /** 追加一条上传记录（成功与失败同路径；id/createdAt 落库后回填）。 */
  public LangImportPO append(LangImportPO po) {
    po.setCreatedAt(Instant.now());
    po.setLockVersion(0);
    mapper.insert(po);
    return po;
  }

  public List<LangImportPO> page(QueryWrapper query, int offset, int limit) {
    return mapper.selectListByQuery(query.limit(offset, limit));
  }

  public long countByQuery(QueryWrapper query) {
    return mapper.selectCountByQuery(query);
  }
}
