package net.zentao.quality.infra;

import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** bug 表 Mapper。 */
@Mapper
public interface BugMapper extends BaseMapper<BugPO> {}
