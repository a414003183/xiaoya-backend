package net.zentao.platform.meta;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** dict_type / dict_data 读写（T16 P1-4）。 */
@Component
public class DictRepository {

  static final QueryColumn TYPE_CODE = new QueryColumn("code");
  static final QueryColumn DATA_TYPE_CODE = new QueryColumn("type_code");
  static final QueryColumn DATA_ID = new QueryColumn("id");

  public static final String ACTIVE = "active";

  private final DictTypeMapper typeMapper;
  private final DictDataMapper dataMapper;

  public DictRepository(DictTypeMapper typeMapper, DictDataMapper dataMapper) {
    this.typeMapper = typeMapper;
    this.dataMapper = dataMapper;
  }

  public Optional<DictTypePO> findType(String code) {
    return Optional.ofNullable(typeMapper.selectOneByCondition(TYPE_CODE.eq(code)));
  }

  public void insertType(DictTypePO po) {
    typeMapper.insert(po);
  }

  public void updateType(DictTypePO po) {
    typeMapper.update(po);
  }

  /** 删类型并级联删其数据项（T16：类型没了，数据项就是孤儿；页面确认框已写明会连带删除）。 */
  public int deleteTypeCascade(String code) {
    dataMapper.deleteByCondition(DATA_TYPE_CODE.eq(code));
    return typeMapper.deleteByCondition(TYPE_CODE.eq(code));
  }

  public List<DictTypePO> pageTypes(QueryWrapper query, int offset, int limit) {
    return typeMapper.selectListByQuery(query.limit(offset, limit));
  }

  public long countTypes(QueryWrapper query) {
    return typeMapper.selectCountByQuery(query);
  }

  public Optional<DictDataPO> findData(long id) {
    return Optional.ofNullable(dataMapper.selectOneByCondition(DATA_ID.eq(id)));
  }

  public List<DictDataPO> listActiveData(String typeCode) {
    return dataMapper.selectListByQuery(QueryWrapper.create()
        .where(DATA_TYPE_CODE.eq(typeCode))
        .and(new QueryColumn("status").eq(ACTIVE))
        .orderBy(new QueryColumn("sort_no").asc(), DATA_ID.asc())); // banned-words-ok：MyBatis-Flex 构造器方法名，非请求参数
  }

  public void insertData(DictDataPO po) {
    dataMapper.insert(po);
  }

  public void updateData(DictDataPO po) {
    dataMapper.update(po);
  }

  public int deleteData(long id) {
    return dataMapper.deleteByCondition(DATA_ID.eq(id));
  }

  public List<DictDataPO> pageData(QueryWrapper query, int offset, int limit) {
    return dataMapper.selectListByQuery(query.limit(offset, limit));
  }

  public long countData(QueryWrapper query) {
    return dataMapper.selectCountByQuery(query);
  }
}
