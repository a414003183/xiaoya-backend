package net.zentao.platform.audit;

import com.mybatisflex.core.BaseMapper;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * audit_query_stat 表 Mapper（T04）。累加列走「单条 SQL 自增」（CONVENTIONS §2.4，禁读改写）：
 * 先尝试 UPDATE，未命中再 INSERT——并发插入撞唯一键由调用方 catch 后重试 UPDATE。
 */
@Mapper
public interface AuditQueryStatMapper extends BaseMapper<AuditQueryStatPO> {

  /** 已有聚合作累加；返回影响行数（0 = 还没有这个 账号+资源+日）。 */
  @Update("UPDATE audit_query_stat SET query_count = query_count + #{count}, total_ms = total_ms + #{millis}"
      + " WHERE account = #{account} AND resource = #{resource} AND stat_day = #{day}")
  int addToExisting(@Param("account") String account, @Param("resource") String resource, @Param("day") LocalDate day,
      @Param("count") long count, @Param("millis") long millis);

  @Insert("INSERT INTO audit_query_stat (account, resource, stat_day, query_count, total_ms)"
      + " VALUES (#{account}, #{resource}, #{day}, #{count}, #{millis})")
  int insertRow(@Param("account") String account, @Param("resource") String resource, @Param("day") LocalDate day,
      @Param("count") long count, @Param("millis") long millis);
}
