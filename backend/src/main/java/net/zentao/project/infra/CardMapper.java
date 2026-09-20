package net.zentao.project.infra;

import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** board_card 表 Mapper。 */
@Mapper
public interface CardMapper extends BaseMapper<CardPO> {}
