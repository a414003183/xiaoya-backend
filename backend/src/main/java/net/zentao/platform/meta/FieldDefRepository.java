package net.zentao.platform.meta;

import java.util.List;
import org.springframework.stereotype.Component;

/** field_def 表读取（platform 卡 §3.10；无软删，启动全量加载）。 */
@Component
public class FieldDefRepository {

  private final FieldDefMapper mapper;

  public FieldDefRepository(FieldDefMapper mapper) {
    this.mapper = mapper;
  }

  public List<FieldDefPO> findAll() {
    return mapper.selectAll();
  }
}
