package net.zentao.project.domain;

import java.util.List;

/**
 * 项目/执行↔需求关联仓储（project 卡 §2：project_story，`project_id` 存项目或执行 id）。
 * 关联幂等（重复关联不增行）；解除（B-PRJ-06）为硬删行、幂等（行不存在不报错）。
 */
public interface ProjectStoryRepository {

  /** 对象关联的需求 id 集（列表与看板的跨域读基准）。 */
  List<Long> storyIds(long projectId);

  /** 关联需求（已关联的不再插行）；productId 为需求所属产品，落冗余列供反查。 */
  void link(long projectId, List<Long> storyIds, long productId);

  /** 解除关联（删 project_id+story_id 行；幂等）。执行侧只删执行自身行，项目级行不受影响。 */
  void unlink(long projectId, long storyId);

  /** 关联行数（project 删除守卫：存在未删关联行 → 42203）。 */
  long countByProject(long projectId);
}
