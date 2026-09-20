package net.zentao.project.infra;

import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** board 表 Mapper。 */
@Mapper
public interface BoardMapper extends BaseMapper<BoardPO> {}
