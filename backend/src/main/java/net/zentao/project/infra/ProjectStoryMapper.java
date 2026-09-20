package net.zentao.project.infra;

import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** project_story 表 Mapper（project 卡 §2 链接表，T-1 已建表）。 */
@Mapper
public interface ProjectStoryMapper extends BaseMapper<ProjectStoryPO> {}
