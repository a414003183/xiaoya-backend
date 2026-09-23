package net.zentao.platform.file;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.row.Row;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** file 表读写（platform 卡 §3.5；软删行不可见）。 */
@Component
public class FileRepository {

  private static final QueryColumn ID = new QueryColumn("id");
  private static final QueryColumn DELETED_AT = new QueryColumn("deleted_at");

  private final FileMapper mapper;

  public FileRepository(FileMapper mapper) {
    this.mapper = mapper;
  }

  public void insert(FilePO po) {
    mapper.insert(po);
  }

  public Optional<FilePO> findVisibleById(long id) {
    return Optional.ofNullable(mapper.selectOneByCondition(ID.eq(id).and(DELETED_AT.isNull())));
  }

  public void update(FilePO po) {
    mapper.update(po);
  }

  /**
   * 下载计数 +1（T57 / BE-05）：**单条 SQL 自增**，不是「读出来 +1 再整行写回」。
   * 旧写法在并发下载下必丢计数（实测 16 并发只剩 1），且整行写回会拿陈旧快照覆盖同表其它列。
   */
  public int incrementDownloads(long id) {
    return Db.updateByCondition("file", new Row().setRaw("downloads", "downloads + 1"), ID.eq(id));
  }

  /** 软删（deleted_at 置位）。 */
  public void softDelete(FilePO po) {
    po.setDeletedAt(Instant.now());
    mapper.update(po);
  }

  public List<FilePO> page(String objectType, long objectId, int offset, int limit) {
    return mapper.selectListByQuery(QueryWrapper.create()
        .where(new QueryColumn("object_type").eq(objectType)
            .and(new QueryColumn("object_id").eq(objectId))
            .and(DELETED_AT.isNull()))
        .orderBy(new QueryColumn("id").desc())  // banned-words-ok：MyBatis-Flex 构造器方法名
        .limit(offset, limit));
  }

  public long countByObject(String objectType, long objectId) {
    return mapper.selectCountByQuery(QueryWrapper.create()
        .where(new QueryColumn("object_type").eq(objectType)
            .and(new QueryColumn("object_id").eq(objectId))
            .and(DELETED_AT.isNull())));
  }

  /** 清理候选：软删超阈值的行。 */
  public List<FilePO> findPurgeable(Instant threshold) {
    return mapper.selectListByQuery(QueryWrapper.create()
        .where(DELETED_AT.isNotNull().and(DELETED_AT.le(java.sql.Timestamp.from(threshold)))));
  }

  public void delete(FilePO po) {
    mapper.delete(po);
  }
}
