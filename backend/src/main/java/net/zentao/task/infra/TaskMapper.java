package net.zentao.task.infra;

import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** task 表 Mapper。 */
@Mapper
public interface TaskMapper extends BaseMapper<TaskPO> {}
