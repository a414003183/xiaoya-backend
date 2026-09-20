package net.zentao.requirement.infra;

import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** story 表 Mapper。 */
@Mapper
public interface StoryMapper extends BaseMapper<StoryPO> {}
