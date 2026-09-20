package net.zentao.platform.audit;

import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** audit_log 表 Mapper。 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLogPO> {}
