package net.zentao.platform.session;

import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** session 表 Mapper（MyBatis-Flex BaseMapper；经 @MapperScan 按 @Mapper 注解注册）。 */
@Mapper
public interface SessionMapper extends BaseMapper<SessionPO> {}
